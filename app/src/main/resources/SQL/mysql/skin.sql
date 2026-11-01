CREATE TABLE `skin`
(
    `id`        bigint                                                       NOT NULL,
    `mc_uuid`   varchar(38)                                                  NOT NULL,
    `skin_uuid` varchar(38) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `skin_img`  blob                                                         NOT NULL,
    -- 头像贴图缓存：16×8 PNG（左 8×8 头部正面 + 右 8×8 帽子层正面），由 skin_img 生成；为空时读路径现场生成
    `avatar`    blob                                                         DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `fk_mc_uuid` (`mc_uuid`) USING BTREE,
    -- skin_uuid 不加唯一约束：不同玩家上传相同图片会得到相同 skin_uuid，需允许多行共享同一纹理
    KEY `idx_skin_uuid` (`skin_uuid`) USING BTREE,
    CONSTRAINT `fk_uuid` FOREIGN KEY (`mc_uuid`) REFERENCES `account` (`mc_uuid`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci

-- 存量库迁移（本文件的建表语句只对"表不存在"时执行，已有 skin 表的库必须手工执行下面这条，否则查询会报 Unknown column 'avatar'）：
-- ALTER TABLE `skin` ADD COLUMN `avatar` blob NULL COMMENT '头像贴图缓存：16×8 PNG（左面部/右帽子层）' AFTER `skin_img`;
-- SQL Server 等价写法：ALTER TABLE skin ADD avatar VARBINARY(MAX) NULL;