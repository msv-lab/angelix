#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

struct Pair
{
  int first;
  int second;
};

int main(int argc, char *argv[]) {
  struct Pair p;
  struct Pair *pp = &p;
  pp->first = atoi(argv[1]);
  pp->second = atoi(argv[2]);
  if (pp->first > pp->second) { // >=
    printf("%d\n", ANGELIX_OUTPUT(int, 0, "stdout"));
  } else {
    printf("%d\n", ANGELIX_OUTPUT(int, 1, "stdout"));
  }
  return 0;
}
