import inspect
def value_operation():
    pi = 13.4
    d = 15
    r = d / 2
    S = pi * (r**2)
    print(S)
value_operation()

result_1 = inspect.ismodule(value_operation)
print(result_1)

result_2 = inspect.isfunction(value_operation)
print(result_2)





