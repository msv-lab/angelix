# Test Abstraction #

Real-world C programs use various test frameworks. Often testing scenarious are expressed using a combination of small C programs and Bash/Perl scripts. Angelix requires extracting expected outputs from tests to perform semantics analysis. To do so, it provides a test framework abstraction that can be relatively easy injected into exising test harness.

To abstract over test framework, Angelix require the following:

* Instrument output expressions in the source code of the buggy program
* Adjust test running configuration
* Specify expected output values for the instrumented expressions

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

Output values are wrapped with `ANGELIX_OUTPUT` macro providing type and label of the expression. Reachibility can be captured using `ANGELIX_REACHABLE` macro and label. You also need to provide a default definition for these macros, so that the program remains compilable:

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

## Running ##

To abstract over the test runner, we use the notion of oracle executable. It takes a test identifier as the only argument, runs the corresponding test and terminates with `0` exit code if and only if the test passes.

Oracle executes buggy binary using _angelix run command_ stored in `ANGELIX_RUN` environment variable, if it is defined. Each test must include at most one execution of angelix run command. This is an example of oracle script:

    #!/bin/bash

    case "$1" in
        test1)
            ${ANGELIX_RUN:-eval} ./test 1 2
            ;;
        test2)
            ${ANGELIX_RUN:-eval} ./test 0 -1
            ;;
    ...

Oracle is executed from the root of a copy of the source code directory, therefore all references to the source tree must be relative to the root of the source tree.

## Expected output ##

To specify expected outputs values for instrumented expression, we use an assert file. Outputs are specified in JSON format:

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

Each output label corresponds to a list of values since an expression can be evaluated multiple times during test execution. `reachable` is a special label for capturing reachibility property and corresponding values include labels that are executed at least once. An empty list for a label means that the corresponding expression (or location) must not be executed, while the absence of a label in the test specification means that any value is allowed.

### Output extraction ###

It is often difficult to define correct values for an arbitrary program expression. For this reason, Angelix can extract such values automatically from program runs for passing test cases, and from golden version's runs if it is available. When using golden version, it must be instrumented accordingly.

Note that when values are extracted from program runs, the value of the label `reachable` is empty list by default, as opposite to other labels that are not present in the specification if not executed.
