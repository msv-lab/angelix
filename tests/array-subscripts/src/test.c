#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

int main(int argc, char *argv[]) {
  int a[1];
  int b[1];
  a[0] = atoi(argv[1]);
  b[0] = atoi(argv[2]);
  if (a[0] > b[0]) { // >=
    printf("%d\n", ANGELIX_OUTPUT(int, 0, "stdout"));
  } else {
    printf("%d\n", ANGELIX_OUTPUT(int, 1, "stdout"));
  }
  return 0;
}
