create table alogin.account_identity
(
    id            bigint unsigned not null auto_increment comment 'id'
        primary key,
    account_id    bigint unsigned not null comment 'account.id',
    provider      varchar(32)  not null comment '外部身份提供方',
    subject       varchar(255) not null comment '提供方内稳定主体',
    display_name  varchar(64)  null comment '绑定时的名称快照',
    issuer        varchar(128) null comment '断言签发者',
    status        varchar(16)  not null comment 'ACTIVE 或 REVOKED',
    verified_at   bigint unsigned not null comment '首次验证时间',
    last_seen_at  bigint unsigned not null comment '最近验证时间',
    created_at    bigint unsigned not null comment '创建时间',
    updated_at    bigint unsigned not null comment '更新时间',
    constraint uk_identity_provider_subject
        unique (provider, subject),
    constraint fk_identity_account
        foreign key (account_id) references alogin.account (id)
) row_format = DYNAMIC;
