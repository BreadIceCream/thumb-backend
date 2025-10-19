create table if not exists blog
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null comment '所属用户id',
    title      varchar(512)                       null comment '标题',
    coverImg   varchar(1024)                      null comment '封面',
    content    text                               not null comment '内容',
    thumbCount int      default 0                 not null comment '点赞数',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
);

create index idx_userId
    on blog (userId);

create table if not exists thumb
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null comment '点赞用户id',
    blogId     bigint                             not null comment '点赞blog id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint idx_userId_blogId
        unique (userId, blogId)
);

create table if not exists user
(
    id       bigint auto_increment
        primary key,
    username varchar(128) not null
);

