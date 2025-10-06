def factorial(value:int):
    if value == 0 or value == 1:
        return 1
    else:
        return value * factorial(value -1)

print(factorial(5))


