# Angelix #

Semantics-based automated program repair tool for C programs. Angelix fixes bugs manifested by failing test cases by modifying side-effect free program expressions. It searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

## Prerequisites ##

Install dependencies:

    sudo apt-get install g++ curl dejagnu subversion bison flex bc libcap-dev
    sudo apt-get install cmake libncurses5-dev libboost-all-dev
    sudo apt-get install default-jdk sbt

Set environment variables:

    # for Ubuntu 64-bit (put to .bashrc):
    export C_INCLUDE_PATH=/usr/include/x86_64-linux-gnu
    export CPLUS_INCLUDE_PATH=/usr/include/x86_64-linux-gnu

    # Angelix environment:
    . activate

Download and build required modules:

    make all
    
Run tests:

    make test

Tested on Ubuntu 14.04 64-bit.

## Usage ##

Angelix supports Makefile-based projects, and it assumes that (1) compiler is defined by the `CC` variable, (2) compilation and linking are done by separate compiler calls. Note that you need to configure your project to use static linking so that it can be executed by KLEE. Angelix copies all data to the `.angelix` directory and do not modify the original files. Run `angelix -h` to see the list of available options.

There are four activities that are performed manually by the user:

1. Instrumenting output expressions
2. Creating oracle script
3. Extracting required information from the testing framework
4. Correcting output values for failing test cases (if correct source code is not available)

### Instrumentation ###

Angelix requires specifying output values and suspicious location in the source code of the subject program. Consider a simple example:

    #include <stdio.h>

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        printf("%d\n", z + 1);
        return 0;
    }

Output values are specified by wrapping them with `ANGELIX` macro:

    #include <stdio.h>

    #ifndef ANGELIX
    #define ANGELIX(type, id, expr) expr
    #endif

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        printf("%d\n", ANGELIX(int, "stdout", z + 1));
        return 0;
    }

The following types are supported for output expressions:

    int
    bool
    char
    str (null-terminated string)

### Test model ###

To abstract over test framework, we use the following format (tests JSON database):

    [
        {
            "id": "test1",
            "executable": "tests/test1.exe",
            "arguments": "-a 1 -b 2",
            "make": {                         # optional
                "arguments": "test1",
                "directory": "tests"
            }
        },
        ...
    ]

_oracle_ - an executable that takes test identifier as the only argument and terminates with `0` exit code if and only if the corresponding test passes.

Angelix dumps output values for each test case. Extracted information is stored in dump files:

    dump/test1/x/2

where `x` is output id, `2` is its execution instance.

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
