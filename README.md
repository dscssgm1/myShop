# myShop 电商购物平台 · 后端

myShop 是一个多角色电商购物平台，本项目为其后端服务，基于 Java 21 + Spring Boot 3 + Spring Cloud Alibaba 构建的微服务架构，使用 Nacos 作为服务注册中心与配置中心，为商城前端 [myShopUI](../myShopUI) 提供业务与数据接口。

## 平台角色

系统内置 11 个角色（与前端原型一致），由 `myShop-system` 统一维护：

| 端 | 角色编码 | 名称 |
|----|----------|------|
| 用户端 | `user` | 买家 |
| 商家端 | `merchant` / `merchant-sales` / `merchant-cs` | 店长 / 业务员 / 客服 |
| 物流端 | `logistics` / `logistics-ops` / `logistics-courier` | 物流管理员 / 调度员 / 快递员 |
| 平台端 | `admin` / `admin-sales` / `admin-cs` / `admin-ops` | 管理员 / 业务员 / 客服 / 运维员 |

各端能力：

- **买家**：浏览商品、购物车、下单支付、订单跟踪、评价售后、收藏足迹、会员积分
- **商家**：店铺、商品、营销活动、订单发货、售后、客服
- **物流**：运单、揽收派送、快递员区域管理
- **平台**：用户角色、审核、配置、促销、日志与数据看板

演示账号见「本地启动」，默认密码均为 `123456`。

## 微服务架构

| 服务（Nacos 注册名） | 模块 | 端口 | 说明 |
|------|------|------|------|
| myshop-gateway | myShop-gateway | 8080 | 统一入口，按路径转发到各业务服务 |
| myshop-system | myShop-system/biz | 8081 | 认证（JWT）、用户、角色、地址/银行卡/收藏/足迹、会员积分 |
| myshop-product | myShop-product/biz | 8082 | 分类（两级）、品牌、规格模板、商品与 SKU、库存 |
| myshop-order | myShop-order/biz | 8083 | 购物车、订单、订单状态流转 |
| myshop-payment | myShop-payment/biz | 8084 | 支付单、模拟支付、回调订单 |
| myshop-discount | myShop-discount/biz | 8085 | 活动、优惠券、折扣计算、个性化推荐、促销推送 |
| myshop-merchant | myShop-merchant/biz | 8086 | 店铺、店铺员工、入驻申请 |
| myshop-logistics | myShop-logistics/biz | 8087 | 物流公司、快递员与区域、运单与轨迹、签收分红（与平台按比例分运费） |
| myshop-message | myShop-message/biz | 8088 | 客服会话、消息、系统通知 |
| myshop-platform | myShop-platform/biz | 8089 | 商家管理（店铺/入驻审核）、审核中心、运营配置、操作/登录/系统日志 |
| myshop-review | myShop-review/biz | 8090 | 已购评价、商品/店铺评分、防刷与评价审核 |
| myshop-file | myShop-file | 8091 | 图片上传/删除，MinIO 对象存储（单模块，无数据库） |
| Nacos | - | 8848 | 服务注册发现与配置中心 |

每个业务服务拆成两个 Maven 模块：

- `myShop-xxx-api`：对外契约，只放请求/响应 DTO 和内部 Feign 接口；
- `myShop-xxx-biz`：唯一实现，依赖本服务的 api，包含接口层、应用层、领域层与基础设施层。

调用方只依赖提供方的 `-api`，不依赖 `-biz`，避免契约重复和实现耦合。网关、common 保持单模块。

网关路由（`StripPrefix=1`）：

| 路径 | 目标服务 |
|------|----------|
| `/api/products/**`、`/api/categories/**`、`/api/brands/**`、`/api/specs/**` | myshop-product |
| `/api/cart/**`、`/api/orders/**` | myshop-order |
| `/api/payments/**` | myshop-payment |
| `/api/promotions/**`、`/api/coupons/**`、`/api/discounts/**`、`/api/recommendations/**`、`/api/push/**` | myshop-discount |
| `/api/shops/**`、`/api/merchant/**` | myshop-merchant |
| `/api/logistics/**` | myshop-logistics |
| `/api/messages/**` | myshop-message |
| `/api/audit/**`、`/api/config/**`、`/api/logs/**`、`/api/dashboard/**`、`/api/platform/**` | myshop-platform |
| `/api/files/**` | myshop-file（需登录，图片回显直连 MinIO `:9000`，不经网关） |

**Token 鉴权**：网关解析 `Authorization: Bearer <JWT>`（令牌携带 `jti` 会话 ID），
再用 `jti` 从 Redis 读取 `myshop:auth:session:{jti}` 会话；会话不存在（已登出、被单点下线、账号被禁用或已过期）
即视为未登录。身份属性取自会话而非令牌，因此角色变更、昵称修改等操作对下一次请求立即生效。
校验通过后向下游透传 `X-User-Id`、`X-User-Username`、`X-User-Role`、`X-User-Group`、
`X-User-Shop-Id`、`X-User-Company-Id`、`X-User-Courier-Id`（客户端自带的 `X-User-*` 一律被剥离）。
白名单仅覆盖登录/注册/找回密码（`/api/system/auth/login|register|email-code|password/forgot|password/reset`）
与商品浏览类路径（`/api/products`、`/api/categories`、`/api/brands`、`/api/specs`、`/api/shops`、`/api/promotions`、`/api/recommendations`、`/api/reviews`），
以及买家端公开运营配置 `/api/config/public/**`（Banner、支付方式、售后原因、举报类型、系统参数）。
公开路径携带过期/损坏令牌时按匿名放行，不会阻断游客浏览。

**服务异常提示**：下游服务不可用（无实例、连接失败、超时）时，网关 `GatewayErrorHandler`
按命中的路由返回 `503 {"code":50300,"message":"订单服务异常，请联系客服"}`，并记录失败路由与原因；
前端在网关本身不可达时按接口路径推断所属服务，展示相同文案，相同提示会合并避免并发请求刷屏。

服务间通过 OpenFeign 调用，`/internal/**` 接口不经过网关对外暴露；下游服务通过网关注入的 `X-User-*` 请求头读取调用者身份（system 服务另有无状态 Spring Security 校验 JWT）。

## 核心能力

**认证与用户**：
- 注册 / 登录，签发 JWT（默认 2 小时），同时写入 Redis 登录会话；Spring Security 无状态鉴权；
- **登录设备管理**：个人中心可查看当前账号的全部在线设备并单点下线；平台管理员可在用户管理中查看并强制下线指定设备；退出登录、修改/重置密码、禁用账号会立即失效对应会话；
- 角色体系（11 个角色，4 个端），登录后权限同时包含角色与所属端（`ROLE_ADMIN` + `ROLE_PLATFORM`）；
- 个人中心：资料、改密码、改邮箱（邮箱验证码）、收货地址、银行卡（脱敏返回）、收藏、浏览足迹；
- **忘记密码**：输入邮箱发送验证码，凭验证码重置密码；
- **邮件验证码**：验证码存 Redis（5 分钟 TTL、一次性作废），由 system 服务通过 `@Async` 异步投递 HTML 邮件（Thymeleaf 模板，SMTP 需配置 `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD`）；发送前限流（同一邮箱 60 秒一次、同一 IP 每天 10 次）；调试模式（`myshop.security.email-code.debug=true`）直接把验证码返回前端，生产环境关闭后由邮件投递；
- 会员等级按成长值匹配，积分按「消费金额 ÷ 每积分消费额 × 等级倍率」发放（每积分消费额可在配置中心调整），支持内部接口发放/扣减积分；
- 平台管理员：用户分页检索、启用/禁用、调整角色；角色增删改（角色编码不可修改，有用户时不可删除）。

**商家入驻**：
- 买家在个人中心提交入驻申请（店铺名称、经营类目、联系人/电话、所在区域、揽收地址、店铺介绍，以及**工商资质**：法定代表人、经营许可证号、经营许可证照片、注册地址、经营范围、营业期限），资质项为必填，同一账号同时只能有一条待审核申请；
- 平台「审核中心 → 商家入驻审核」查看资料（含资质与许可证照片）并通过/驳回（驳回原因必填），列表支持按状态与关键字检索；
- 审核通过自动创建店铺（默认 emoji 标识与主题色，资质信息一并带入），把申请人账号升级为 `merchant` 并绑定店铺 ID，同时发送站内通知；申请人需重新登录，JWT 才会携带新角色与店铺 ID；
- 平台可启用/停用店铺并记录原因；商家在「店铺信息」中**必须上传图片店铺标识**（`shop.icon` 为图片 URL，前后端均校验必填），并维护名称、类目、介绍、联系人与揽收地址，以及**工商资质信息（必填**：法定代表人、经营许可证号、经营许可证照片、注册地址、经营范围、营业期限——日期区间选择，存为「开始 至 结束」字符串**）**；历史 emoji 标识在保存时会被要求替换为图片，买家店铺页/平台列表对旧数据按 emoji 回退展示；
- **资质公示**：店铺的许可证号、法定代表人、营业期限、注册地址、经营范围与许可证照片对所有用户可见（买家店铺页「资质信息」板块，照片可点击放大）。

**物流**：物流公司入驻、快递员与负责区域、商家发货创建运单、按区域生成揽收/派送任务、揽收/运输/派送/签收状态流转与轨迹。

**消息**：买家与店铺/平台客服会话（获取或创建）、收发消息、未读计数、系统通知。

**平台运营**：统一「审核中心」（Tab 汇总商品审核、商家入驻、物流入驻两级与通用审核、评价审核、售后仲裁，菜单与各 Tab 均有待审核角标计数）、商家店铺管理与启停、配置中心、操作/登录/系统日志查询与内部写入接口。

**平台配置中心**（后台「运营配置」；分类单独在「分类管理」菜单）：
- **分类管理**：大类 / 小类增删改、图标与展示色，买家端导航、首页分类与筛选条件实时联动；删除大类会一并删除其小类；
- **规格模板**：规格名 + 规格值列表，商家发布商品时直接选用；
- **支付方式**：支付宝 / 微信 / 银行卡的启停与前台显示名称，收银台实时联动（至少保留一种）；
- **服务保障**：平台维护保障项（配置 key + 名称/图标/启停），商家发布商品时从中多选，商品通过 `product_config_rel` 表按 key 关联，商品详情「服务保障」页签实时联动；删除保障项后已选商品不再展示该项；
- **会员等级**：成长值门槛、积分倍率、会员折扣、是否包邮，支持新增 / 编辑 / 删除，成长值门槛不可重复；
- **积分规则**：每消费 N 元累计 1 积分（system 服务直读配置表，保存后立即生效）、积分商城倍数、评价奖励积分；
- **首页 Banner**：标题、副标题、背景色（纯色或 CSS 渐变）、跳转链接，买家首页实时联动；
- **售后原因 / 举报类型 / 敏感词**：售后退款原因已联动买家申请售后选项（平台代发起售后同样快选）；敏感词由评价服务直读并过滤；举报类型供内容举报场景使用；
- **原因类别预设（审核驳回 / 售后拒绝 / 下架 / 停用）**：审核中心与入驻驳回、商家与平台拒绝售后、商品下架、店铺停用时先选原因类别（可自定义），再必填详细原因，最终以「【类别】详细原因」存入原 reason 字段；
- **系统参数**：平台名称、客服电话、服务时间、包邮门槛、待支付超时（买家页脚与收银台倒计时联动）；
- 品牌与物流公司保留各自的独立管理页，不纳入配置中心。

**商品**：
- 两级分类（大类 + 小类）、品牌、规格模板（含选项）的增删改查；
- 商品（SPU）：多类目关联、品牌、售价与促销价（折扣，需低于售价；无独立原价/划线价字段）、服务保障（关联配置中心 `service-guarantee` 组的 key）、上下架（含原因）、审核状态（发布待审核 → 平台通过，**通过即自动上架**）；
- **商品修改需平台审核**：任何编辑都会进入待审核；上架中的商品修改暂存为草稿（`pending_data`），审核通过前买家仍看到原内容，通过后新内容生效；驳回必须填写原因（原因可配置在 `audit-reject-reason` 配置组），商家在商品列表悬停「已驳回」可查看原因，修改保存后重新进入待审核；
- 商品 SKU：规格组合（颜色/尺码/容量等）各自独立价格与库存，无规格商品自动生成默认 SKU；
- **活动标签**：商品标签/角标不再由商家维护，买家端商品卡片角标与详情「促销」行实时取自进行中的活动（公开接口 `/promotions?status=active`，按 全平台/分类/指定商品 范围匹配，前端 30 秒缓存）；历史 `tags` 字段保留兼容但不再展示；
- 分页搜索（关键字/大类/小类/品牌/价格/标签/排序）、详情、热门商品、库存扣减与恢复（商品级 + SKU 级）。

**订单**：购物车增删改、下单预览、创建订单、订单列表与详情、取消、发货、确认收货。下单时调用商品服务扣库存、调用折扣服务算价并核销优惠券；取消订单会恢复库存并释放优惠券。订单状态为：待付款 `UNPAID` → 待发货 `PAID` → 待收货 `SHIPPED` → 已完成 `COMPLETED`，另有售后中 `AFTER_SALE`、已退款 `REFUNDED`、已取消 `CANCELLED`。已付款未发货可直接取消并原路退款；发货后只能走售后。

**售后**：
- 类型支持仅退款、退货退款；用户可发起，平台/商家客服也可协商后代用户发起；
- 商家 24 小时内审核，超时自动转平台审核；拒绝后用户可申请平台介入；买家/商家端显示剩余处理时间倒计时，倒计时结束即调用超时升级接口（服务端以数据库时间复核，未到截止时间不允许升级），定时任务每分钟兜底扫描；
- 同意退货退款后生成反向运单与 6 位取件码，买家在两天内预约快递上门取件，快递员凭取件码揽收；
- 由商家决定退款时机并触发退款；退款后商家可对不满足退款条件的退货发起申诉，平台审核成立后生成补款订单（按原订单实付金额），买家支付前无法继续下单/加购/支付，补款订单不可取消，支付后商家重新发货。

**支付**：创建支付单、查询、模拟支付成功、关闭支付单、按订单退款；支付成功后回调订单服务，接口幂等。

**折扣与算法**：
- 订单优惠规则：一单最多一个活动 + 一张优惠券；活动由商家配置 `couponStackable` 决定能否叠加优惠券。
- 活动规则：满减（支持每满 N 减 M）与折扣（支持优惠上限），可按全品类/分类/商品限定范围。
- 折扣引擎会在「仅活动」「仅优惠券」「活动+优惠券」中选出总优惠最大的组合。
- 个性化推荐：根据用户历史购买构建分类/标签偏好，按 `0.5*分类 + 0.3*标签 + 0.2*销量 + 0.05*评分` 打分，无历史时降级为热销推荐。
- 促销推送：按最近购买时间与累计消费做简化 RFM 分层（新用户 / 活跃 / 流失预警 / 沉睡，高/中/低价值），推送匹配的活动与可领取优惠券。

## 项目结构与分层

```
myShop/                        # 父工程，统一管理依赖版本
├── pom.xml
├── docker-compose.yml         # 本地 MySQL / Redis / MinIO
├── nacos/
│   ├── config/                # 各服务 Nacos 配置
│   └── init-config.sh         # 一键发布配置
├── sql/                       # 各服务建表与种子数据
├── myShop-common/             # 统一响应、错误码、异常、用户上下文
├── myShop-gateway/            # 网关
├── myShop-system/             # 用户与认证服务（聚合模块）
│   ├── api/                   #   契约
│   └── biz/                   #   实现（认证/用户/角色/个人资产/会员积分）
├── myShop-product/            # 商品服务（聚合模块）
│   ├── api/                   #   契约 + 内部 Feign 接口
│   └── biz/                   #   实现（轻 DDD）
├── myShop-order/              # 订单服务（聚合模块）
│   ├── api/                   #   购物车/订单契约 + 内部 Feign 接口
│   └── biz/                   #   实现（轻 DDD）
├── myShop-payment/            # 支付服务（聚合模块）
│   ├── api/                   #   契约
│   └── biz/                   #   实现（轻 DDD）
├── myShop-discount/           # 折扣服务（聚合模块）
│   ├── api/                   #   契约 + 内部 Feign 接口
│   └── biz/                   #   实现（完整 DDD）
├── myShop-merchant/           # 商家服务：店铺、员工、入驻申请
├── myShop-logistics/          # 物流服务：公司、快递员、运单轨迹
├── myShop-message/            # 消息服务：客服会话与通知
├── myShop-platform/           # 平台服务：审核、配置、日志
├── myShop-review/             # 评价服务：已购评价、评分统计、防刷与审核
└── myShop-file/               # 文件服务：图片上传/删除（MinIO，单模块无数据库）
```

新增模块（merchant / logistics / message / platform）采用精简分层：
`interfaces/rest` + `application` + `domain/model` + `infrastructure/persistence`，
CRUD 密集的场景直接使用 MyBatis-Plus Mapper，出现复杂领域规则时再引入仓储抽象。

每个服务目录是一个 Maven 聚合模块，`api`/`biz` 为它的子模块（artifactId 仍为 `myShop-xxx-api`、`myShop-xxx-biz`）。

**轻 DDD（system / product / order / payment）**

```
interfaces/rest      Controller
application          用例编排、事务边界、DTO 组装
domain/model         聚合根与实体（可保留 MyBatis-Plus 注解）
domain/repository    仓储接口
infrastructure/      Mapper、仓储实现、Feign 客户端、配置
```

订单、支付、商品的状态与库存规则放在聚合根中（`Order.pay/ship/complete/cancel`、`Payment.markPaid/close`、`Product.deduct/restore`）；状态迁移与库存扣减在仓储实现中用条件 SQL 保证并发安全。

**完整 DDD（discount）**

```
interfaces/rest       Controller
application           应用服务、assembler、port（订单/商品查询端口）
domain/model          Money、Scope、DiscountRule、OrderLine 等值对象；
                      Promotion、CouponTemplate、UserCoupon 聚合根
domain/service        DiscountPricingService、RecommendationScoringService、UserSegmentationService
domain/repository     仓储接口
domain/event          优惠券领取/核销/释放领域事件
infrastructure/       PO + Mapper + 仓储实现、Feign 端口适配、配置
```

domain 层不依赖 MyBatis-Plus、Feign、Spring Web 或 api DTO；跨服务读取结果由 application 层转换为领域只读模型后传入领域服务。

所有服务复用 `myShop-common` 的 `Result`、`PageResult`、`BusinessException` 与全局异常处理。

## 数据库

默认所有服务共用一个 `myshop` 库，表名带业务前缀、互不冲突。

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS myshop DEFAULT CHARACTER SET utf8mb4"

for f in sql/system.sql sql/product.sql sql/order.sql sql/payment.sql sql/discount.sql \
         sql/merchant.sql sql/logistics.sql sql/message.sql sql/platform.sql; do
  mysql -uroot -p --default-character-set=utf8mb4 myshop < "$f"
done
```

`sql/system.sql` 会创建角色与会员等级种子数据；11 个演示账号由服务首次启动时自动创建（BCrypt 加密，密码 `123456`）。

**分库模式**：分别创建库并导入对应脚本，再修改各服务的 `spring.datasource.url` 即可：

| 脚本 | 建议库名 |
|------|----------|
| `sql/system.sql` | myshop_system |
| `sql/merchant.sql` | myshop_merchant |
| `sql/logistics.sql` | myshop_logistics |
| `sql/message.sql` | myshop_message |
| `sql/platform.sql` | myshop_platform |
| `sql/product.sql` | myshop_product |
| `sql/order.sql` | myshop_order |
| `sql/payment.sql` | myshop_payment |
| `sql/discount.sql` | myshop_discount |

数据源默认连接 `127.0.0.1:3306/myshop`，账号 `root/root`，可通过环境变量 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖。种子数据包含 6 个分类、12 个商品、4 个活动、4 张券模板，以及演示用户 1 的历史订单和一张未使用优惠券。

> 分库模式下，system 服务（积分规则）与 review 服务（敏感词）会直读平台库的 `config_item` 表；如拆分数据库，需要为这两个服务配置可访问 `myshop_platform` 的账号，或改用服务间调用。

## 本地启动

### 方式一：使用 Nacos（完整微服务模式）

1. 启动依赖（MySQL `:13307`、Redis `:6379`、MinIO `:9000`）：`docker compose up -d`；Nacos 使用本地安装版本（默认 `127.0.0.1:8848`，控制台 `http://localhost:8848/nacos`）；MinIO 控制台 `http://localhost:9001`（minioadmin / minioadmin）
2. 发布配置：
   ```bash
   bash nacos/init-config.sh
   ```
3. 首次先把各模块安装到本地仓库，再按顺序启动服务（新终端分别执行）：
   ```bash
   mvn install -DskipTests

   mvn -pl myShop-system/biz spring-boot:run
   mvn -pl myShop-product/biz spring-boot:run
   mvn -pl myShop-discount/biz spring-boot:run
   mvn -pl myShop-order/biz spring-boot:run
   mvn -pl myShop-payment/biz spring-boot:run
   mvn -pl myShop-merchant/biz spring-boot:run
   mvn -pl myShop-logistics/biz spring-boot:run
   mvn -pl myShop-message/biz spring-boot:run
   mvn -pl myShop-platform/biz spring-boot:run
   mvn -pl myShop-file spring-boot:run
   mvn -pl myShop-gateway spring-boot:run
   ```
4. 通过网关访问 `http://localhost:8080/api/products`，Nacos 控制台可看到各服务实例。

### 方式二：无 Nacos 本地联调

关闭注册与配置中心，并通过 `myshop.clients.*` 指定下游服务地址：

```bash
mvn clean package -DskipTests
export MYSQL_PASSWORD=<本机 MySQL 密码>

COMMON="--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false --spring.config.import=optional:file:./none.yml"

java -jar myShop-product/biz/target/myShop-product-biz-1.0-SNAPSHOT.jar $COMMON &

MYSHOP_PRODUCT_URL=http://127.0.0.1:8082 \
MYSHOP_DISCOUNT_URL=http://127.0.0.1:8085 \
java -jar myShop-order/biz/target/myShop-order-biz-1.0-SNAPSHOT.jar $COMMON &

MYSHOP_ORDER_URL=http://127.0.0.1:8083 \
MYSHOP_PRODUCT_URL=http://127.0.0.1:8082 \
java -jar myShop-discount/biz/target/myShop-discount-biz-1.0-SNAPSHOT.jar $COMMON &

MYSHOP_ORDER_URL=http://127.0.0.1:8083 \
java -jar myShop-payment/biz/target/myShop-payment-biz-1.0-SNAPSHOT.jar $COMMON &
```

此模式下直接访问各服务端口（无需网关）。

## 接口示例

```bash
# 登录（演示账号 buyer / 123456）
curl -X POST "http://localhost:8081/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"buyer","password":"123456"}'

# 忘记密码：发送验证码 -> 重置密码
curl -X POST "http://localhost:8081/auth/password/forgot" \
  -H "Content-Type: application/json" -d '{"email":"buyer@myshop.com"}'
curl -X POST "http://localhost:8081/auth/password/reset" \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@myshop.com","code":"123456","newPassword":"654321"}'

# 携带令牌访问个人中心
curl -H "Authorization: Bearer <token>" "http://localhost:8081/users/me"
curl -H "Authorization: Bearer <token>" "http://localhost:8081/member/profile"

# 买家端公开配置（Banner / 支付方式 / 系统参数等，匿名可读）
curl "http://localhost:8080/api/config/public/banner"
curl "http://localhost:8080/api/config/public/pay-method"
curl "http://localhost:8080/api/config/public/service-guarantee"

# 平台：新增/删除配置项（服务保障等可增删的配置组）
curl -X PUT "http://localhost:8080/api/config/service-guarantee" \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"items":[{"key":"ship-insurance","value":"{\"name\":\"运费险\",\"enabled\":true}","description":"运费险"}]}'
curl -X DELETE "http://localhost:8080/api/config/service-guarantee/ship-insurance" \
  -H "Authorization: Bearer <token>"

# 平台配置中心（需平台账号令牌）
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/system/member/levels"
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/config/points-rule"

# 商品列表
curl "http://localhost:8082/products?page=1&size=4&sort=sales"

# 活动与可领取优惠券
curl "http://localhost:8085/promotions"
curl -H "X-User-Id: 1" "http://localhost:8085/coupons/templates"

# 算价（商品 2，使用用户优惠券 1）
curl -X POST "http://localhost:8085/discounts/calculate" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":1,"items":[{"productId":2,"categoryId":1,"price":399,"quantity":1}]}'

# 订单预览 / 创建订单
curl -X POST "http://localhost:8083/orders/preview" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":2,"quantity":1}],"couponId":1}'

curl -X POST "http://localhost:8083/orders" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":2,"quantity":1}],"couponId":1,
       "receiverName":"Zhang San","receiverPhone":"13800000000",
       "receiverAddress":"Beijing Chaoyang Wangjing SOHO T1"}'

# 支付
curl -X POST "http://localhost:8084/payments" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"orderNo":"<订单号>","channel":"ALIPAY"}'
curl -X POST "http://localhost:8084/payments/<支付单号>/pay" -H "X-User-Id: 1"

# 取消已付款未发货订单（自动退款）
curl -X POST "http://localhost:8083/orders/<订单号>/cancel" -H "X-User-Id: 1"

# 售后：用户发起 / 我的售后列表 / 详情
curl -X POST "http://localhost:8083/orders/after-sales" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"orderNo":"<订单号>","type":"REFUND_ONLY","reason":"商品质量问题","description":"外包装破损"}'
curl -H "X-User-Id: 1" "http://localhost:8083/orders/after-sales?page=1&size=10"
curl -H "X-User-Id: 1" "http://localhost:8083/orders/after-sales/<售后单号>"

# 客服代用户发起（PLATFORM 组，填写 userId）
curl -X POST "http://localhost:8083/orders/after-sales" \
  -H "X-User-Id: 11" -H "X-User-Group: PLATFORM" -H "Content-Type: application/json" \
  -d '{"orderNo":"<订单号>","userId":2,"type":"RETURN_REFUND","reason":"协商退货"}'

# 商家审核 / 触发退款 / 退款后申诉
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/merchant-review" \
  -H "X-User-Id: 3" -H "X-User-Group: MERCHANT" -H "X-User-Shop-Id: 1" \
  -H "Content-Type: application/json" -d '{"approved":true,"reason":"同意退货"}'
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/refund" \
  -H "X-User-Id: 3" -H "X-User-Group: MERCHANT" -H "X-User-Shop-Id: 1"

# 买家预约上门取件（两天内）/ 申请平台介入
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/appointment" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"appointmentTime":"2026-11-05T14:00:00"}'
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/escalate" -H "X-User-Id: 1"

# 平台：待审核列表 / 审核 / 处理申诉
curl -H "X-User-Id: 11" -H "X-User-Group: PLATFORM" \
  "http://localhost:8083/orders/after-sales/platform?status=PENDING_PLATFORM&page=1&size=10"
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/platform-review" \
  -H "X-User-Id: 11" -H "X-User-Group: PLATFORM" \
  -H "Content-Type: application/json" -d '{"approved":true,"reason":"平台同意"}'
curl -X POST "http://localhost:8083/orders/after-sales/<售后单号>/appeal-review" \
  -H "X-User-Id: 11" -H "X-User-Group: PLATFORM" \
  -H "Content-Type: application/json" -d '{"approved":true,"reason":"退货不符，生成补款订单"}'

# 推荐与促销推送
curl -H "X-User-Id: 1" "http://localhost:8085/recommendations?limit=8"
curl -H "X-User-Id: 1" "http://localhost:8085/push/promotions"

# 商家入驻：买家提交申请 / 查看我的申请
curl -X POST "http://localhost:8086/merchant/apply" \
  -H "X-User-Id: 2" -H "X-User-Role: user" -H "Content-Type: application/json" \
  -d '{"shopName":"星野数码","categories":["手机数码"],"contact":"Li Si",
       "phone":"13800000000","region":"Hangzhou Xihu","address":"Wensan Road 1",
       "description":"Digital accessories"}'
curl -H "X-User-Id: 2" "http://localhost:8086/merchant/apply/mine"

# 平台：入驻申请列表 / 待审核数 / 通过 / 驳回（需 PLATFORM 组）
curl -H "X-User-Role: admin" -H "X-User-Group: PLATFORM" \
  "http://localhost:8089/platform/shops/applies?status=PENDING&page=1&size=10"
curl -H "X-User-Role: admin" -H "X-User-Group: PLATFORM" \
  "http://localhost:8089/platform/shops/applies/pending-count"
curl -X PUT "http://localhost:8089/platform/shops/applies/1/approve" \
  -H "X-User-Role: admin" -H "X-User-Group: PLATFORM" -H "Content-Type: application/json" -d '{"reason":""}'
curl -X PUT "http://localhost:8089/platform/shops/applies/1/reject" \
  -H "X-User-Role: admin" -H "X-User-Group: PLATFORM" -H "Content-Type: application/json" -d '{"reason":"Information incomplete"}'
```

Windows 终端如果传入中文 JSON 出现编码错误，可先把请求体保存为 UTF-8 文件，再用 `curl --data-binary @body.json` 发送。

## 构建与测试

```bash
mvn clean package        # 构建全部模块（13 个模块）
mvn test                 # 30 个单元测试：折扣引擎、推荐评分、RFM 分层、
                         # 订单/支付状态机、商品库存规则
```

## 已知边界

- **system** 已接入 JWT + Spring Security；**gateway** 统一校验 Token 并透传身份头；
- 业务服务信任网关注入的 `X-User-*` 头，直连服务端口可伪造请求头，生产环境必须限制为内网访问；
- 商家入驻审核已串联「通过 → 自动建店 → 升级账号角色 → 站内通知」；其余审核联动（如商品上架通过自动建商品）尚未串联，需要各服务提供内部接口后补；
- 验证码存 Redis，需启动 Redis（`docker compose up -d` 已包含，仅 Redis）；SMTP 需通过 `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD` 配置，未配置时投递失败只记录日志不影响流程；
- 验证码在调试模式（`myshop.security.email-code.debug=true`）下直接返回，生产环境关闭后由邮件投递；
- 角色为单角色模型（一个账号一个角色），如需多角色需增加 `sys_user_role` 关联表。
- 无分布式事务、消息队列与熔断限流，跨服务失败通过补偿与幂等处理。
- 支付为模拟支付，未接入真实渠道；退款为服务内模拟退款，不产生真实资金流水。
- 上架中商品的待审核修改通过后整体覆盖商品内容，审核期间发生的销量/库存变动与平台调整的促销价会被草稿中提交的值覆盖。
- 售后超时自动转平台审核由订单服务定时任务扫描（约每分钟一次）；退货预约限制在两天内，需物流服务保持可用。
- 物流分红：正向运单签收时按配置 `dividend/platform-ratio`（平台占运费百分比）实时分成，写入分红流水并累加物流公司账户余额；售后退货件与包邮单不参与；平台可手工调账（正入账/负扣罚），账务流水不做删除。
- 评价仅限已完成订单中的商品，商品评分取该商品展示中评价的均值，店铺评分取全店所有商品评价的均值；商家不能购买自己店铺的商品。
- 评价支持晒图（最多 9 张）；商家可回复本店展示中的评价；奖励积分按配置 `points-rule/review-reward` 直发（bizNo 幂等，隐藏后再次通过不重复发放）。
- 推荐与推送为可解释的轻量算法，未引入离线画像与特征平台。

## 相关项目

| 项目 | 说明 |
|------|------|
| [myShopUI](../myShopUI) | 商城前端（Vue 3） |
| [Prototype](../Prototype) | 前端界面与交互原型 |
