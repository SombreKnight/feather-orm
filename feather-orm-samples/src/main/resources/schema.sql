CREATE TABLE IF NOT EXISTS tb_user (
    id BIGINT PRIMARY KEY,
    user_name VARCHAR(64),
    age INT,
    status INT,
    ext_info VARCHAR(512),
    tags VARCHAR(512)
);
