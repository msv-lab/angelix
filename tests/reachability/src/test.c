#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, label) expr
#define ANGELIX_REACHABLE(label)
#endif

int main(int argc, char *argv[]) {
  int a, b;
  a = atoi(argv[1]);
  b = atoi(argv[2]);
  if (a > b) { // >=
    ANGELIX_REACHABLE("zero");
    printf("%d\n", 0);
  } else {
    printf("%d\n", 1);
  }
  return 0;
}
