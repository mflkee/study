#include <iostream>
using namespace std;

int main() {
  int z = 42;
  int *ptr_z = &z;

  cout << z << endl;
  cout << *ptr_z << endl;

  int * ptr_null_1 = 0;
  int * ptr_null_2 = nullptr;

  cout << ptr_null_1 << endl;
  cout << ptr_null_2 << endl;

}
