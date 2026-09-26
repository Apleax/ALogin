create table alogin.identity_assertion_replay
(
    id         bigint unsigned not null auto_increment comment 'id'
        primary key,
    jti        varchar(128)    not null comment '断言一次性 ID',
    expires_at bigint unsigned not null comment '断言过期时间',
    created_at bigint unsigned not null comment '消费时间',
    constraint uk_identity_assertion_replay_jti
        unique (jti)
) row_format = DYNAMIC;
