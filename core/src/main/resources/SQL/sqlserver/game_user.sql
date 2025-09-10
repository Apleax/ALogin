CREATE TABLE game_user
(
    uuid              NVARCHAR(36)    NOT NULL,
    name              NVARCHAR(20)    NULL,
    last_login_server INT NULL,

    CONSTRAINT pk_game_user PRIMARY KEY (uuid),
    CONSTRAINT fk_game_user_main_user_uuid FOREIGN KEY (uuid)
        REFERENCES account (mc_uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);