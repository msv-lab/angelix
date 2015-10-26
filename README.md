# Angelix #

Semantics-based automated program repair tool for C programs. Angelix fixes bugs manifested by failing test cases by modifying side-effect free program expressions. It searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

## Installation ##

Clone repository recursively:

    git clone --recursive https://github.com/mechtaev/angelix.git

Install dependencies:

    sudo apt-get install g++ curl dejagnu subversion bison flex bc libcap-dev
    sudo apt-get install cmake libncurses5-dev libboost-all-dev
    sudo apt-get install default-jdk sbt

Set Angelix environment:

    . activate

Download and build required modules:

    make all
    
Run tests:

    make test

Tested on Ubuntu 14.04 64-bit.

## Usage ##

Prior to execution of Angelix, user needs to perform the following three activities:

* Instrument output expressions
* Modify test running configuration
* Specify expected output values

The following is required for successful execution of Angelix:

* Compiler used by the build system can be substituted by redefining `CC` environment variable.
* Compilation and linking are done by separate compiler calls.
* Project is configured to use static linking.
* All executables and object files are removed (e.g. run `make clean`).
* Angelix environment is set as shown above.

Angelix is hygienic (it does not modify original project files), however, it also assumes that the source code only uses relative references to the source tree. All intermediate data is stored in the `.angelix` directory.

Run `angelix -h` to see the list of available options.

## Instrumentation ##

Angelix requires specifying output values in the source code of the subject program. Consider a simple example:

    #include <stdio.h>

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        if (z < 0) {
            printf("Error\n");
        }
        printf("%d\n", z + 1);
        return 0;
    }

Output values are wrapped with `ANGELIX_OUTPUT` macro providing type and label of the expression. Reachibility can be captured using `ANGELIX_REACHABLE` macro and label. You also need to provide a default definition for this macro, so that the program remains compilable:

    #include <stdio.h>

    #ifndef ANGELIX_OUTPUT
    #define ANGELIX_OUTPUT(type, expr, label) expr
    #define ANGELIX_REACHABLE(label)
    #endif

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        if (z < 0) {
            ANGELIX_REACHABLE("error");
            printf("Error\n");
        }
        printf("%d\n", ANGELIX_OUTPUT(int, z + 1, "stdout"));
        return 0;
    }

The following types of output expressions are supported:

* [x] int
* [x] bool
* [x] char
* [ ] str (null-terminated string)

## Test model ##

To abstract over test framework, Angelix uses the following objects:

* Oracle executable
* Assert file

### Oracle ###

Oracle is an executable that takes a test identifier as the only argument, runs the corresponding test and terminates with `0` exit code if and only if the test passes.

Oracle executes buggy binary using _angelix run command_ stored in `ANGELIX_RUN` environment variable, if it is defined. Each test must include at most one execution of angelix run command. This is an example of oracle script:

    #!/bin/bash

    case "$1" in
        test1)
            "${ANGELIX_RUN:-eval}" ./test 1 2
            ;;
        test2)
            "${ANGELIX_RUN:-eval}" ./test 0 -1
            ;;
    ...

Oracle is executed from the root of a copy of the source code directory, therefore all references to the source tree must be relative to the root of the source tree.

### Assert file ###

Assert file is used to specify expected output values. Outputs are specified in JSON format:

    {
        "test1": {
            "stdout": [4]
            },
        "test2": {
            "stdout": [0],
            "reachable": ["error"]
        }
        ...
    }

Each output label corresponds to a list of values since an expression can be evaluated multiple times during test execution. An empty list means that the value must not be executed, while the absence of a label means that any value is allowed. `reachable` is a special label for capturing reachibility property and corresponding values include labels that must be executed at least once.

If expected outputs for a passing test case are not given, they are extracted automatically from the test executions. Expected outputs for failing test cases can be extracted from golden version (golden version must be instrumented accordingly).

## Known issues ##

* If you use multiarch, there can be a linking [problem](https://stackoverflow.com/questions/6329887/compiling-problems-cannot-find-crt1-o) when compiling with `llvm-gcc`

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
