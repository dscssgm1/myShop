-- 2026-11-03 店铺标识改为上传图片：icon 由 emoji（16 字符）改为图片 URL
-- 旧数据中的 emoji 保留，前端按「URL 显示图片、否则显示 emoji/🏪」回退
ALTER TABLE shop MODIFY icon VARCHAR(500) NOT NULL DEFAULT '' COMMENT '店铺标识：图片 URL（旧数据为 emoji，作回退展示）';
