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

Angelix supports Makefile-based projects and it assumes that (1) compiler is defined by the `CC` variable, (2) compilation and linking are done by separate compiler calls. Note that you need to configure your project to use static linking so that it can be executed by KLEE. Angelix copies all data to the `.angelix` directory and does not modify the original files. Run `angelix -h` to see the list of available options.

There are three activities that are performed manually by the user:

1. Instrumenting output expressions
2. Extracting required information from the testing framework
3. Specifying expected output values for failing test cases (if golden version is not available)

## Instrumentation ##

Angelix requires specifying output values in the source code of the subject program. Consider a simple example:

    #include <stdio.h>

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        printf("%d\n", z);
        return 0;
    }

Output values are wrapped with `ANGELIX` macro specifying type and id of the expression. You also need to provide a default definition for this macro, so that the program remains compilable:

    #include <stdio.h>

    #ifndef ANGELIX
    #define ANGELIX(type, id, expr) expr
    #endif

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        printf("%d\n", ANGELIX(int, "stdout", z));
        return 0;
    }

The following types of output expressions are supported:

* int
* bool
* char
* str (null-terminated string)

## Test model ##

To abstract over test framework, we use the following three objects:

* Oracle executable
* JSON test database
* Correct outputs for failing test cases

### Oracle ###

Oracle is an executable that takes a test identifier as the only argument, runs the corresponding test and terminates with `0` exit code if and only if the test passes. Oracle is executed from the root of a copy of the source code directory, therefore all references to the source tree must be relative to the root of the source tree.

### JSON test database ###

JSON test database specifies test executables, their arguments and how to build them (if needed):

    {
        "test1": {
            "executable": "tests/test1.exe",
            "arguments": ["-a", "1", "-b", "2"],
            "make": {                         # optional
                "arguments": "test1",
                "directory": "tests"
            }
        },
        ...
    }

### Correct outputs ###

Angelix can extract correct outputs from a golden version (it must be instrumented accordingly). If golden version is not available, correct outputs are specified in JSON format:

    {
        "test1": {
            "stdout": [1, 2 ,3]
        },
        ...
    }

Each output id corresponds to a list of values, since an expression can be evaluated multiple times during the test execution.

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
