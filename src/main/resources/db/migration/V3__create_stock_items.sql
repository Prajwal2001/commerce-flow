create table stock_item
(
    product_id uuid,
    available  int    not null,
    version    bigint not null,
    constraint pk_stock_item primary key (product_id),
    constraint positive_available check ( available >= 0 )
);