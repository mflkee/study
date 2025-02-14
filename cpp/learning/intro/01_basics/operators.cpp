#include <iostream>

int main() {
  const auto a = true, b = false;

  std::cout << (!a) << (a || b) << (a && b) << std::endl;
  std::cout << ((!b) || (!a)) << std::endl;
}
