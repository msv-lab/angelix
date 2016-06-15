#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

int main(int argc, char *argv[]) {
  int a, b;
  a = atoi(argv[1]);
  b = atoi(argv[2]);
  long result;
  if (a > b) { // >=
    result = 2147483647L;
  } else {
    result = 0L;
  }
  printf("%ld\n", ANGELIX_OUTPUT(long, result, "stdout"));
  return 0;
}
