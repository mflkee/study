-- =========================================
-- 0) exts + uuid-ossp
-- =========================================
CREATE SCHEMA IF NOT EXISTS exts;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA exts;

-- =========================================
-- 1) основная схема
-- =========================================
CREATE SCHEMA IF NOT EXISTS makeev_food;
SET search_path = makeev_food, exts, public;

-- =========================================
-- 2) ENUM'ы
-- =========================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
                 WHERE t.typname='order_status' AND n.nspname='makeev_food') THEN
    CREATE TYPE makeev_food.order_status AS ENUM (
      'создан',
      'оплачен',
      'в_доставке',
      'доставлен',
      'отменен'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
                 WHERE t.typname='product_status' AND n.nspname='makeev_food') THEN
    CREATE TYPE makeev_food.product_status AS ENUM ('активен','неактивен');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
                 WHERE t.typname='staff_status' AND n.nspname='makeev_food') THEN
    CREATE TYPE makeev_food.staff_status AS ENUM ('активен','уволен');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
                 WHERE t.typname='pay_type' AND n.nspname='makeev_food') THEN
    CREATE TYPE makeev_food.pay_type AS ENUM ('карта');
  END IF;
END $$;

-- =========================================
-- 3) таблицы
-- =========================================

CREATE TABLE IF NOT EXISTS users (
  user_id uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  fio     varchar(150) NOT NULL,
  email   varchar(120),
  phone   varchar(20) NOT NULL,
  UNIQUE (email),
  UNIQUE (phone)
);

CREATE TABLE IF NOT EXISTS address_user (
  address_user_id uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  user_id         uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  street          varchar(120) NOT NULL,
  house           varchar(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS address_rest (
  address_rest_id uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  street          varchar(120) NOT NULL,
  house           varchar(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS restaurant (
  restaurant_id   uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  name            varchar(120) NOT NULL,
  address_rest_id uuid NOT NULL UNIQUE REFERENCES address_rest(address_rest_id) ON DELETE RESTRICT,
  requisites      text,
  work_time       tsrange,
  rate            numeric(3,2) CHECK (rate >= 0 AND rate <= 5)
);

CREATE TABLE IF NOT EXISTS product_category (
  product_category_id uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  name                varchar(80) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS product (
  product_id          uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  restaurant_id       uuid NOT NULL REFERENCES restaurant(restaurant_id) ON DELETE CASCADE,
  price               numeric(12,2) NOT NULL CHECK (price > 0),
  price_with_tax      numeric(12,2) NOT NULL CHECK (price_with_tax > 0),
  status              product_status NOT NULL DEFAULT 'активен',
  name                varchar(120) NOT NULL,
  weight              numeric(8,2) NOT NULL CHECK (weight > 0),
  description         text,
  product_category_id uuid NOT NULL REFERENCES product_category(product_category_id) ON DELETE RESTRICT,
  rate                numeric(3,2) CHECK (rate >= 0 AND rate <= 5)
);

CREATE TABLE IF NOT EXISTS staff (
  staff_id       uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  fio            text NOT NULL,
  salary_rate    numeric(5,2) NOT NULL CHECK (salary_rate >= 0 AND salary_rate <= 100),
  status         staff_status NOT NULL DEFAULT 'активен',
  hire_date      date NOT NULL DEFAULT current_date,
  dismissal_date date,
  work_time      tsrange
);

CREATE TABLE IF NOT EXISTS staff_salary_rate_hist (
  hist_id     uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  staff_id    uuid NOT NULL REFERENCES staff(staff_id) ON DELETE CASCADE,
  valid_from  date NOT NULL,
  valid_to    date,
  salary_rate numeric(5,2) NOT NULL,
  changed_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS staff_pay (
  staff_id  uuid NOT NULL REFERENCES staff(staff_id) ON DELETE CASCADE,
  month     int  NOT NULL CHECK (month BETWEEN 1 AND 12),
  year      int  NOT NULL CHECK (year >= 2000),
  total_pay numeric(12,2) NOT NULL CHECK (total_pay >= 0),
  PRIMARY KEY (staff_id, year, month)
);

CREATE TABLE IF NOT EXISTS "order" (
  order_id               uuid PRIMARY KEY DEFAULT exts.uuid_generate_v4(),
  user_id                uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
  restaurant_id          uuid NOT NULL REFERENCES restaurant(restaurant_id) ON DELETE RESTRICT,
  description            text,
  pay_type               pay_type NOT NULL DEFAULT 'карта',
  delivery_period        tsrange,
  status                 order_status NOT NULL DEFAULT 'создан',
  staff_id               uuid NOT NULL REFERENCES staff(staff_id) ON DELETE RESTRICT,
  delivery_end_timestamp timestamp,
  total_amount           numeric(12,2) CHECK (total_amount >= 0),
  total_amount_tax       numeric(12,2) CHECK (total_amount_tax >= 0),
  CHECK (total_amount_tax IS NULL OR total_amount_tax >= 1000),
  CHECK (total_amount_tax IS NULL OR total_amount IS NULL OR total_amount_tax >= total_amount)
);

CREATE TABLE IF NOT EXISTS order_items (
  order_id  uuid NOT NULL REFERENCES "order"(order_id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES product(product_id) ON DELETE RESTRICT,
  qty       numeric(8,2) NOT NULL CHECK (qty > 0),
  PRIMARY KEY (order_id, product_id)
);

-- =========================================
-- 4) insert_test_data(value)
-- =========================================
CREATE OR REPLACE PROCEDURE insert_test_data(value int)
LANGUAGE plpgsql
AS $$
DECLARE
  i int;
  j int;
  v_user_id uuid;
  v_rest_id uuid;
  v_staff_id uuid;
  v_address_id uuid;
  v_category_id uuid;
  v_product_id uuid;
  v_order_id uuid;
  v_price numeric(12,2);
  v_price_tax numeric(12,2);
  v_qty numeric(8,2);
  v_total numeric(12,2);
  v_total_tax numeric(12,2);
  v_start_ts timestamp;
  v_end_ts timestamp;
  v_items_count int;
  v_status order_status;
  v_staff_status staff_status;
  v_product_status product_status;
  v_fio text;
  v_rest_name text;
  v_product_name text;
  v_desc text;
  fio_list text[] := ARRAY[
    'Иванов Иван Иванович','Петров Петр Петрович','Сидоров Сергей Сергеевич',
    'Кузнецов Алексей Николаевич','Смирнова Анна Викторовна','Федорова Мария Андреевна',
    'Орлов Дмитрий Игоревич','Николаева Ольга Павловна','Зайцев Максим Олегович'
  ];
  street_list text[] := ARRAY['Ленина','Советская','Мира','Пушкина','Гагарина','Кирова','Чапаева'];
  rest_list text[] := ARRAY['Дом Пельменей','Сибирская кухня','Уют','Суши Тайм','Печь','Вок и Плов','Кофейня Парк'];
  product_list text[] := ARRAY['Борщ','Пельмени','Сырники','Блины','Плов','Оливье','Шашлык','Рататуй','Котлета'];
  desc_list text[] := ARRAY['Домашний','Фирменный','Классический','Нежный','Сытный','Острый'];
  category_list text[] := ARRAY['Супы','Горячее','Салаты','Десерты','Напитки'];
BEGIN
  IF value IS NULL OR value <= 0 THEN
    RAISE EXCEPTION 'value must be positive';
  END IF;

  FOREACH v_desc IN ARRAY category_list LOOP
    INSERT INTO product_category(name)
    VALUES (v_desc)
    ON CONFLICT (name) DO NOTHING;
  END LOOP;

  -- пользователи + адреса
  FOR i IN 1..value LOOP
    v_fio := fio_list[1 + floor(random()*array_length(fio_list,1))::int];

    INSERT INTO users(fio, email, phone)
    VALUES (
      v_fio,
      format('user%s@mail.ru', i),
      '79'||lpad((floor(random()*1000000000))::text,9,'0')
    )
    RETURNING user_id INTO v_user_id;

    INSERT INTO address_user(user_id, street, house)
    VALUES (
      v_user_id,
      street_list[1 + floor(random()*array_length(street_list,1))::int],
      (1 + floor(random()*200))::text
    );
  END LOOP;

  -- рестораны + адреса
  FOR i IN 1..value LOOP
    INSERT INTO address_rest(street, house)
    VALUES (
      street_list[1 + floor(random()*array_length(street_list,1))::int],
      (1 + floor(random()*200))::text
    )
    RETURNING address_rest_id INTO v_address_id;

    v_rest_name := rest_list[1 + floor(random()*array_length(rest_list,1))::int];

    INSERT INTO restaurant(name, address_rest_id, requisites, work_time, rate)
    VALUES (
      v_rest_name,
      v_address_id,
      format('ИНН %s', 1000000000 + floor(random()*9000000000)::bigint),
      tsrange(date_trunc('day', now()) + time '10:00', date_trunc('day', now()) + time '22:00', '[)'),
      round((3 + random()*2)::numeric, 2)
    )
    RETURNING restaurant_id INTO v_rest_id;
  END LOOP;

  -- курьеры (staff) + история ставок
  FOR i IN 1..value LOOP
    v_staff_status := (enum_range(NULL::staff_status))[1 + floor(random()*array_length(enum_range(NULL::staff_status),1))::int];
    IF i = 1 THEN
      v_staff_status := 'активен';
    END IF;

    v_fio := fio_list[1 + floor(random()*array_length(fio_list,1))::int];

    INSERT INTO staff(fio, salary_rate, status, hire_date, dismissal_date, work_time)
    VALUES (
      v_fio,
      2.00,
      v_staff_status,
      current_date - (floor(random()*365))::int,
      CASE
        WHEN v_staff_status = 'уволен' THEN current_date - (floor(random()*30))::int
        ELSE NULL
      END,
      tsrange(date_trunc('day', now()) + time '10:00', date_trunc('day', now()) + time '22:00', '[)')
    )
    RETURNING staff_id INTO v_staff_id;

    INSERT INTO staff_salary_rate_hist(staff_id, valid_from, valid_to, salary_rate)
    VALUES (v_staff_id, (date_trunc('month', current_date) - interval '1 month')::date, NULL, 2.00);
  END LOOP;

  -- продукты
  FOR i IN 1..(value*5) LOOP
    SELECT restaurant_id INTO v_rest_id FROM restaurant ORDER BY random() LIMIT 1;
    SELECT product_category_id INTO v_category_id FROM product_category ORDER BY random() LIMIT 1;

    v_product_name := product_list[1 + floor(random()*array_length(product_list,1))::int];
    v_desc := desc_list[1 + floor(random()*array_length(desc_list,1))::int];
    v_price := round((200 + random()*1500)::numeric, 2);
    v_product_status := (enum_range(NULL::product_status))[1 + floor(random()*array_length(enum_range(NULL::product_status),1))::int];

    INSERT INTO product(
      restaurant_id, price, price_with_tax, status, name, weight, description, product_category_id, rate
    )
    VALUES (
      v_rest_id,
      v_price,
      round(v_price * 1.10, 2),
      v_product_status,
      v_product_name,
      round((100 + random()*900)::numeric, 2),
      v_desc || ' ' || v_product_name,
      v_category_id,
      round((3 + random()*2)::numeric, 2)
    )
    RETURNING product_id INTO v_product_id;
  END LOOP;

  -- заказы + позиции
  FOR i IN 1..(value*5) LOOP
    SELECT user_id INTO v_user_id FROM users ORDER BY random() LIMIT 1;
    SELECT restaurant_id INTO v_rest_id FROM restaurant ORDER BY random() LIMIT 1;
    SELECT staff_id INTO v_staff_id FROM staff ORDER BY random() LIMIT 1;

    v_start_ts := now() - (random()*interval '180 days');
    v_end_ts := v_start_ts + (interval '30 minutes' + random()*interval '90 minutes');
    v_status := (enum_range(NULL::order_status))[1 + floor(random()*array_length(enum_range(NULL::order_status),1))::int];

    INSERT INTO "order"(
      user_id, restaurant_id, description, pay_type, delivery_period, status, staff_id, delivery_end_timestamp,
      total_amount, total_amount_tax
    )
    VALUES (
      v_user_id,
      v_rest_id,
      'Заказ №'||i,
      'карта',
      tsrange(v_start_ts, v_end_ts, '[)'),
      v_status,
      v_staff_id,
      CASE WHEN v_status = 'доставлен' THEN v_end_ts ELSE NULL END,
      NULL,
      NULL
    )
    RETURNING order_id INTO v_order_id;

    v_items_count := 1 + floor(random()*3)::int;

    FOR j IN 1..v_items_count LOOP
      SELECT product_id, price, price_with_tax
      INTO v_product_id, v_price, v_price_tax
      FROM product
      WHERE restaurant_id = v_rest_id
      ORDER BY random()
      LIMIT 1;

      v_qty := (1 + floor(random()*3))::numeric;

      INSERT INTO order_items(order_id, product_id, qty)
      VALUES (v_order_id, v_product_id, v_qty)
      ON CONFLICT (order_id, product_id) DO UPDATE
        SET qty = order_items.qty + EXCLUDED.qty;
    END LOOP;

    SELECT
      round(SUM(p.price * oi.qty), 2),
      round(SUM(p.price_with_tax * oi.qty), 2)
    INTO v_total, v_total_tax
    FROM order_items oi
    JOIN product p ON p.product_id = oi.product_id
    WHERE oi.order_id = v_order_id;

    IF v_total_tax < 1000 THEN
      SELECT oi.product_id, p.price_with_tax
      INTO v_product_id, v_price_tax
      FROM order_items oi
      JOIN product p ON p.product_id = oi.product_id
      WHERE oi.order_id = v_order_id
      LIMIT 1;

      UPDATE order_items
      SET qty = qty + CEIL((1000 - v_total_tax) / v_price_tax)
      WHERE order_id = v_order_id AND product_id = v_product_id;

      SELECT
        round(SUM(p.price * oi.qty), 2),
        round(SUM(p.price_with_tax * oi.qty), 2)
      INTO v_total, v_total_tax
      FROM order_items oi
      JOIN product p ON p.product_id = oi.product_id
      WHERE oi.order_id = v_order_id;
    END IF;

    UPDATE "order"
    SET total_amount = v_total,
        total_amount_tax = v_total_tax
    WHERE order_id = v_order_id;
  END LOOP;

  -- гарантируем заказы за прошлый месяц для расчета выплат
  WITH sample AS (
    SELECT u.user_id, r.restaurant_id, s.staff_id
    FROM users u
    CROSS JOIN staff s
    JOIN restaurant r ON EXISTS (
      SELECT 1 FROM product p WHERE p.restaurant_id = r.restaurant_id
    )
    LIMIT 1
  )
  INSERT INTO "order"(
    user_id, restaurant_id, description, pay_type, delivery_period, status, staff_id, delivery_end_timestamp,
    total_amount, total_amount_tax
  )
  SELECT
    s.user_id,
    s.restaurant_id,
    'Контрольный заказ',
    'карта',
    tsrange(
      date_trunc('month', current_date) - interval '1 month' + interval '10 days',
      date_trunc('month', current_date) - interval '1 month' + interval '10 days' + interval '1 hour',
      '[)'
    ),
    'доставлен',
    s.staff_id,
    (date_trunc('month', current_date) - interval '1 month' + interval '10 days' + interval '1 hour')::timestamp,
    1000,
    1100
  FROM sample s;

  INSERT INTO order_items(order_id, product_id, qty)
  SELECT o.order_id, p.product_id, 1
  FROM "order" o
  JOIN product p ON p.restaurant_id = o.restaurant_id
  WHERE o.description = 'Контрольный заказ'
  LIMIT 1;

  UPDATE "order" o
  SET total_amount = 1000,
      total_amount_tax = 1100
  WHERE o.description = 'Контрольный заказ';

  CALL staff_salary();
END $$;

-- =========================================
-- 5) get_products(restaurant_id)
-- =========================================
CREATE OR REPLACE FUNCTION get_products(p_restaurant_id uuid)
RETURNS TABLE (
  name varchar,
  price numeric,
  price_with_tax numeric,
  weight numeric,
  description text
)
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_restaurant_id IS NULL THEN
    RETURN QUERY SELECT 'Выберите ресторан'::varchar, NULL::numeric, NULL::numeric, NULL::numeric, NULL::text;
    RETURN;
  END IF;

  RETURN QUERY
  SELECT p.name, p.price, p.price_with_tax, p.weight, p.description
  FROM product p
  WHERE p.restaurant_id = p_restaurant_id
  ORDER BY p.name;
END $$;

-- =========================================
-- 6) get_statistic()
-- =========================================
CREATE OR REPLACE FUNCTION get_statistic()
RETURNS TABLE (
  restaurant_name varchar,
  best_product_name varchar,
  total_amount numeric,
  avg_amount numeric,
  best_user varchar
)
LANGUAGE sql
AS $$
WITH order_totals AS (
  SELECT o.restaurant_id,
         SUM(o.total_amount_tax) AS total_amount,
         AVG(o.total_amount_tax) AS avg_amount
  FROM "order" o
  GROUP BY o.restaurant_id
),
product_rank AS (
  SELECT o.restaurant_id,
         oi.product_id,
         SUM(oi.qty) AS qty_sum,
         ROW_NUMBER() OVER (
           PARTITION BY o.restaurant_id
           ORDER BY SUM(oi.qty) DESC, oi.product_id
         ) AS rn
  FROM "order" o
  JOIN order_items oi ON oi.order_id = o.order_id
  GROUP BY o.restaurant_id, oi.product_id
),
best_product AS (
  SELECT restaurant_id, product_id
  FROM product_rank
  WHERE rn = 1
),
user_rank AS (
  SELECT o.restaurant_id,
         o.user_id,
         COUNT(*) AS order_cnt,
         ROW_NUMBER() OVER (
           PARTITION BY o.restaurant_id
           ORDER BY COUNT(*) DESC, o.user_id
         ) AS rn
  FROM "order" o
  GROUP BY o.restaurant_id, o.user_id
),
best_user AS (
  SELECT restaurant_id, user_id
  FROM user_rank
  WHERE rn = 1
)
SELECT
  r.name AS restaurant_name,
  COALESCE(p.name, '')::varchar AS best_product_name,
  round(COALESCE(ot.total_amount, 0), 2) AS total_amount,
  round(COALESCE(ot.avg_amount, 0), 2) AS avg_amount,
  COALESCE(u.fio, '')::varchar AS best_user
FROM restaurant r
LEFT JOIN order_totals ot ON ot.restaurant_id = r.restaurant_id
LEFT JOIN best_product bp ON bp.restaurant_id = r.restaurant_id
LEFT JOIN product p ON p.product_id = bp.product_id
LEFT JOIN best_user bu ON bu.restaurant_id = r.restaurant_id
LEFT JOIN users u ON u.user_id = bu.user_id
ORDER BY r.name;
$$;

-- =========================================
-- 7) add_product(атрибуты product)
-- =========================================
CREATE OR REPLACE PROCEDURE add_product(
  p_restaurant_id uuid,
  p_price numeric,
  p_price_with_tax numeric,
  p_status product_status,
  p_name varchar,
  p_weight numeric,
  p_description text,
  p_product_category_id uuid,
  p_rate numeric
)
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO product(
    restaurant_id, price, price_with_tax, status, name, weight, description, product_category_id, rate
  )
  VALUES (
    p_restaurant_id,
    p_price,
    p_price_with_tax,
    p_status,
    p_name,
    p_weight,
    p_description,
    p_product_category_id,
    p_rate
  );
END $$;

-- =========================================
-- 8) staff_salary()
-- =========================================
CREATE OR REPLACE PROCEDURE staff_salary()
LANGUAGE plpgsql
AS $$
DECLARE
  prev_month_start date := (date_trunc('month', current_date) - interval '1 month')::date;
  curr_month_start date := (date_trunc('month', current_date))::date;
BEGIN
  INSERT INTO staff_pay(staff_id, month, year, total_pay)
  SELECT
    s.staff_id,
    EXTRACT(MONTH FROM prev_month_start)::int,
    EXTRACT(YEAR FROM prev_month_start)::int,
    round(
      COALESCE(
        SUM((o.total_amount_tax - o.total_amount) * (s.salary_rate / 100.0)),
        0
      ),
      2
    ) AS total_pay
  FROM staff s
  LEFT JOIN "order" o
    ON o.staff_id = s.staff_id
   AND o.delivery_end_timestamp >= prev_month_start
   AND o.delivery_end_timestamp < curr_month_start
   AND o.status = 'доставлен'
  GROUP BY s.staff_id
  ON CONFLICT (staff_id, year, month) DO UPDATE
    SET total_pay = EXCLUDED.total_pay;

  WITH deliveries AS (
    SELECT o.staff_id, COUNT(*) AS cnt
    FROM "order" o
    WHERE o.delivery_end_timestamp >= prev_month_start
      AND o.delivery_end_timestamp < curr_month_start
      AND o.status = 'доставлен'
    GROUP BY o.staff_id
  ),
  new_rate AS (
    SELECT s.staff_id,
           CASE
             WHEN COALESCE(d.cnt,0) <= 150 THEN 2.00
             WHEN COALESCE(d.cnt,0) <= 250 THEN 3.00
             WHEN COALESCE(d.cnt,0) <= 500 THEN 4.00
             ELSE 5.00
           END AS salary_rate
    FROM staff s
    LEFT JOIN deliveries d ON d.staff_id = s.staff_id
  )
  UPDATE staff s
  SET salary_rate = nr.salary_rate
  FROM new_rate nr
  WHERE s.staff_id = nr.staff_id;

  UPDATE staff_salary_rate_hist h
  SET valid_to = curr_month_start - interval '1 day'
  WHERE h.valid_to IS NULL AND h.valid_from < curr_month_start;

  INSERT INTO staff_salary_rate_hist(staff_id, valid_from, valid_to, salary_rate)
  SELECT s.staff_id, curr_month_start, NULL, s.salary_rate
  FROM staff s
  WHERE NOT EXISTS (
    SELECT 1
    FROM staff_salary_rate_hist h
    WHERE h.staff_id = s.staff_id
      AND h.valid_from = curr_month_start
      AND h.valid_to IS NULL
  );
END $$;

-- =========================================
-- 9) VIEW how_much_money
-- =========================================
CREATE OR REPLACE VIEW how_much_money AS
WITH orders_by_month AS (
  SELECT date_trunc('month', o.delivery_end_timestamp)::date AS month_start,
         SUM(o.total_amount) AS sum_without_commission,
         SUM(o.total_amount_tax) AS sum_with_commission
  FROM "order" o
  WHERE o.delivery_end_timestamp IS NOT NULL
  GROUP BY 1
),
commission_by_month AS (
  SELECT
    month_start,
    sum_without_commission,
    sum_with_commission,
    (sum_with_commission - sum_without_commission) AS commission_size
  FROM orders_by_month
),
courier_payments AS (
  SELECT make_date(sp.year, sp.month, 1) AS month_start,
         SUM(sp.total_pay) AS courier_payment
  FROM staff_pay sp
  GROUP BY 1
)
SELECT
  cbm.month_start AS "год и месяц",
  round(cbm.sum_without_commission,2) AS "сумма за месяц без комиссии",
  round(cbm.sum_with_commission,2) AS "сумма за месяц с комиссией",
  round(cbm.commission_size,2) AS "размер комиссии",
  round(LAG(cbm.commission_size) OVER (ORDER BY cbm.month_start),2) AS "размер комиссии за предыдущий месяц",
  round(cbm.commission_size - LAG(cbm.commission_size) OVER (ORDER BY cbm.month_start),2) AS "разница в комиссии между текущим и предыдущим месяцем",
  round(COALESCE(cp.courier_payment,0),2) AS "размер оплаты курьерам",
  round(cbm.commission_size - COALESCE(cp.courier_payment,0),2) AS "чистая прибыль"
FROM commission_by_month cbm
LEFT JOIN courier_payments cp ON cp.month_start = cbm.month_start
ORDER BY cbm.month_start;

-- =========================================
-- 10) inspector + права
-- =========================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='inspector') THEN
    CREATE ROLE inspector LOGIN PASSWORD 'NetoSQL2025';
  END IF;
END $$;

GRANT USAGE, CREATE ON SCHEMA makeev_food TO inspector;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA makeev_food TO inspector;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA makeev_food TO inspector;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA makeev_food TO inspector;

ALTER DEFAULT PRIVILEGES IN SCHEMA makeev_food GRANT ALL ON TABLES    TO inspector;
ALTER DEFAULT PRIVILEGES IN SCHEMA makeev_food GRANT ALL ON SEQUENCES TO inspector;
ALTER DEFAULT PRIVILEGES IN SCHEMA makeev_food GRANT ALL ON FUNCTIONS TO inspector;

GRANT USAGE ON SCHEMA exts TO inspector;

GRANT USAGE ON SCHEMA information_schema TO inspector;
GRANT USAGE ON SCHEMA pg_catalog TO inspector;
GRANT SELECT ON ALL TABLES IN SCHEMA information_schema TO inspector;
GRANT SELECT ON ALL TABLES IN SCHEMA pg_catalog TO inspector;
