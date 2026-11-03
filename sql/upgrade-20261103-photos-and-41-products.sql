-- 2026-11-03 第二批扩展：老商品 1-12 按类目就近补图 + 新增 41 个商品（52-92，覆盖 6 店全类目）
-- 图片均为可直连的 Unsplash 图床 URL；cover_image 主图，images 图集（逗号分隔）
-- 老商品仅补图不改名不改价；新商品全部 status=1 / audit_status='APPROVED'

-- ===== 一、老商品 1-12 就近配图 =====
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1580910051074-3eb694886505?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1580910051074-3eb694886505?w=800&q=80&auto=format&fit=crop'
WHERE id = 1;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&q=80&auto=format&fit=crop'
WHERE id = 2;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1552975084-6e027cd345c2?w=800&q=80&auto=format&fit=crop'
WHERE id = 3;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1610557892470-55d9e80c0bce?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1610557892470-55d9e80c0bce?w=800&q=80&auto=format&fit=crop'
WHERE id = 4;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80&auto=format&fit=crop'
WHERE id = 5;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop'
WHERE id = 6;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1568702846914-96b305d2aaeb?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1568702846914-96b305d2aaeb?w=800&q=80&auto=format&fit=crop'
WHERE id = 7;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&q=80&auto=format&fit=crop'
WHERE id = 8;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1532012197267-da84d127e765?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1519682337058-a94d519337bc?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=800&q=80&auto=format&fit=crop'
WHERE id = 9;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?w=800&q=80&auto=format&fit=crop'
WHERE id = 10;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800&q=80&auto=format&fit=crop'
WHERE id = 11;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80&auto=format&fit=crop'
WHERE id = 12;

-- ===== 二、新增商品 52-92 =====
INSERT IGNORE INTO product
(id, name, brand_id, category_id, shop_id, shop_name, price, discount_price, stock, sales, rating, emoji, color, cover_image, images, tags, description, specs, status, audit_status) VALUES
-- ===== 数码数码配件（数码旗舰店） =====
(52, '头戴式无线降噪耳机 40h 续航', 5, 1, 1, '数码旗舰店', 699.00, 599.00, 400, 1345, 4.8, '🎧', 'linear-gradient(135deg,#4b6cb7,#182848)', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1583394838336-acd977736f90?w=800&q=80&auto=format&fit=crop', '自营,热卖', '主动降噪一键静音，40mm 大驱动单元，佩戴轻若无物，通勤出差神器。', '[{"name":"颜色","options":["曜石黑","珍珠白"]}]', 1, 'APPROVED'),
(53, '复古头戴式有线耳机 包耳式', NULL, 1, 1, '数码旗舰店', 199.00, NULL, 500, 876, 4.6, '🎧', 'linear-gradient(135deg,#f5f7fa,#c3cfe2)', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800&q=80&auto=format&fit=crop', '自营', '经典复古造型，包耳式设计隔音自然，3.5mm 接口即插即用。', '[]', 1, 'APPROVED'),
(54, '桌面无线蓝牙音箱 原木质感', NULL, 1, 1, '数码旗舰店', 399.00, 349.00, 300, 543, 4.7, '🔊', 'linear-gradient(135deg,#c79081,#dfa579)', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=800&q=80&auto=format&fit=crop', '自营,满减', '360° 环绕声场，低音浑厚，木质箱体颜值在线，桌面点睛之笔。', '[{"name":"颜色","options":["原木色","黑色"]}]', 1, 'APPROVED'),
(55, '超薄无线键盘 双模连接 静音', NULL, 1, 1, '数码旗舰店', 249.00, 199.00, 400, 678, 4.6, '⌨️', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=800&q=80&auto=format&fit=crop', '自营,折扣', '超薄剪刀脚结构，静音敲击，蓝牙/2.4G 双模一键切换，办公颜值担当。', '[{"name":"颜色","options":["白色","深空灰"]}]', 1, 'APPROVED'),
(56, '机械键盘 RGB 背光 热插拔轴体', NULL, 1, 1, '数码旗舰店', 449.00, 399.00, 350, 912, 4.8, '⌨️', 'linear-gradient(135deg,#8e2de2,#4a00e0)', 'https://images.unsplash.com/photo-1595225476474-87563907a212?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1595225476474-87563907a212?w=800&q=80&auto=format&fit=crop', '热卖', '热插拔轴体随心换轴，全键无冲，1680 万色 RGB 背光，游戏办公双修。', '[{"name":"轴体","options":["红轴","茶轴"]}]', 1, 'APPROVED'),
(57, '静音无线鼠标 办公人体工学', NULL, 1, 1, '数码旗舰店', 129.00, 99.00, 800, 1876, 4.7, '🖱️', 'linear-gradient(135deg,#bdc3c7,#2c3e50)', 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '人体工学侧翼支撑，静音微动不扰人，一节电池用半年，办公族福音。', '[{"name":"颜色","options":["石墨黑","象牙白"]}]', 1, 'APPROVED'),
(58, '一体机电脑 23.8 英寸 4K 全面屏', NULL, 1, 1, '数码旗舰店', 4999.00, 4699.00, 100, 324, 4.8, '🖥️', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=800&q=80&auto=format&fit=crop', '自营,满减', '4K 全面屏一体成型，隐藏式主机仓，桌面只留一台屏幕的简洁。', '[{"name":"配置","options":["16G+512G","16G+1T"]}]', 1, 'APPROVED'),
(59, '入门级单反相机 18-55mm 套机', NULL, 1, 1, '数码旗舰店', 3899.00, 3599.00, 120, 265, 4.7, '📷', 'linear-gradient(135deg,#141e30,#243b55)', 'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800&q=80&auto=format&fit=crop', '满减', '约 2410 万有效像素，套机镜头日用够用，光学取景器所见即所得。', '[{"name":"规格","options":["18-55mm 套机"]}]', 1, 'APPROVED'),
(60, '移动电源 20000mAh 22.5W 快充', 2, 1, 1, '数码旗舰店', 149.00, 129.00, 1000, 3210, 4.8, '🔋', 'linear-gradient(135deg,#f7971e,#ffd200)', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=800&q=80&auto=format&fit=crop', '自营,热卖', '20000mAh 大容量，22.5W 超级快充，可带上飞机，出行续航安心。', '[{"name":"容量","options":["20000mAh"]}]', 1, 'APPROVED'),
(61, '创作设计本 13.5 英寸 高刷全面屏', 5, 1, 1, '数码旗舰店', 9999.00, 9499.00, 80, 198, 4.9, '💻', 'linear-gradient(135deg,#3a1c71,#d76d77)', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&q=80&auto=format&fit=crop', '自营', '高刷全面屏 + P3 广色域，性能释放冷静，设计师与剪辑师的生产力工具。', '[{"name":"配置","options":["16G+512G","32G+1T"]}]', 1, 'APPROVED'),
(62, '轻薄笔记本 Air 13 英寸 静音续航', 5, 1, 1, '数码旗舰店', 6999.00, 6799.00, 150, 456, 4.8, '💻', 'linear-gradient(135deg,#8e9eab,#eef2f3)', 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800&q=80&auto=format&fit=crop', '自营,热卖', '轻至 1.24kg，无风扇静音设计，全天候续航，轻薄本标杆之作。', '[{"name":"配置","options":["8G+256G","16G+512G"]}]', 1, 'APPROVED'),
(63, '智能手表 方形表盘 简约白', NULL, 1, 1, '数码旗舰店', 899.00, 799.00, 300, 876, 4.6, '⌚', 'linear-gradient(135deg,#e0eaef,#f5f7fa)', 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=80&auto=format&fit=crop', '折扣', '方形表盘极简设计，心率睡眠监测，消息提醒抬腕可见。', '[{"name":"表壳尺寸","options":["40mm","44mm"]}]', 1, 'APPROVED'),
(64, '商务机械腕表 全自动机芯 皮带款', NULL, 1, 1, '数码旗舰店', 2999.00, 2699.00, 60, 132, 4.9, '⌚', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80&auto=format&fit=crop', '满减', '全自动机械机芯，蓝宝石镜面，牛皮表带随岁月愈显质感。', '[{"name":"表盘","options":["白盘","黑盘"]}]', 1, 'APPROVED'),
-- ===== 服饰鞋包（潮流服饰馆） =====
(65, '加绒连帽卫衣 oversize 宽松款', NULL, 3, 3, '潮流服饰馆', 259.00, 199.00, 600, 1543, 4.7, '🧥', 'linear-gradient(135deg,#bdc3c7,#2c3e50)', 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800&q=80&auto=format&fit=crop', '热卖,折扣', '320g 加绒抓暖，oversize 版型不挑身材，秋冬叠穿百搭。', '[{"name":"颜色","options":["麻灰","黑色"]},{"name":"尺码","options":["M","L","XL"]}]', 1, 'APPROVED'),
(66, '直筒牛仔裤 棉弹丹宁 经典蓝', NULL, 3, 3, '潮流服饰馆', 299.00, 239.00, 500, 987, 4.6, '👖', 'linear-gradient(135deg,#2193b0,#6dd5ed)', 'https://images.unsplash.com/photo-1604176354204-9268737828e4?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1604176354204-9268737828e4?w=800&q=80&auto=format&fit=crop', '折扣', '精选棉弹丹宁，直筒版型藏肉显瘦，经典蓝耐洗不褪色。', '[{"name":"尺码","options":["28","29","30","31","32","33"]}]', 1, 'APPROVED'),
(67, '宽松工装外套 春秋薄款夹克', NULL, 3, 3, '潮流服饰馆', 329.00, 269.00, 400, 876, 4.6, '🧥', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=800&q=80&auto=format&fit=crop', '折扣', '宽松落肩版型，春秋薄款好叠穿，搭牛仔裤与工装裤都利落。', '[{"name":"颜色","options":["燕麦色","军绿"]},{"name":"尺码","options":["S","M","L","XL"]}]', 1, 'APPROVED'),
(68, '简约白色棒球帽 网眼透气', NULL, 3, 3, '潮流服饰馆', 89.00, 69.00, 800, 2134, 4.6, '🧢', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=800&q=80&auto=format&fit=crop', '热卖', '网眼透气后仓，可调节帽围，遮阳防晒一帽搞定。', '[]', 1, 'APPROVED'),
(69, '粗针织围巾披肩 两用加长', NULL, 3, 3, '潮流服饰馆', 129.00, 99.00, 400, 765, 4.7, '🧣', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1457545195570-67f207084966?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1457545195570-67f207084966?w=800&q=80&auto=format&fit=crop', '折扣', '粗针织纹路柔软亲肤，两用长度披肩围脖随意切换，冬日暖手神器。', '[{"name":"颜色","options":["草绿","烟灰"]}]', 1, 'APPROVED'),
(70, '波西米亚长款连衣裙 荷叶袖', NULL, 3, 3, '潮流服饰馆', 359.00, 289.00, 300, 678, 4.8, '👗', 'linear-gradient(135deg,#f6d365,#fda085)', 'https://images.unsplash.com/photo-1596783074918-c84cb06531ca?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1596783074918-c84cb06531ca?w=800&q=80&auto=format&fit=crop', '热卖', '垂坠雪纺飘逸显瘦，荷叶袖遮拜拜肉，度假通勤两相宜。', '[{"name":"颜色","options":["珊瑚粉"]},{"name":"尺码","options":["S","M","L"]}]', 1, 'APPROVED'),
(71, '综合体能训练鞋 平底稳支撑', 3, 3, 3, '潮流服饰馆', 699.00, 599.00, 400, 1098, 4.8, '👟', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=800&q=80&auto=format&fit=crop', '秒杀,热卖', '平底宽前掌稳如磐石，横向移动支撑到位，撸铁跳操全胜任。', '[{"name":"颜色","options":["荧光绿"]},{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(72, '复古老爹鞋 厚底拼色', NULL, 3, 3, '潮流服饰馆', 459.00, 399.00, 350, 876, 4.6, '👟', 'linear-gradient(135deg,#d4af37,#f9ef8f)', 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=800&q=80&auto=format&fit=crop', '折扣', '厚底增高 4cm，复古拼色自带焦点，走路像踩棉花一样软。', '[{"name":"尺码","options":["38","39","40","41","42","43"]}]', 1, 'APPROVED'),
(73, '气垫缓震跑步鞋 白橙配色', 3, 3, 3, '潮流服饰馆', 799.00, 699.00, 300, 654, 4.7, '👟', 'linear-gradient(135deg,#f7971e,#ffd200)', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop', '满减', '后掌可见式气垫，落地回弹干脆，白橙配色清爽出街。', '[{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(74, '日系基础款纯棉短袖T恤 宽松版', NULL, 3, 3, '潮流服饰馆', 139.00, 109.00, 900, 2345, 4.7, '👕', 'linear-gradient(135deg,#f5f7fa,#c3cfe2)', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '230g 重磅纯棉，宽松落肩版型男女同款，基础色怎么搭都不会错。', '[{"name":"颜色","options":["米白","浅灰"]},{"name":"尺码","options":["M","L","XL","XXL"]}]', 1, 'APPROVED'),
-- ===== 食品生鲜（鲜丰水果） =====
(75, '当季奶油草莓 250g×2 盒', NULL, 4, 4, '鲜丰水果', 39.90, 32.90, 600, 1876, 4.8, '🍓', 'linear-gradient(135deg,#e53935,#e35d5b)', 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=800&q=80&auto=format&fit=crop', '秒杀,产地直发', '空心少、香气足，奶香味浓郁，冷链直达开箱即食。', '[{"name":"规格","options":["250g×2盒"]}]', 1, 'APPROVED'),
(76, '高原蜜西瓜 单果 8 斤+', NULL, 4, 4, '鲜丰水果', 59.90, 49.90, 500, 1345, 4.7, '🍉', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=800&q=80&auto=format&fit=crop', '产地直发', '沙瓤脆甜皮薄如纸，高原昼夜温差养出的自然甜，冰镇更佳。', '[{"name":"规格","options":["单果 8斤+"]}]', 1, 'APPROVED'),
(77, '高山甜香蕉 自然熟 5 斤装', NULL, 4, 4, '鲜丰水果', 29.90, 24.90, 800, 2134, 4.6, '🍌', 'linear-gradient(135deg,#f8b500,#ffe259)', 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=800&q=80&auto=format&fit=crop', '产地直发,折扣', '高山梯田自然熟，无催熟剂，软糯香甜，宝宝辅食也放心。', '[{"name":"规格","options":["5斤装"]}]', 1, 'APPROVED'),
(78, '秘鲁牛油果 单果 130g 6 个装', NULL, 4, 4, '鲜丰水果', 49.90, 42.90, 400, 987, 4.7, '🥑', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=800&q=80&auto=format&fit=crop', '折扣', '单果 130g 起，捏起来微软即可食用，早餐吐司轻食伴侣。', '[{"name":"规格","options":["6个装"]}]', 1, 'APPROVED'),
(79, '东北长粒香大米 10KG 当季新米', NULL, 4, 4, '鲜丰水果', 89.90, 79.90, 300, 765, 4.8, '🍚', 'linear-gradient(135deg,#ffecd2,#fcb69f)', 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800&q=80&auto=format&fit=crop', '满减,热卖', '黑土地一年一熟，米粒修长饱满，饭香四溢冷而不回生。', '[{"name":"规格","options":["10KG/袋"]}]', 1, 'APPROVED'),
(80, '特级初榨橄榄油 750ml 冷压榨', NULL, 4, 4, '鲜丰水果', 129.00, 109.00, 350, 654, 4.7, '🫒', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800&q=80&auto=format&fit=crop', '折扣', '物理冷压榨，酸度 ≤0.5%，凉拌烹饪皆宜，轻食健身必备。', '[{"name":"规格","options":["750ml/瓶"]}]', 1, 'APPROVED'),
(81, '中度烘焙咖啡豆 1KG 坚果可可调', NULL, 4, 4, '鲜丰水果', 99.00, 89.00, 250, 543, 4.6, '☕', 'linear-gradient(135deg,#5a3f37,#2d132c)', 'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800&q=80&auto=format&fit=crop', '折扣', '中度烘焙坚果可可调，油脂丰厚，手冲意式都出色。', '[{"name":"规格","options":["1KG/袋"]}]', 1, 'APPROVED'),
(82, '冷冻大号基围虾 1KG 海捕速冻', NULL, 4, 4, '鲜丰水果', 79.90, 69.90, 400, 876, 4.7, '🦐', 'linear-gradient(135deg,#ff9966,#ff5e62)', 'https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=800&q=80&auto=format&fit=crop', '满减', '海捕速冻锁鲜，单只 12cm 起，白灼清蒸都鲜甜弹牙。', '[{"name":"规格","options":["1KG/盒"]}]', 1, 'APPROVED'),
(83, '奥尔良风味烤鸡腿半成品 1KG', NULL, 4, 4, '鲜丰水果', 45.90, 39.90, 500, 1098, 4.6, '🍗', 'linear-gradient(135deg,#c31432,#240b36)', 'https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '秘制奥尔良腌料入味，解冻后烤箱 20 分钟即享，追剧露营必备。', '[{"name":"规格","options":["1KG/袋"]}]', 1, 'APPROVED'),
-- ===== 图书文具（书虫书店） =====
(84, '人文经典精选套装（全 6 册）', NULL, 5, 5, '书虫书店', 299.00, 239.00, 200, 432, 4.8, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800&q=80&auto=format&fit=crop', '满减', '哲学、历史、艺术六部入门经典，布面精装，书架颜值担当。', '[]', 1, 'APPROVED'),
(85, '世界散文随笔经典（全 4 册）', NULL, 5, 5, '书虫书店', 199.00, 159.00, 260, 534, 4.7, '📖', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=800&q=80&auto=format&fit=crop', '折扣', '名家译本收录四大随笔圣手，睡前读两页，治愈一整天。', '[]', 1, 'APPROVED'),
(86, '牛皮纸手账本 A5 方格计划本', NULL, 5, 5, '书虫书店', 49.90, 39.90, 600, 1345, 4.6, '📒', 'linear-gradient(135deg,#c79081,#dfa579)', 'https://images.unsplash.com/photo-1517971129774-8a2b38fa128e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1517971129774-8a2b38fa128e?w=800&q=80&auto=format&fit=crop', '折扣,热卖', 'A5 方格内页书写不洇墨，附月度计划页与夹层口袋，学习工作两相宜。', '[{"name":"颜色","options":["复古棕","墨绿"]}]', 1, 'APPROVED'),
(87, '多功能文具收纳套装 12 件套', NULL, 5, 5, '书虫书店', 79.00, 59.00, 500, 987, 4.7, '✏️', 'linear-gradient(135deg,#89f7fe,#66a6ff)', 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?w=800&q=80&auto=format&fit=crop', '折扣', '笔袋文件分格收纳，开箱即用，学生党办公族桌面整理首选。', '[{"name":"规格","options":["12 件套"]}]', 1, 'APPROVED'),
(88, '儿童开学文具礼包 文具套装', NULL, 5, 5, '书虫书店', 69.00, 49.90, 400, 765, 4.8, '🎒', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=800&q=80&auto=format&fit=crop', '热卖', '笔、本、橡皮一次配齐，圆角护眼设计，开学季礼物首选。', '[{"name":"规格","options":["开学礼包"]}]', 1, 'APPROVED'),
-- ===== 美妆个护（美妍美妆） =====
(89, '哑光眼影盘 35 色 大地色系', NULL, 6, 6, '美妍美妆', 169.00, 139.00, 400, 987, 4.7, '🎨', 'linear-gradient(135deg,#a18cd1,#fbc2eb)', 'https://images.unsplash.com/photo-1573461160327-b450ce3d8e7f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1573461160327-b450ce3d8e7f?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1512496015851-a90fb38ba796?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '大地色系打底 + 摩卡红棕点缀，粉质细腻不飞粉，新手也化不出错。', '[{"name":"色系","options":["35 色大地盘"]}]', 1, 'APPROVED'),
(90, '玫瑰金化妆刷套装 8 件套', NULL, 6, 6, '美妍美妆', 129.00, 99.00, 350, 765, 4.8, '💄', 'linear-gradient(135deg,#f6d365,#fda085)', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=800&q=80&auto=format&fit=crop', '折扣', '纤维刷毛抓粉均匀，玫瑰金漆柄手感温润，散粉眼影刷一应俱全。', '[{"name":"规格","options":["8 件套"]}]', 1, 'APPROVED'),
(91, '防晒棒 SPF50+ 清爽不成膜', NULL, 6, 6, '美妍美妆', 89.00, 69.00, 600, 1543, 4.6, '🧴', 'linear-gradient(135deg,#e0eaef,#f5f7fa)', 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '旋转出膏随时补涂，清爽不成膜不假白，通勤户外都能扛。', '[{"name":"规格","options":["21g/支"]}]', 1, 'APPROVED'),
(92, '蔚蓝男士浓香水 100ml 木质芳香调', NULL, 6, 6, '美妍美妆', 1299.00, 1199.00, 100, 213, 4.9, '🌸', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=800&q=80&auto=format&fit=crop', '满减', '木质芳香调，开场柑橘凌厉，尾调檀香沉稳，商务场合气场全开。', '[]', 1, 'APPROVED');

-- ===== 三、新增 SKU（延续既有编号） =====
INSERT IGNORE INTO product_sku (id, product_id, specs, price, stock, sales, status) VALUES
(25, 52, '{"颜色":"曜石黑"}', 599.00, 300, 600, 1),
(26, 52, '{"颜色":"珍珠白"}', 599.00, 100, 300, 1),
(27, 65, '{"颜色":"麻灰","尺码":"M"}', 199.00, 250, 600, 1),
(28, 65, '{"颜色":"麻灰","尺码":"L"}', 199.00, 250, 700, 1),
(29, 71, '{"颜色":"荧光绿","尺码":"41"}', 599.00, 120, 400, 1),
(30, 71, '{"颜色":"荧光绿","尺码":"42"}', 599.00, 150, 450, 1);

-- ===== 四、新增商品类目关联 =====
INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(52, 1), (52, 12),
(53, 1), (53, 12),
(54, 1), (54, 12),
(55, 1),
(56, 1),
(57, 1),
(58, 1), (58, 51),
(59, 1),
(60, 1),
(61, 1), (61, 51),
(62, 1), (62, 51),
(63, 1), (63, 13),
(64, 1), (64, 13),
(65, 3), (65, 31),
(66, 3), (66, 31),
(67, 3), (67, 31),
(68, 3),
(69, 3),
(70, 3),
(71, 3), (71, 32),
(72, 3), (72, 32),
(73, 3), (73, 32),
(74, 3), (74, 31),
(75, 4), (75, 41),
(76, 4), (76, 41),
(77, 4), (77, 41),
(78, 4), (78, 41),
(79, 4),
(80, 4),
(81, 4),
(82, 4), (82, 42),
(83, 4), (83, 42),
(84, 5),
(85, 5),
(86, 5), (86, 52),
(87, 5), (87, 52),
(88, 5), (88, 52),
(89, 6),
(90, 6),
(91, 6), (91, 61),
(92, 6);
