CREATE TABLE account
(
    id                   BIGINT NOT NULL,
    account              NVARCHAR(8)     NOT NULL,
    email                NVARCHAR(255)   NOT NULL,
    nick_name            NVARCHAR(32)    NOT NULL,
    password             NVARCHAR(255)   NOT NULL,
    salt                 NVARCHAR(32)    NOT NULL,
    algorithm            NVARCHAR(15)    NOT NULL,
    avatar               NVARCHAR(255)   NULL,
    mc_uuid              NVARCHAR(36)    NOT NULL,
    qq_account           INT NULL,
    registration_time    BIGINT NOT NULL,
    bind_mc_account_time BIGINT NULL,
    bind_qq_account_time BIGINT NULL,
    last_login_ip        NVARCHAR(20)    NULL,

    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uk_qq_account UNIQUE (qq_account),
    CONSTRAINT uk_uuid UNIQUE (mc_uuid),
    CONSTRAINT un_email UNIQUE (email)
);