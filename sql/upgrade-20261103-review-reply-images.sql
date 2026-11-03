-- 评价增强：商家回复 + 评价晒图
ALTER TABLE review
    ADD COLUMN images  TEXT     NULL COMMENT '评价图片 URL JSON 数组' AFTER content,
    ADD COLUMN reply   TEXT     NULL COMMENT '商家回复' AFTER images,
    ADD COLUMN reply_at DATETIME NULL COMMENT '商家回复时间' AFTER reply;
