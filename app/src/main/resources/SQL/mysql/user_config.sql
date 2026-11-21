CREATE TABLE `user_config`
(
    `id`                 bigint unsigned NOT NULL,
    `account`            varchar(8)      NOT NULL COMMENT '账号',
    `auto_login_java`    tinyint(1)      NOT NULL DEFAULT 1 COMMENT 'Java 正版免密自动登录开关',
    `auto_login_bedrock` tinyint(1)      NOT NULL DEFAULT 1 COMMENT '基岩版免密自动登录开关',
    `update_time`        bigint unsigned DEFAULT NULL COMMENT '最后修改时间（毫秒）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account` (`account`),
    CONSTRAINT `fk_user_config_account` FOREIGN KEY (`account`) REFERENCES `account` (`account`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
