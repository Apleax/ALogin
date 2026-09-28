CREATE TABLE `skin`
(
    `id`        bigint                                                       NOT NULL,
    `mc_uuid`   varchar(38)                                                  NOT NULL,
    `skin_uuid` varchar(38) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `skin_img`  blob                                                         NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `fk_mc_uuid` (`mc_uuid`) USING BTREE,
    -- skin_uuid 不加唯一约束：不同玩家上传相同图片会得到相同 skin_uuid，需允许多行共享同一纹理
    KEY `idx_skin_uuid` (`skin_uuid`) USING BTREE,
    CONSTRAINT `fk_uuid` FOREIGN KEY (`mc_uuid`) REFERENCES `account` (`mc_uuid`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci