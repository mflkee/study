from decorators import id_print

def variable_id() -> None:
    a = 100
    b = a
    print(f"a: id({id(a)})")
    print(f"b: id({id(b)})")
    a += 1
    print(f"a: id({id(a)})")

# variable_id()
 
@id_print
def list_id() -> None:
    list_a = [100]
    list_a.append(101)
    print(list_a)


