# Angelix #

Semantics-based [automated program repair](http://automated-program-repair.org) tool for C programs. Angelix fixes bugs manifested by failing test cases and searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

## Installation ##

Angelix is distributed in source code form and pre-installed in VirtualBox image. The VirtualBox image also contains Angelix ICSE'16 and SemFix ICSE'13 evaluation results.

### Option 1: VirtualBox image ###

You can [request](https://docs.google.com/forms/d/1XoQ3AomEwd2hke7-ty8CDaQ_iH7TH3W5foO5BQWc-6o/viewform?usp=send_form) VirtualBox image with pre-installed Angelix. Note that it may not include the latest version of Angelix.

### Option 2: Build from source ###

The following is required to install Angelix from source code:

* 30GB Hard drive
* 8GM Memory
* Ubuntu 14.04 64-bit\*

\* Angelix can be run on other 64-bit Linux distributions, however installation scripts were tested only on Ubuntu 14.04. If you want to install Angelix on a different system, you may need to modify Makefile and activate scripts.

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

To set optimal configuration for your subject, refer to the Configuration section of the [manual](doc/Manual.md).

## SemFix ##

SemFix is a predecessor of Angelix. Taking advantage of the modular design of Angelix, we incorporate the algorithm of SemFix into Angelix. SemFix can be activated using the `--semfix` option of Angelix.

## Publications ##

**Angelix: Scalable Multiline Program Patch Synthesis via Symbolic Analysis.** S. Mechtaev, J. Yi, A. Roychoudhury. ICSE'16.

**DirectFix: Looking for Simple Program Repairs.** S. Mechtaev, J. Yi, A. Roychoudhury. ICSE'15.

**SemFix: Program Repair via Semantic Analysis.** H.D.T. Nguyen, D. Qi, A. Roychoudhury, S. Chandra. ICSE'13.

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers:

* Sergey Mechtaev
* Jooyong Yi
