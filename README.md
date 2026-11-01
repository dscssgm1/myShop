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
| myshop-logistics | myShop-logistics/biz | 8087 | 物流公司、快递员与区域、运单与轨迹 |
| myshop-message | myShop-message/biz | 8088 | 客服会话、消息、系统通知 |
| myshop-platform | myShop-platform/biz | 8089 | 审核中心、运营配置、操作/登录/系统日志 |
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
| `/api/audit/**`、`/api/config/**`、`/api/logs/**`、`/api/dashboard/**` | myshop-platform |

**Token 鉴权**：网关解析 `Authorization: Bearer <JWT>`，校验通过后向下游透传
`X-User-Id`、`X-User-Role`、`X-User-Group`、`X-User-Shop-Id`、`X-User-Company-Id`、`X-User-Courier-Id`。
白名单路径（`/api/system/auth/`、`/api/products`、`/api/categories`、`/api/shops`、`/api/promotions`、`/api/recommendations`）允许匿名访问。

服务间通过 OpenFeign 调用，`/internal/**` 接口不经过网关暴露。当前尚未接入认证服务，需要用户身份的接口从请求头 `X-User-Id` 读取用户 ID，后续替换为 JWT 即可。

## 核心能力

**认证与用户**：
- 注册 / 登录，签发 JWT（HS256，默认 2 小时），Spring Security 无状态鉴权；
- 角色体系（11 个角色，4 个端），登录后权限同时包含角色与所属端（`ROLE_ADMIN` + `ROLE_PLATFORM`）；
- 个人中心：资料、改密码、改邮箱（邮箱验证码）、收货地址、银行卡（脱敏返回）、收藏、浏览足迹；
- **忘记密码**：输入邮箱发送验证码，凭验证码重置密码；
- **邮件验证码**：验证码存 Redis（5 分钟 TTL、一次性作废），由 system 服务通过 `@Async` 异步投递 HTML 邮件（Thymeleaf 模板，SMTP 需配置 `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD`）；发送前限流（同一邮箱 60 秒一次、同一 IP 每天 10 次）；调试模式（`myshop.security.email-code.debug=true`）直接把验证码返回前端，生产环境关闭后由邮件投递；
- 会员等级按成长值匹配，积分按「消费金额 × 等级倍率」发放，支持内部接口发放/扣减积分；
- 平台管理员：用户分页检索、启用/禁用、调整角色；角色增删改（角色编码不可修改，有用户时不可删除）。

**商家**：店铺列表与详情、店铺信息维护（含揽收地址与多类目）、店铺员工管理、入驻申请。

**物流**：物流公司入驻、快递员与负责区域、商家发货创建运单、按区域生成揽收/派送任务、揽收/运输/派送/签收状态流转与轨迹。

**消息**：买家与店铺/平台客服会话（获取或创建）、收发消息、未读计数、系统通知。

**平台运营**：统一审核中心（商家入驻/商品上架/活动报名/退款/举报等）、分组配置中心、操作/登录/系统日志查询与内部写入接口。

**商品**：
- 两级分类（大类 + 小类）、品牌、规格模板（含选项）的增删改查；
- 商品（SPU）：多类目关联、品牌、促销价（折扣）、上下架（含原因）、审核状态（发布待审核 → 平台通过）；
- 商品 SKU：规格组合（颜色/尺码/容量等）各自独立价格与库存，无规格商品自动生成默认 SKU；
- 分页搜索（关键字/大类/小类/品牌/价格/标签/排序）、详情、热门商品、库存扣减与恢复（商品级 + SKU 级）。

**订单**：购物车增删改、下单预览、创建订单、订单列表与详情、取消、发货、确认收货。下单时调用商品服务扣库存、调用折扣服务算价并核销优惠券；取消订单会恢复库存并释放优惠券。

**支付**：创建支付单、查询、模拟支付成功、关闭支付单；支付成功后回调订单服务，接口幂等。

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
├── docker-compose.yml         # 本地 Redis
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
└── myShop-platform/           # 平台服务：审核、配置、日志
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

默认四个服务共用一个 `myshop` 库，表名带业务前缀、互不冲突。

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

## 本地启动

### 方式一：使用 Nacos（完整微服务模式）

1. 启动 Redis：`docker compose up -d`；Nacos 使用本地安装版本（默认 `127.0.0.1:8848`，控制台 `http://localhost:8848/nacos`）
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

# 推荐与促销推送
curl -H "X-User-Id: 1" "http://localhost:8085/recommendations?limit=8"
curl -H "X-User-Id: 1" "http://localhost:8085/push/promotions"
```

Windows 终端如果传入中文 JSON 出现编码错误，可先把请求体保存为 UTF-8 文件，再用 `curl --data-binary @body.json` 发送。

## 构建与测试

```bash
mvn clean package        # 构建全部模块（12 个模块）
mvn test                 # 30 个单元测试：折扣引擎、推荐评分、RFM 分层、
                         # 订单/支付状态机、商品库存规则
```

## 已知边界

- **system** 已接入 JWT + Spring Security；**gateway** 统一校验 Token 并透传身份头；
- 业务服务信任网关注入的 `X-User-*` 头，直连服务端口可伪造请求头，生产环境必须限制为内网访问；
- 审核通过后的业务联动（如入驻通过自动开店、商品上架通过自动建商品）尚未串联，需要各服务提供内部接口后补；
- 验证码存 Redis，需启动 Redis（`docker compose up -d` 已包含，仅 Redis）；SMTP 需通过 `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD` 配置，未配置时投递失败只记录日志不影响流程；
- 验证码在调试模式（`myshop.security.email-code.debug=true`）下直接返回，生产环境关闭后由邮件投递；
- 角色为单角色模型（一个账号一个角色），如需多角色需增加 `sys_user_role` 关联表。
- 无分布式事务、消息队列与熔断限流，跨服务失败通过补偿与幂等处理。
- 支付为模拟支付，未接入真实渠道；不支持退款、物流轨迹、商品评价、SKU/规格。
- 推荐与推送为可解释的轻量算法，未引入离线画像与特征平台。

## 相关项目

| 项目 | 说明 |
|------|------|
| [myShopUI](../myShopUI) | 商城前端（Vue 3） |
| [Prototype](../Prototype) | 前端界面与交互原型 |
