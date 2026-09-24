-- 0001_fixtures.sql — таблицы «фикстур» модуля 12 (каталог + заказы для тестов функций).

CREATE TABLE products (
    id    serial PRIMARY KEY,
    sku   text NOT NULL UNIQUE,
    name  text NOT NULL,
    price numeric(12,4) NOT NULL
);

CREATE TABLE orders (
    id         serial PRIMARY KEY,
    product_id int NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    qty        int NOT NULL CHECK (qty > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);