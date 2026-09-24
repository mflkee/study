-- 0002_order_total.sql — функция для интеграционных тестов + индекс.

-- Стоимость заказа: price × qty (в проде — decimal-точность: numeric).
CREATE OR REPLACE FUNCTION order_total(p_order_id int)
RETURNS numeric(14,4) LANGUAGE plpgsql AS $$
DECLARE
    v_total numeric(14,4);
BEGIN
    SELECT p.price * o.qty INTO v_total
      FROM orders o JOIN products p ON p.id = o.product_id
     WHERE o.id = p_order_id;
    IF v_total IS NULL THEN
        RAISE EXCEPTION 'заказ % не найден', p_order_id;
    END IF;
    RETURN v_total;
END $$;

CREATE INDEX IF NOT EXISTS orders_created_idx ON orders (created_at);