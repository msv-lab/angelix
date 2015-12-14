# Angelix #

Semantics-based automated program repair tool for C programs. Angelix fixes bugs manifested by failing test cases and searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

## Installation ##

Clone repository recursively:

    git clone --recursive https://github.com/mechtaev/angelix.git

Install dependencies:

    sudo apt-get install git wget xz-utils build-essential
    sudo apt-get install curl dejagnu subversion bison flex bc libcap-dev
    sudo apt-get install cmake libncurses5-dev libboost-all-dev
    sudo apt-get install default-jdk sbt

Set Angelix environment:

    . activate

Download and build required modules:

    make all
    
Run tests:

    make test

Tested on Ubuntu 14.04 64-bit.

## Documentation ##

* [Tutorial](doc/Tutorial.md)
* [Manual](doc/Manual.md)
* [Troubleshooting](doc/Troubleshooting.md)

Run `angelix --help` to see the list of available options.

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers (in alphabetical order):

* Jooyong Yi
* Sergey Mechtaev
