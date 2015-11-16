#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

int inc(int i) {
  return i + 1;
}

int main(int argc, char *argv[]) {
  int x, n;
  x = atoi(argv[1]);
  n = inc(x);
  printf("%d\n", ANGELIX_OUTPUT(int, n, "n"));
  return 0;
}
