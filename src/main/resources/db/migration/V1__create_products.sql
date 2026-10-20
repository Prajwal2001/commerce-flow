CREATE TABLE public.products
(
    id          uuid           NOT NULL primary key,
    created_at  timestamptz    NOT NULL,
    description varchar(2000) NULL,
    name        varchar(255)   NOT NULL,
    price       numeric(12, 2) NOT NULL,
    sku         varchar(64)    NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

