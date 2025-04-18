import inspect
# Список проверок, которые мы хотим выполнить
checks = [
    "ismodule",
    "isfunction",
    "ismethod",
    "isclass",
    "isbuiltin",
]

for i, check in enumerate(checks, start=1):
    if hasattr(inspect, check):  # Проверяем, существует ли метод в inspect
        print(True)
    else:
        print (False, "Нет такого:", check)

