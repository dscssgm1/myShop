-- ===== 2026-11-05 MinIO 切到线上对象存储 =====
-- 背景：图片 URL 在上传时被拼成绝对地址写进库，旧值指向 http://127.0.0.1:9000，
--       别的电脑解析到自己本机的 9000 端口，图片必然裂。
-- 处理：本地 14 个对象已 mc mirror 到线上桶（同一套 minioadmin 凭据、同样带 download 匿名读策略），
--       库里的旧绝对地址统一换成线上地址。商品 14/16（用户自建）仅改图片主机名，不动其它字段。

UPDATE product
SET cover_image = REPLACE(REPLACE(cover_image, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                          'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE cover_image LIKE 'http://127.0.0.1:9000%' OR cover_image LIKE 'http://localhost:9000%';

UPDATE product
SET images = REPLACE(REPLACE(images, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                     'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE images LIKE '%http://127.0.0.1:9000%' OR images LIKE '%http://localhost:9000%';

UPDATE shop
SET icon = REPLACE(REPLACE(icon, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                   'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE icon LIKE '%http://127.0.0.1:9000%' OR icon LIKE '%http://localhost:9000%';

UPDATE shop
SET license_image = REPLACE(REPLACE(license_image, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                            'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE license_image LIKE '%http://127.0.0.1:9000%' OR license_image LIKE '%http://localhost:9000%';

UPDATE shop_apply
SET license_image = REPLACE(REPLACE(license_image, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                            'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE license_image LIKE '%http://127.0.0.1:9000%' OR license_image LIKE '%http://localhost:9000%';

UPDATE order_item
SET product_cover_image = REPLACE(REPLACE(product_cover_image, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                                  'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE product_cover_image LIKE '%http://127.0.0.1:9000%' OR product_cover_image LIKE '%http://localhost:9000%';

UPDATE review
SET images = REPLACE(REPLACE(images, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                     'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE images LIKE '%http://127.0.0.1:9000%' OR images LIKE '%http://localhost:9000%';

UPDATE sys_points_mall_item
SET product_cover_image = REPLACE(REPLACE(product_cover_image, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                                  'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE product_cover_image LIKE '%http://127.0.0.1:9000%' OR product_cover_image LIKE '%http://localhost:9000%';

UPDATE sys_user
SET avatar = REPLACE(REPLACE(avatar, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                     'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE avatar LIKE '%http://127.0.0.1:9000%' OR avatar LIKE '%http://localhost:9000%';

-- 补漏：品牌 logo 列（首轮迁移漏掉，2026-11-05 补）
UPDATE product_brand
SET logo = REPLACE(REPLACE(logo, 'http://127.0.0.1:9000', 'http://101.132.73.8:9000'),
                   'http://localhost:9000', 'http://101.132.73.8:9000')
WHERE logo LIKE '%http://127.0.0.1:9000%' OR logo LIKE '%http://localhost:9000%';
