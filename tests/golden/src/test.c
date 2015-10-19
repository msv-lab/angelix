#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#endif

int main(int argc, char *argv[]) {
  int a, b;
  a = atoi(argv[1]);
  b = atoi(argv[2]);
  if (a > b) { // a >= b
    return ANGELIX_OUTPUT(int, 0, "exitcode");
  } else {
    return ANGELIX_OUTPUT(int, 1, "exitcode");
  }  
}
