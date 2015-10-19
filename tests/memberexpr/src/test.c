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
    return ANGELIX_OUTPUT(int, 0, "exitcode");
  } else {
    return ANGELIX_OUTPUT(int, 1, "exitcode");
  }  
}
