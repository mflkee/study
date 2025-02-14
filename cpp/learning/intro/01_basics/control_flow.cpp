#include <cmath>
#include <iostream>
// #include <string>
using namespace std;

void ControlFlow() {
  float x = 3.0;
  for (int i = 0; i < 10; ++i) {
    cout << "x = " << x << endl;
    cout << "сеил: " << ceil(x) << endl;
    cout << "флоа: " << floor(x) << endl;
    cout << "раунд: " << round(x) << endl;
    cout << "транк: " << trunc(x) << endl;
    x += 0.1;
    cout << "_________________" << endl;
  }
}

int main() {
  ControlFlow();
}
