#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

void inc(int* i) {
  (*i)++;
}

int main(int argc, char *argv[]) {
  int n;
  n = atoi(argv[1]);
  inc(&n); // if (n > 0)
  printf("%d\n", ANGELIX_OUTPUT(int, n, "n"));
  return 0;
}
