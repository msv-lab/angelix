#include <stdio.h>
#include <ctype.h>
#include <string.h>

#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

int main(int argc, char *argv[]) {
  char* s = argv[1];
  int i;
  for (i = 0; i < strlen(s); i++) {
    if (s[i] > 'm') { // s[i] >= 'm'
      s[i] = toupper(s[i]);
    }
  }
  printf("%s\n", ANGELIX_OUTPUT(str, s, "stdout"));
  return 0;
}
