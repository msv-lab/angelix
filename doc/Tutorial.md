# Tutorial #

This tutorial demonstrates how Angelix can be used to repair a simple program. Suppose we have the following buggy program (distance.c):

    #include <stdio.h>

    int distance(int x1, int y1, int x2, int y2) {
        int dx, dy;
        if (x1 > x2)
            dx = x1 - x2;
        else
            dx = x2 - x1;
        if (y1 > y2)
            dy = y1 - y2;
        else
            dy = y1 - y2;
        if (dx > dy)
            return dx;
        else
            return dy;
    }

    int main(int argc, char *argv[]) {
        int a, b;
        x1 = atoi(argv[1]);
        y1 = atoi(argv[2]);
        x2 = atoi(argv[3]);
        y2 = atoi(argv[4]);
        printf("%d\n", distance(x1, y1, x2, y2));
        return 0;
    }

Notice that `dy` is assigned to `y1 - y2` in both branches of the if statement, while it is supposed to be `y2 - y1` in the second branch. This is a typical mistake caused by copy-pasting.

This program produces the computes the following values:

    $ ./distance 2 3 1 1
    2
    $ ./distance 3 2 5 1
    2
    $ ./distance 3 1 2 5
    1

The last value is wrong. The expected distance is 4, but because of the bug in `dy` computation, the program outputs dx. Let's apply Angelix to fix the bug automatically using the above tests.

In order to Angelix can analyse the program, you need to provide an interface to the build system and testing framework used by you program. In this example, we will use a simple Makefile. For more detailed information, please read the [manual](Manual.md).

    CC=gcc
    CFLAGS=-I.
    
    all:
	    $(CC) $(CFLAGS) -c distance.c -o distance.o
	    $(CC) distance.o -o distance

To abstract over the testing framework, Angelix uses three concepts: output expressions that must be specified in the source code of the buggy program, runner script that executes a test by its ID ("oracle"), expected output values. This is similar to the organization of JUnit tests and can be injected into existing test framework without significant modifications.

Output values are specified by wrapping them with `ANGELIX_OUTPUT` macro. In out example, the only output value is the computed distance, therefore the instrumentation will look the following:

    #include <stdio.h>

    #ifndef ANGELIX_OUTPUT
    #define ANGELIX_OUTPUT(type, expr, id) expr
    #endif

    int distance(int x1, int y1, int x2, int y2) {
        int dx, dy;
        if (x1 > x2)
            dx = x1 - x2;
        else
            dx = x2 - x1;
        if (y1 > y2)
            dy = y1 - y2;
        else
            dy = y1 - y2;
        if (dx > dy)
            return dx;
        else
            return dy;
    }

    int main(int argc, char *argv[]) {
        int a, b;
        x1 = atoi(argv[1]);
        y1 = atoi(argv[2]);
        x2 = atoi(argv[3]);
        y2 = atoi(argv[4]);
        printf("%d\n", ANGELIX_OUTPUT(int, distance(x1, y1, x2, y2), "stdout"));
        return 0;
    }

Note that we need to provide a default definition of the `ANGELIC_OUTPUT` macro, so that the program remains compilable.

Oracle script must execute the test using `ANGELIX_RUN` command if it is defined. It can be organized, for example, in the following way:

    #!/bin/bash

    assert-equal () {
        diff -q <("${ANGELIX_RUN:-eval}" $1) <(echo -ne "$2") > /dev/null
    }

    case "$1" in
        1)
            assert-equal "./test 2 3 1 1" '2\n'
            ;;
        2)
            assert-equal "./test 3 2 5 1" '2\n'
            ;;
        3)
            assert-equal "./test 3 1 2 5" '4\n'
            ;;
    esac

Apart from that, it is required to provide expected output values for failing test, because in general they cannot be extracted automatically from the test scripts. Expected output are specified using JSON format. In the example, we only need to specify the expected output for the test 3 (assert.json):

    {
        "3": {
            "stdout": [4]
        }
    }

Now we can run Angelix to generate a patch for that make all test pass:

    angelix /path/to/src distance.c oracle 1 2 3 --assert assert.json

Angelix will generate the following patch:

    --++

