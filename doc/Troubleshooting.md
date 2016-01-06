# Troubleshooting #

Angelix prints log messages on the error output. The are three verbosity levels: default, quiet and verbose. The later can be enabled by `--quiet` and `--verbose` accordingly. There are also three message types: INFO, WARNING and ERROR. INFO is used to trace events in normal execution, ERROR for situations that cause tool failure and WARNING for events that potentially can cause failure. In case of error, `--verbose` level can be used to identify the problem or to create a bug report.

Below are instruction on how to identify and solve common problem based on output messages.

## ERROR ##

### golden version or assert file needed for test ... ###

The failing test cannot be repaired, because golden version or assert file with expected outputs are not provided for this test.

### transformation of ... failed ###

Source code transformation failed. Can happen when the frontend is not built successfully. Try `make frontend`.

## WARNING ##

### configuration of {} returned non-zero code ###

Angelix builds three targets: validation target using normal compilation, frontend target linked with Angelix runtime library, and backend target linked with Angelix runtime library and compiled into LLVM bitcode. This warning is often preceded by the message "failed to build ..." that can help to identify the problem.

#### cannot find crt1.o: No such file or directory ####

If Angelix fails with the following compilation message on Ubuntu 14.04

    ...
    /usr/bin/ld: cannot find crt1.o: No such file or directory
    /usr/bin/ld: cannot find crti.o: No such file or directory
    collect2: ld returned 1 exit status
    ...

follow instructions [here](https://stackoverflow.com/questions/6329887/compiling-problems-cannot-find-crt1-o).

## no suspicious expressions localized ##

Suspicious expressions cannot be localized when the program does not have expressions within the selected defect class that are executed by the given failing tests. You can either change the defect class or add other failing tests. 

## synthesis returned non-zero code ##

Can happen when the synthesizer is not built successfully. Try `make synthesis`.

## ANGELIX_RUN is not executed by test ... ##

The test script is not instrumented correctly. Add ANGELIX_RUN according to the manual.

## ANGELIX_RUN is executed multiple times by test ... ##

The test script is no instrumented correctly or the binary is executed multiple times which is not supported by Angelix.

## generated invalid fix (tests ... not repaired) ##

Inferred angelic forest is wrong. Can be caused by incorrect output instrumentation, incorrect expected output or imprecision of symbolic execution.

## Other issues ##
