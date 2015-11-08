# Angelix #

Semantics-based automated program repair tool for C programs. Angelix fixes bugs manifested by failing test cases, and searches for the minimal change in order to preserve the oroginal source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

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

Angelix uses symbolic execution and program synthesis to search for a patch. It can delete program statements, add if guards and modify simple program expressions (see supported [defect classes](doc/DefectClasses.md) and [synthesis levels](doc/SynthesisLevels.md)).

To analyze program test executions, Angelix requires interface to test running and test assertions (see [test framework abstraction](doc/TestAbstraction.md)).

Angelix is designed to support Makefile-based projects. The following is required for successful execution of Angelix:

* Compiler used by the build system can be substituted by redefining `CC` environment variable.
* Compilation and linking are done by separate compiler calls.
* Project is configured to use static linking.
* All executables and object files are removed (e.g. run `make clean`).
* Angelix environment is set as shown above.

Angelix is hygienic (it does not modify original project files), however, it also assumes that the source code only uses relative references to the source tree. All intermediate data is stored in the `.angelix` directory.

Run `angelix --help` to see the list of available options.

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
