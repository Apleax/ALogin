create table account
(
    id                   bigint unsigned not null comment 'id'
        primary key,
    account              varchar(8)      not null comment '账号',
    email                varchar(255)    not null comment '邮箱',
    nick_name            varchar(32)     not null comment '昵称',
    password             varchar(255)    not null comment '密码',
    salt                 varchar(32)     not null comment '盐值',
    algorithm            varchar(15)     not null comment '使用的加密算法',
    avatar               varchar(255)    null comment '用户头像uuid',
    mc_uuid              varchar(36)     not null comment '服务器内uuid',
    qq_account           int unsigned    null comment '绑定的QQ号',
    registration_time    bigint unsigned not null comment '注册时间',
    bind_mc_account_time bigint unsigned null comment '绑定mc时间',
    bind_qq_account_time bigint unsigned null comment '绑定qq时间',
    last_login_ip        varchar(20)     null comment '最后登录IP',
    constraint uk_qq_account
        unique (qq_account) comment 'qq唯一索引',
    constraint uk_uuid
        unique (mc_uuid) comment 'uuid唯一索引',
    constraint un_email
        unique (email) comment '邮箱唯一索引'
)
    row_format = DYNAMIC;

DYNAMIC;

