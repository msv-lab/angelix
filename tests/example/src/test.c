#include <stdio.h>

#ifndef ANGELIX
#define ANGELIX(type, id, expr) expr
#endif

int main() {
  int i;
  for (i=ANGELIX(int, "i", 0); i<10; i++) {
    printf("%d\n", i);
  }
  return 0;
}
