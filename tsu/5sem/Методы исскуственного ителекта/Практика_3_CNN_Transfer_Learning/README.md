# Практика 3. Компьютерное зрение и Transfer Learning (CNN, ResNet18, CIFAR-10)

**Выполнил:** Макеев Г. Б. · **Группа:** BHEBDPS-24-MII-5 · **Дисциплина:** Методы искусственного интеллекта
**Инструменты:** Python 3.12, PyTorch (CPU), torchvision, matplotlib
**Материалы:** `Practice_3_CNN_Transfer_Learning.pptx`, лекция 3 (`Lecture_3_Deep_Learning.pptx`)

Задания разложены по папкам — каждый этап практики отдельно, со своим кодом,
графиками и отчётом (`results.md`):

| Папка | Что там | Запуск (из этой папки) |
|---|---|---|
| [01_data_augmentation](01_data_augmentation/) | Этап 1: датасет CIFAR-10, EDA, аугментации, нормализация | `../.venv/bin/python 01_data_augmentation/main.py` |
| [02_model_adaptation](02_model_adaptation/) | Этап 2: ResNet18 с весами ImageNet, заморозка, `fc` 512→10 | `../.venv/bin/python 02_model_adaptation/main.py` |
| [03_train_eval](03_train_eval/) | Этап 3: обучение, accuracy, confusion matrix, разбор ошибок | `../.venv/bin/python 03_train_eval/main.py` |
| [04_fine_tuning_star](04_fine_tuning_star/) | :star: Fine-tuning последних слоёв vs обучение с нуля | `../.venv/bin/python 04_fine_tuning_star/main.py` |
| [05_homework_neuron](05_homework_neuron/) | ДЗ лекции 3: нейрон с Sigmoid на чистом Python (без PyTorch) + ответы на вопросы лекции | `../.venv/bin/python 05_homework_neuron/main.py` |
| [common](common/) | Общий код: данные, модель, цикл обучения, графики | — |
| `data/` | CIFAR-10 (скачивается/распаковывается автоматически, ~180 МБ, не в git) | — |

## Готовый отчёт (сводный, под требования LMS)

| Файл | Что это |
|---|---|
| `Отчёт_Практика_3.ipynb` | сводный Jupyter-ноутбук: заголовок с номером работы и ФИО, шаги по всем этапам, «скриншоты» с комментариями, выводы (уже выполнен, графики внутри) |
| `Отчёт_Практика_3.html` | тот же отчёт в HTML (самодостаточный файл, удобно открыть/приложить в LMS) |
| `Отчёт_Практика_3.pdf` | то же в PDF (15 страниц) |
| `build_report.py` | собирает отчёт из артефактов прогонов (`outputs/metrics.json` + картинки) |

Требования LMS (курс «Методы ИИ I», раздел «Основное»): «в заголовке — номер практического занятия
и его название; в заголовке — ФИО сдающего; в тексте раскрыты шаги по выполнению работы, крайне
приветствуются скриншоты и комментарии к ним; вывод — по желанию». Пересборка отчёта:

```bash
../.venv/bin/python build_report.py
../.venv/bin/jupyter nbconvert --to notebook --execute --inplace "Отчёт_Практика_3.ipynb"
../.venv/bin/jupyter nbconvert --to html --output "Отчёт_Практика_3.html" "Отчёт_Практика_3.ipynb"
chromium --headless=new --no-sandbox --no-pdf-header-footer --print-to-pdf="Отчёт_Практика_3.pdf" \
  "file://$PWD/Отчёт_Практика_3.html"
```

## Быстрый старт (всё подряд)

```bash
cd "Методы исскуственного ителекта/Практика_3_CNN_Transfer_Learning"
../.venv/bin/python 01_data_augmentation/main.py
../.venv/bin/python 02_model_adaptation/main.py
../.venv/bin/python 03_train_eval/main.py --compare-bn   # ~5,5 мин (в т.ч. сравнение BatchNorm)
../.venv/bin/python 04_fine_tuning_star/main.py          # ~11 мин (fine-tuning + «с нуля»)
../.venv/bin/python 05_homework_neuron/main.py           # PyTorch не нужен, секунды
```

Сокращённые прогоны: `03_train_eval/main.py --epochs 3` (~30 с), `04_... --skip-scratch --epochs 3`.

## Итоги (измерено в этом окружении, CPU)

| Этап | Результат |
|---|---|
| 3. Transfer Learning: замороженный ResNet18, обучается только голова (5 130 параметров = 0.046% модели), 15 эпох | **accuracy = 0.6059**, macro-F1 = 0.6049, 2 мин 29 с |
| 3. То же, но BatchNorm бэкенда в режиме `train` (контрольный эксперимент) | accuracy = 0.4010 — **в 1.5 раза хуже** |
| 4. :star: Fine-tuning `layer4` + `fc` (8.4 млн параметров, lr 1e-4), 5 эпох | **accuracy = 0.6499** (+0.0440 к этапу 3) |
| 4. Контроль: обучение ResNet18 **с нуля** (случайные веса), 5 эпох | accuracy = 0.4257 — передача обучения выигрывает +0.18 |
| 5. ДЗ: один нейрон с Sigmoid, чистый Python | градиенты сходятся с численной производной (8e-12) и с `torch.autograd` (2.8e-17); XOR одним нейроном не решается — предел 0.75 |

Полные таблицы, кривые и confusion matrix — в `results.md` каждой папки.

> Воспроизводимость: везде фиксирован один сид (`SEED = 42` в `common/utils.py`),
> подвыборка CIFAR-10 сохраняется в `01_data_augmentation/outputs/subset_indices.json`
> и переиспользуется всеми этапами — сравнение моделей идёт ровно на одних данных.

## Инженерное замечание (важное для этого задания)

Стандартный `torchvision.resnet18` рассчитан на изображения 224×224: первый слой —
свёртка 7×7 со `stride=2` плюс `MaxPool`. На картинках CIFAR-10 (32×32) это значит,
что после `layer4` остаётся карта признаков **1×1** — сеть теряет пространственную
информацию, и линейная голова получает «слишком сжатую» картину. Измеренные варианты
(подробности и цифры — в `03_train_eval/results.md`) показывают, как это лечится:
убрать `MaxPool` или заменить первый слой на 3×3/`stride=1`.

## Средовое

* GPU в окружении нет (Intel Iris Xe) → всё обучение на CPU (10 потоков).
* Из-за этого выбрана подвыборка train (10 000 картинок: по 1000 на класс) при
  полном тесте (10 000 картинок) и 3–5 эпох — этапы считаются минутами, а не часами.
* Датасет CIFAR-10 (170 МБ) берётся из исходного `cifar-10-python.tar.gz`
  (md5 которых сверяется torchvision): в этом окружении официальный хост отдаёт
  ~30 КБ/с, поэтому архив один раз скачан с зеркала Kaggle и закеширован в `data/`.
