list_1 = [1 * i for i in range(0,10)]
list_2 = [0]
arg_1 = (any(list_1))
arg_2 = (any(list_2))

comp_arg = [[{"arg_1": arg_1}], [{"arg_2": arg_2}]]
print(comp_arg[0])


