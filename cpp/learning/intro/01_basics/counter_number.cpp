#include <iostream>
#include <set>

int main() {
  unsigned long long n = 123456781;
  auto               m = n;
  int                k{0};
  for (; m > 0; m /= 10, k++);
  std::cout << "Всего чисел: " << k << std::endl;

  bool          same_num = false;
  std::set<int> digits;
  auto          x = n;
  while (x > 0) {
    int digit = x % 10;
    if (digits.find(digit) != digits.end()) {
      same_num = true;
      break;
    }
    digits.insert(digit);
    x /= 10;
  }
  if (same_num) {
    std::cout << "Есть одинаковые числа!" << std::endl;
  } else {
    std::cout << "Одинаковых чисел нет!" << std::endl;
  }
  return 0;
}
