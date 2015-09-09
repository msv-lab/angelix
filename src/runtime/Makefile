CC=gcc
CFLAGS=-I $(KLEE_INCLUDE_PATH)

DEP = runtime.h
SRC = runtime.c
OBJ_KLEE = $(SRC:%.c=%.klee.o)
OBJ_TEST = $(SRC:%.c=%.test.o)

all: libangelix.test.a libangelix.klee.a

%.test.o: %.c $(DEP)
	llvm-gcc -g -c -o $@ $< $(CFLAGS)

%.klee.o: %.c $(DEP)
	llvm-gcc -g -O0 --emit-llvm -c -o $@ $< $(CFLAGS)

libangelix.klee.a: $(OBJ_KLEE)
	llvm-ar rcs $@ $(OBJ_KLEE)

libangelix.test.a: $(OBJ_TEST)
	ar rcs $@ $(OBJ_TEST)

clean:
	rm -f *.o
	rm -f *.a
