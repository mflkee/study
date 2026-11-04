# Практика 1 — Telco Customer Churn

**Выполнил:** Макеев Г. Б.
**Группа:** BHEBDPS-24-MII-5
**Дисциплина:** Методы искусственного интеллекта

Решение практического задания.

## Содержимое

| Файл | Описание |
|------|----------|
| `Практика_1_Telco_Churn.ipynb` | Jupyter-ноутбук с полным решением (уже выполнен, графики внутри) |
| `data/Telco-Customer-Churn.csv` | Датасет Telco Customer Churn (7043 записи, 21 признак) |
| `Практика 1 [gUB9pf].pdf` | Исходное задание |

## Что сделано (по пунктам задания)

1. **Загрузка и типы** — исправлен `TotalCharges` (`object` → `float64`), пустые строки
   (клиенты с `tenure=0`) заполнены нулём.
2. **Дисбаланс классов** — `countplot` для `Churn` и круговая диаграмма долей (~26.5% ушедших).
3. **Кодирование** — Label Encoding для бинарных, One-Hot для многозначных признаков,
   `StandardScaler` для чисел (всё внутри `sklearn` `Pipeline`).
4. **Обучение** — `train_test_split` (80/20, `stratify`), `LogisticRegression`.
5. **Оценка** — `classification_report` и `Confusion Matrix`.
6. **ROC-AUC** — расчёт + ROC-кривая.

### Задания со звёздочкой
- :star: Ансамбли `RandomForest` и `CatBoost` + сравнение метрик (F1, ROC-AUC).
- :star: График важности признаков (`feature_importances_`) — главный фактор: **тип контракта
  (`Month-to-month`)** и **стаж (`tenure`)**.
- :star::star: Сборка дашборда в Yandex DataLens — опционально, выполняется вручную (no-code).

> Итог: на этом датасете baseline `LogisticRegression` (F1 ≈ 0.60, ROC-AUC ≈ 0.842) обошёл
> ансамбли с дефолтными настройками — см. разбор в ноутбуке.

## Как запустить

```bash
cd "Методы исскуственного ителекта"
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python pandas scikit-learn matplotlib seaborn jupyter catboost
.venv/bin/jupyter lab      # открыть Практика_1_Telco_Churn.ipynb
```

Повторный прогон ноутбука без открытия UI:

```bash
.venv/bin/jupyter nbconvert --to notebook --execute --inplace Практика_1_Telco_Churn.ipynb
```

## Источник данных

Telco Customer Churn (IBM Sample Data Sets):
<https://raw.githubusercontent.com/IBM/telco-customer-churn-on-icp4d/master/data/Telco-Customer-Churn.csv>

---

# Практика 3 — CNN и Transfer Learning (CIFAR-10, ResNet18)

Отдельный каталог: **[`Практика_3_CNN_Transfer_Learning/`](Практика_3_CNN_Transfer_Learning/)**
— задания разложены по папкам, у каждого свой код, графики и отчёт `results.md`:

| Папка | Задание |
|---|---|
| `01_data_augmentation/` | этап 1: CIFAR-10, EDA, аугментации, нормализация |
| `02_model_adaptation/` | этап 2: ResNet18 (ImageNet), заморозка, `fc` 512→10 |
| `03_train_eval/` | этап 3: обучение головы, accuracy, confusion matrix, ошибки модели |
| `04_fine_tuning_star/` | :star: fine-tuning последних слоёв vs обучение «с нуля» |
| `05_homework_neuron/` | ДЗ лекции 3: нейрон с Sigmoid на чистом Python (без PyTorch) |

Сводный отчёт под требования LMS (заголовок с номером работы и ФИО, шаги, скриншоты с
комментариями, выводы) — `Практика_3_CNN_Transfer_Learning/Отчёт_Практика_3.{ipynb,html,pdf}`,
собирается скриптом `build_report.py` из артефактов прогонов.

Вся общая логика — в `Практика_3_CNN_Transfer_Learning/common/`; внутри каждого
каталога есть свой `results.md` с цифрами и графиками, есть и общий
`Практика_3_CNN_Transfer_Learning/README.md`.


---

# Сдано в LMS (курс «Методы искусственного интеллекта I», id = 9599)

| Задание в LMS | Дедлайн | Файлы в репозитории | Статус |
|---|---|---|---|
| Отчет о работе — Практика 1 (EDA) | 25.01.2027 | `Практика_1_Telco_Churn.{ipynb,html}` | ✅ сдано 10.10.2026 |
| ДЗ с лекции (CatBoost вместо LogReg) | 09.10.2026 | `ДЗ_лекции_2_CatBoost.{ipynb,html}` | ✅ сдано 18.10.2026 (с опозданием) |
| Отчет о работе — Практика 2 (ансамбли, пайплайны) | 16.01.2027 | `Практика_2_Ансамбли_и_пайплайны.{ipynb,html}` | ✅ сдано 18.10.2026 |
| Файлик с текстом ответа на вопросы (лекция 3) | 26.10.2026 | `ДЗ_лекции_3_Ответы.{md,html,pdf}` | ✅ сдано 18.10.2026 |
| Отчёт о работе — Практика 3 (CNN, ResNet18) | 23.01.2027 | `Практика_3_CNN_Transfer_Learning/Отчёт_Практика_3.pdf` | ✅ сдано 18.10.2026 |

Требования LMS к отчётам: в заголовке — номер и название практики + ФИО; шаги выполнения;
скриншоты/графики с комментариями; вывод (по желанию).

## Инструменты репозитория (`tools/`)

| Файл | Назначение |
|---|---|
| `md2pdf.py` | Markdown → HTML → PDF (Chromium, корректная кириллица и таблицы) |
| `build_dz2_notebook.py` | собирает `ДЗ_лекции_2_CatBoost.ipynb` |
| `build_practice2_notebook.py` | собирает `Практика_2_Ансамбли_и_пайплайны.ipynb` |
| `cors_serve.py` | локальный CORS-сервер для загрузки файлов из браузера в LMS (127.0.0.1) |

Пересборка ноутбуков: `.venv/bin/python tools/build_*.py && .venv/bin/python -m nbconvert --execute --inplace <nb> && .venv/bin/python -m nbconvert --to html --embed-images <nb>`.
