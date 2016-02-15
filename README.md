# Angelix #

Semantics-based automated program repair tool for C programs. Angelix fixes bugs manifested by failing test cases and searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

## Installation ##

Angelix is distrubuted in source code and pre-installed in VirtualBox image. The VirtualBox image also contains ICSE'16 evaluation scripts and results.

### Option 1: VirtualBox image ###

You can [request](https://docs.google.com/forms/d/1XoQ3AomEwd2hke7-ty8CDaQ_iH7TH3W5foO5BQWc-6o/viewform?usp=send_form) VirtualBox image with preinstalled Angelix. Note that it probably does not include the latest version of Angelix.

### Option 1: From source code ###

The following is required to install Angelix from source code:

1. 30GB Hard drive
2. 8GM Memory
3. Ubuntu 14.04 64-bit*

* Angelix can be run on other 64-bit linux distributions, however installation scripts were tested only on Ubuntu 14.04. If you want to install Angelix on a different system, you may need to modify Makefile and activate scripts.

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
