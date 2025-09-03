create table game_user
(
    uuid              varchar(36)  not null comment '服务器内uuid'
        primary key,
    name              varchar(20)  null comment '服务器内名称',
    last_login_server int unsigned null comment '最后登录的服务器id',
    constraint fk_game_user_main_user_uuid
        foreign key (uuid) references account (mc_uuid)
            on update cascade on delete cascade
)
    row_format = DYNAMIC;

