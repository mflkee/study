from random import randint, choice
print("-----------------------------------------------------------------")
print("КОНСОЛЬНАЯ ИГРА УГАДАЙ ЧИСЛО В РАНДОМНО СГЕНЕРИРОВАННОМ СПИСКЕ!!!")
print("-----------------------------------------------------------------")


def gen_list():
    try:
        list_from = int(input("СПИСОК ИЗ ЧИСЕЛ ОТ: "))
        list_to = int(input("ДО: "))
        list_size = int(input("КОЛЛИЧЕСТВО ЭЛЕМЕНТОВ В СПИСКЕ: "))
        rand_list = []

        i = 0
        while i < list_size: 
            rand_list.append(randint(list_from,list_to))
            i += 1
        rand_list.sort()

        return rand_list

    except ValueError:
        print("ВВОДИТЕ ТОЛЬКО ЧИСЛА! ЗАНОГО!")
        return None

def game(rand_list):
    print(rand_list)
    rand_num = choice(rand_list)
    print(rand_num)
    target_num = int(input("ВВЕДИТЕ ЗАГАДАННОЕ ЧИСЛО: "))
    print(len(rand_list))


    i = 0
    while len(rand_list) > i:
        for j in rand_list:
            if target_num == j:
                print(f"ВЫ НАШЛИ ЧИСЛО! {target_num} В СПИСКЕ. PS НИФИГА ВЫ МОЛОДЕЦ!")
            else:
                i =+ 1
        return None


    return None


if __name__ == "__main__":
    rand_list = gen_list()
    if rand_list is not None:
        game(rand_list)


