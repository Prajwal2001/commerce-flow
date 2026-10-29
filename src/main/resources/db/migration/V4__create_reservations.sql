create table reservation
(
    id         uuid         not null primary key,
    product_id uuid         not null,
    quantity   int          not null,
    status     varchar(255) not null,
    created_at timestamptz  not null,
    constraint positive_quantity check ( quantity >= 0 ),
    constraint available_status check ( status in ('RESERVED', 'CONFIRMED', 'RELEASED')),
    constraint fk_product_stock_item foreign key (product_id) references stock_item(product_id)
);