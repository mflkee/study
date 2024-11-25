import time
from concurrent.futures import ThreadPoolExecutor, ProcessPoolExecutor


# Формулы
def formula1(x):
    return x**2 - x**2 + x**4 - x**5 + x + x


def formula2(x):
    return x + x


def formula3(res1, res2):
    return res1 + res2


# Функция для измерения времени выполнения
def benchmark_task(iterations, use_threads=True):
    data = list(range(iterations))

    # Шаг 1: Вычисление formula1
    start = time.time()
    if use_threads:
        with ThreadPoolExecutor() as executor:
            res1 = list(executor.map(formula1, data))
    else:
        with ProcessPoolExecutor() as executor:
            res1 = list(executor.map(formula1, data))
    step1_time = time.time() - start

    # Шаг 2: Вычисление formula2
    start = time.time()
    if use_threads:
        with ThreadPoolExecutor() as executor:
            res2 = list(executor.map(formula2, data))
    else:
        with ProcessPoolExecutor() as executor:
            res2 = list(executor.map(formula2, data))
    step2_time = time.time() - start

    # Шаг 3: Вычисление formula3
    start = time.time()
    res3 = [formula3(r1, r2) for r1, r2 in zip(res1, res2)]
    step3_time = time.time() - start

    return step1_time, step2_time, step3_time


# Запуск задач
print("Задание 1 (потоки):")
print("10,000 итераций:", benchmark_task(10_000, use_threads=True))
print("100,000 итераций:", benchmark_task(100_000, use_threads=True))

print("\nЗадание 2 (процессы):")
print("10,000 итераций:", benchmark_task(10_000, use_threads=False))
print("100,000 итераций:", benchmark_task(100_000, use_threads=False))
