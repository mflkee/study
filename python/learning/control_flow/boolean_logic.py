lim = 100
requests_1 = [i for i in range(1, 101)]
requests_2 = [i for i in range(1, 101)]

if len(requests_1) >= lim and len(requests_2) >= lim:
    print("Колличетсво запросов привышенно лимита!")
else:
    print("Нормальное колличество запросов")

