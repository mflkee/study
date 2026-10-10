# Документация MasterSCADA 4D 2.0 — индекс

Здесь два типа материалов:

1. **[`reference/`](reference/)** — **наши конспекты** (короткие, выверенные на стенде). Читать первыми.
2. **Онлайн-справка вендора** — основной источник истины по функциям и интерфейсу (см. ниже).
3. **`offline/`** — офлайн-зеркало справки (создаётся скриптом [`fetch-docs.sh`](fetch-docs.sh), в git не коммитится).

---

## 1. Локальные конспекты (`reference/`)

| Файл | О чём |
|---|---|
| [`reference/arhitektura-i-os.md`](reference/arhitektura-i-os.md) | Из чего состоит DT 2.0 (Electron/.NET/C++), требования, поддерживаемые ОС |
| [`reference/ustanovka-arch-niri.md`](reference/ustanovka-arch-niri.md) | Установка `.deb` и запуск на Arch + niri/Wayland (проверено) |

## 2. Онлайн-справка MasterSCADA 4D 2.0 (актуальная)

- **База:** <https://support.mps-soft.ru/ms4d_web/>
- **Оглавление:** <https://support.mps-soft.ru/ms4d_web/hmcontent.html>
- Разделы: *Инсталляция · Быстрый старт · Обзор интерфейса · Проект в MasterSCADA 4D ·
  Программирование · Создание окон для клиента визуализации · …* (см. ToC).

Как достать конкретную тему: имя файла из ToC → `https://support.mps-soft.ru/ms4d_web/<файл>.html`.
Примеры имён:

| Тема | Файл |
|---|---|
| Системные требования среды разработки | `sistemnwe_trebovaniya_dt.html` |
| Системные требования среды исполнения | `sistemnwe_trebovaniya_rt.html` |
| Установка DT на Linux | `ustanovka_dt_linux.html` |
| Установка PostgreSQL (для Linux) | `ust_postgresql_linux.html` |
| Обзор интерфейса редактора проекта | `interfejs_redaktora_proekta.html` |
| Редактор ST | `redaktor_st.html` |
| Редактор FBD | `redaktor_fbd.html` |
| Редактор HMI (окна) | `redactor_hmi.html` |

## 3. Прочее у вендора

- Продукт DT 2.0: <https://iek-digital.ru/products/masterscada-4d-dt-2-0/>
- Продукт MasterSCADA 4D (1.3.x): <https://iek-digital.ru/products/masterscada-4d/>
- Старая веб-справка 1.3.x: <https://help.iek-digital.ru/Help-web/> *(не путать с 2.0)*
- Дистрибутивы/демо/лицензии (регистрация): <https://iekid.iek.ru/>

## 4. Офлайн-зеркало

```bash
bash docs/fetch-docs.sh          # требует wget
# результат: docs/offline/support.mps-soft.ru/ms4d_web/index.html
```
