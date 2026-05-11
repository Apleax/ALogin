create table alogin.skin
(
    mc_uuid   varchar(38)   not null
        primary key,
    skin_info varchar(5000) not null,
    skin_img  blob          not null
) row_format = DYNAMIC;