#include <stdio.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

struct t {
  int value;
};

typedef struct t * TStruct;

int main(int argc, char *argv[]) {
  TStruct a = (TStruct) malloc(sizeof (struct t));
  TStruct b = (TStruct) malloc(sizeof (struct t));
  a->value = atoi(argv[1]);
  b->value = atoi(argv[2]);
  if (a->value > b->value) { // >=
    printf("%d\n", ANGELIX_OUTPUT(int, 0, "stdout"));
  } else {
    printf("%d\n", ANGELIX_OUTPUT(int, 1, "stdout"));
  }
  return 0;
}
