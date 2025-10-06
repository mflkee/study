num = 121
def main(num) -> bool:
    arr = []
    original = num
    while num > 0:
        dig = num % 10
        arr.append(dig)
        num = num // 10
        print(arr)
    
    lenght = len(arr)
    arr2 = []
    for i in arr:
        res = i * 10 ** (lenght-1)
        arr2.append(res)
        lenght -= 1
        print(arr2)
    result = sum(arr2)
    print(result)
    print(num)
    return original == result

print(main(num))
