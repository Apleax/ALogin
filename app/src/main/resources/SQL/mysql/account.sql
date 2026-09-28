CREATE TABLE `account`
(
    `id`                         bigint unsigned NOT NULL COMMENT 'id',
    `account`                    varchar(8)      NOT NULL COMMENT '账号',
    `email`                      varchar(255)    NOT NULL COMMENT '邮箱',
    `nick_name`                  varchar(32)     NOT NULL COMMENT '昵称',
    `password`                   varchar(255)    NOT NULL COMMENT '密码',
    `salt`                       varchar(32)     NOT NULL COMMENT '盐值',
    `algorithm`                  varchar(15)     NOT NULL COMMENT '使用的加密算法',
    `avatar`                     varchar(255)                                                   DEFAULT NULL COMMENT '用户头像uuid',
    `mc_uuid`                    varchar(38)     NOT NULL COMMENT '服务器内uuid',
    `properties`                 varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '皮肤数据',
    `qq_account`                 int unsigned                                                   DEFAULT NULL COMMENT '绑定的QQ号',
    `registration_time`          bigint unsigned NOT NULL COMMENT '注册时间',
    `bind_qq_account_time`       bigint unsigned                                                DEFAULT NULL COMMENT '绑定qq时间',
    `last_login_ip`              varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  DEFAULT NULL COMMENT '最后登录IP',
    `last_change_nick_name_time` bigint                                                         DEFAULT NULL COMMENT '最后修改昵称时间',
    `previous_name`              varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci   DEFAULT NULL COMMENT '上次使用的昵称',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nick_name` (`nick_name`) COMMENT '昵称唯一索引',
    UNIQUE KEY `uk_uuid` (`mc_uuid`) COMMENT 'uuid唯一索引',
    UNIQUE KEY `un_email` (`email`) COMMENT '邮箱唯一索引',
    UNIQUE KEY `uk_account` (`account`) USING BTREE COMMENT '账号索引',
    UNIQUE KEY `uk_qq_account` (`qq_account`) COMMENT 'qq唯一索引',
    UNIQUE KEY `uk_previous_name` (`previous_name`) USING BTREE COMMENT '上次使用的昵称唯一索引'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  ROW_FORMAT = DYNAMIC