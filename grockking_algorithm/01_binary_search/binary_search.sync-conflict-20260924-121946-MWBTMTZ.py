# Определение класса BinarySearch
class BinarySearch():

    # Определение метода search_iterative с тремя аргументами (self, list, item)
    def search_iterative(self, list, item):
        # low и high указывают, какая часть списка будет искаться
        low = 0
        high = len(list) - 1

        # Пока область поиска не сократится до одного элемента...
        while low <= high:
            # ... проверяем средний элемент
            mid = (low + high) // 2
            guess = list[mid]
            # Если найден искомый элемент, возвращаем его индекс
            if guess == item:
                return mid
            # Если предположение было слишком высоким
            if guess > item:
                high = mid - 1
            # Если предположение было слишком низким
            else:
                low = mid + 1

        # Если элемент не найден, возвращаем None
        return None

    # Определение метода search_recursive с четырьмя аргументами (self, list, low, high, item)
    def search_recursive(self, list, low, high, item):
        # Проверяем базовый случай
        if high >= low:

            # Находим индекс среднего элемента
            mid = (high + low) // 2
            guess = list[mid]

            # Если элемент находится в середине списка
            if guess == item:
                return mid

                # Если элемент меньше среднего элемента, то ищем в левой части списка
            elif guess > item:
                return self.search_recursive(list, low, mid - 1, item)

                # Если элемент больше среднего элемента, то ищем в правой части списка
            else:
                return self.search_recursive(list, mid + 1, high, item)

        else:
            # Если элемент не найден, возвращаем None
            return None


# Если этот файл является файлом верхнего уровня (а не модулем), то выполнить следующий код
if __name__ == "__main__":
    # Создаем экземпляр класса BinarySearch
    bs = BinarySearch()
    # Создаем отсортированный список
    my_list = [1, 3, 5, 7, 9]

    # Ищем элемент 3 в отсортированном списке с помощью метода search_iterative
    print(bs.search_iterative(my_list, 3))  # => 1

    # Ищем элемент -1 в отсортированном списке с помощью метода search_iterative
    # Возвращаемое значение будет None, так как элемент не найден
    print(bs.search_iterative(my_list, -1))  # => None
