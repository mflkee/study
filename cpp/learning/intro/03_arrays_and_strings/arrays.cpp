#include <cstddef>
#include <iostream>
#include <limits>

using namespace std;

const std::size_t arraySize = 5; // размер массива

int sumMinMax(const int* arr, std::size_t size) {
    if (size == 0) {
        return 0; // или выбросить исключение, если массив пустой
    }

    int minVal = numeric_limits<int>::max();
    int maxVal = numeric_limits<int>::min();

    for (std::size_t i = 0; i < size; ++i) {
        if (arr[i] < minVal) {
            minVal = arr[i];
        }
        if (arr[i] > maxVal) {
            maxVal = arr[i];
        }
    }

    return minVal + maxVal;
}

int main() {
    int arr[arraySize] = {1, 2, 3, 4, 5}; // инициализация массива

    int result = sumMinMax(arr, arraySize);
   cout << "Сумма минимального и максимального " << result << endl;

    return 0;
}
