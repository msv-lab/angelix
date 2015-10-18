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
* Extract required information from the testing framework
* Specify expected output values for failing test cases (if golden version is not available)

The following is required for successful execution of Angelix:

* Compiler used by the build system can be substituted by redefining `CC` environment variable.
* Compilation and linking are done by separate compiler calls.
* Project is configured to use static linking.
* All executables and object files are removed (e.g. run `make clean`).
* Angelix environment, `C_INCLUDE_PATH` and `CPLUS_INCLUDE_PATH` are set as shown above.

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
        printf("%d\n", z + 1);
        return 0;
    }

Output values are wrapped with `ANGELIX_OUTPUT` macro providing type and id of the expression. You also need to provide a default definition for this macro, so that the program remains compilable:

    #include <stdio.h>

    #ifndef ANGELIX_OUTPUT
    #define ANGELIX_OUTPUT(type, expr, id) expr
    #endif

    int main(int argc, char** argv) {
        int x, y, z;
        x = atoi(argv[1]);
        y = atoi(argv[2]);
        z = x + y;
        printf("%d\n", ANGELIX_OUTPUT(int, z + 1, "stdout"));
        return 0;
    }

The following types of output expressions are supported:

* int
* bool
* char
* str (null-terminated string)

## Test model ##

To abstract over test framework, Angelix uses the following three objects:

* Oracle executable
* JSON test database
* Assert file

### Oracle ###

Oracle is an executable that takes a test identifier as the only argument, runs the corresponding test and terminates with `0` exit code if and only if the test passes. Oracle is executed from the root of a copy of the source code directory, therefore all references to the source tree must be relative to the root of the source tree.

### JSON test database ###

JSON test database specifies test executables, their arguments and how to build them (if needed):

    {
        "test1": {
            "executable": "tests/test1.exe",
            "arguments": ["-a", "1", "-b", "2"],
            "build": {                         # optional
                "command": "make -e test1",
                "directory": "tests"
            }
        },
        ...
    }

### Assert file ###

Assert file is used to specify expected output values. Outputs are specified in JSON format:

    {
        "test1": {
            "stdout": [1, 2 ,3]
        },
        ...
    }

Each output id corresponds to a list of values, since an expression can be evaluated multiple times during the test execution.

If expected outputs for a passing test case are not given, they are extracted automatically from the test executions. Expected outputs for failing test cases can be obtained from a golden version (it must be instrumented accordingly).

## Known issues ##

* If you use multiarch, there can be a linking [problem](https://stackoverflow.com/questions/6329887/compiling-problems-cannot-find-crt1-o) when compiling with `llvm-gcc`

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
