#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(expr, type, id) expr
#endif

int main(int argc, char *argv[]) {
  int a, b;
  a = atoi(argv[1]);
  b = atoi(argv[2]);
  if (a > b) { // a >= b
    return ANGELIX_OUTPUT(0, int, "exitcode");
  } else {
    return ANGELIX_OUTPUT(1, int, "exitcode");
  }  
}
