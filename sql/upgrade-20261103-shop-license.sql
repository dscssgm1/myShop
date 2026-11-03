-- 2026-11-03 店铺资质信息：入驻申请与店铺增加工商资质字段
-- 经营许可证照片走公共文件桶，所有用户可见（买家店铺页公示）
ALTER TABLE shop
    ADD COLUMN license_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '经营许可证号' AFTER categories,
    ADD COLUMN license_image VARCHAR(500) NOT NULL DEFAULT '' COMMENT '经营许可证照片 URL' AFTER license_no,
    ADD COLUMN legal_person VARCHAR(50) NOT NULL DEFAULT '' COMMENT '法定代表人' AFTER license_image,
    ADD COLUMN registered_address VARCHAR(200) NOT NULL DEFAULT '' COMMENT '注册地址' AFTER legal_person,
    ADD COLUMN business_scope VARCHAR(200) NOT NULL DEFAULT '' COMMENT '经营范围' AFTER registered_address,
    ADD COLUMN business_term VARCHAR(100) NOT NULL DEFAULT '' COMMENT '营业期限，如 2024-03-15 至 2034-03-14' AFTER business_scope;

ALTER TABLE shop_apply
    ADD COLUMN license_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '经营许可证号' AFTER description,
    ADD COLUMN license_image VARCHAR(500) NOT NULL DEFAULT '' COMMENT '经营许可证照片 URL' AFTER license_no,
    ADD COLUMN legal_person VARCHAR(50) NOT NULL DEFAULT '' COMMENT '法定代表人' AFTER license_image,
    ADD COLUMN registered_address VARCHAR(200) NOT NULL DEFAULT '' COMMENT '注册地址' AFTER legal_person,
    ADD COLUMN business_scope VARCHAR(200) NOT NULL DEFAULT '' COMMENT '经营范围' AFTER registered_address,
    ADD COLUMN business_term VARCHAR(100) NOT NULL DEFAULT '' COMMENT '营业期限' AFTER business_scope;

-- 存量演示店铺补充示例资质
UPDATE shop SET license_no = '91110108MA01N5YX2T', legal_person = '张建国',
    registered_address = '北京市海淀区中关村大街 27 号', business_scope = '手机数码产品销售与维修；家用电器销售',
    business_term = '2021-06-18 至 2041-06-17' WHERE id = 1;
UPDATE shop SET license_no = '91110105MA02P3QK8H', legal_person = '李国强',
    registered_address = '北京市朝阳区望京街 10 号', business_scope = '家用电器销售与安装；售后服务',
    business_term = '2020-03-02 至 2040-03-01' WHERE id = 2;
UPDATE shop SET license_no = '91110105MA03W7RD4B', legal_person = '王志远',
    registered_address = '北京市朝阳区三里屯路 19 号', business_scope = '服装服饰、鞋帽、箱包零售',
    business_term = '2019-09-12 至 2039-09-11' WHERE id = 3;
UPDATE shop SET license_no = '91310115MA04K8TN9C', legal_person = '赵永刚',
    registered_address = '上海市浦东新区张江路 66 号', business_scope = '食品销售；新鲜水果零售；食用农产品初加工',
    business_term = '2022-01-20 至 2042-01-19' WHERE id = 4;
UPDATE shop SET license_no = '91110108MA05J2VP3D', legal_person = '钱学文',
    registered_address = '北京市海淀区学院路 15 号', business_scope = '出版物零售；文具用品销售',
    business_term = '2018-05-08 至 2038-05-07' WHERE id = 5;
UPDATE shop SET license_no = '91110105MA06T9QW7E', legal_person = '孙丽华',
    registered_address = '北京市朝阳区建国路 87 号', business_scope = '化妆品零售；日用百货销售',
    business_term = '2023-02-14 至 2043-02-13' WHERE id = 6;
