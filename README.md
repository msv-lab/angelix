# Angelix #

Semantics-based [automated program repair](http://automated-program-repair.org) tool for C programs. Angelix fixes bugs manifested by failing test cases and searches for the minimal change in order to preserve the original source code. Powered by KLEE symbolic execution engine and Z3 SMT solver.

If you use Angelix in your research project, please include the following citation:

    @inproceedings{mechtaev2016angelix,
        title={Angelix: Scalable multiline program patch synthesis via symbolic analysis},
        author={Mechtaev, Sergey and Yi, Jooyong and Roychoudhury, Abhik},
        booktitle={Proceedings of the 38th International Conference on Software Engineering},
        pages={691--701},
        year={2016},
        organization={ACM}
    }


## Installation ##

Angelix is distributed in source code form and pre-installed in VirtualBox image. The VirtualBox image also contains Angelix ICSE'16 and SemFix ICSE'13 evaluation results.

### Option 1: VirtualBox image ###

You can download VirtualBox image with pre-installed Angelix. Note that it contains an outdated version of Angelix and is distributed only for the demonstration purpose.

Please download the following files:
https://s3-ap-southeast-1.amazonaws.com/angelix/angelix-icse16-v2.vbox
https://s3-ap-southeast-1.amazonaws.com/angelix/angelix-icse16-v2.vdi (9.7 GB)
https://s3-ap-southeast-1.amazonaws.com/angelix/md5sum.txt

Execute "md5sum -c md5sum.txt" to verify your download.

The image includes Ubuntu 14.04 64-bit with pre-installed Angelix.

The user/password are angelix/angelix.

### Option 2: Build from source ###

The following is required to install Angelix from source code:

* 40GB Hard drive
* 8GB Memory
* Ubuntu 14.04 64-bit\*

\* Angelix can run on other 64-bit Linux distributions, however installation scripts were tested only on Ubuntu 14.04. If you want to install Angelix on a different system, you may need to modify Makefile and activate scripts.

Clone repository recursively:

    git clone --recursive https://github.com/mechtaev/angelix.git

Install dependencies:

    sudo apt-get install git wget xz-utils build-essential time
    sudo apt-get install curl dejagnu subversion bison flex bc libcap-dev
    sudo apt-get install cmake libncurses5-dev libboost-all-dev

Install Java 8 (for Ubuntu 14.04) and Maven:

    sudo apt-get install software-properties-common
    sudo apt-add-repository ppa:webupd8team/java
    sudo apt-get update
    sudo apt-get install oracle-java8-installer
    sudo apt-get install maven

Install SBT by following [intructions](http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Linux.html).

Set Angelix environment:

    . activate

Download and build required modules:

    make default

Run tests:

    make test

## Documentation ##

* [Tutorial](doc/Tutorial.md)
* [Manual](doc/Manual.md)
* [Troubleshooting](doc/Troubleshooting.md)

To set optimal configuration for your subject, refer to the Configuration section of the [manual](doc/Manual.md).

## Changelog

### [1.1](https://github.com/mechtaev/angelix/tree/1.1) (2017-Mar-20)

**Implemented enhancements:**

- New experimental synthesizer (`--use-nsynth` option)
- Automatically instrumenting printf arguments with `ANGELIX_OUTPUT` (`--instr-printf` option)
- Finding all patches (`--generate-all` option)
- Improved language support

**Fixed bugs:**

- Various bugs in frontend, inference and synthesis.

### [1.0](https://github.com/mechtaev/angelix/tree/1.0) (2016-Jul-8)

**Implemented enhancements:**

- Supported NULL pointer, break and continue statements, 64-bit long in ANGELIX_OUTPUT.

**Fixed bugs:**

- Various reported bugs in frontend and localization.

### [icse16](https://github.com/mechtaev/angelix/tree/icse16) (2016-Feb-19)

Initial release used to reproduce ICSE'16 experiments. Available on [VirtualBox VM](https://docs.google.com/forms/d/1XoQ3AomEwd2hke7-ty8CDaQ_iH7TH3W5foO5BQWc-6o/viewform?usp=send_form).

## Publications ##

**Angelix: Scalable Multiline Program Patch Synthesis via Symbolic Analysis.** S. Mechtaev, J. Yi, A. Roychoudhury. ICSE'16. [\[pdf\]](http://www.comp.nus.edu.sg/~abhik/pdf/ICSE16-angelix.pdf)

**DirectFix: Looking for Simple Program Repairs.** S. Mechtaev, J. Yi, A. Roychoudhury. ICSE'15. [\[pdf\]](https://www.comp.nus.edu.sg/~abhik/pdf/ICSE15-directfix.pdf)

**SemFix: Program Repair via Semantic Analysis.** H.D.T. Nguyen, D. Qi, A. Roychoudhury, S. Chandra. ICSE'13. [\[pdf\]](https://www.comp.nus.edu.sg/~abhik/pdf/ICSE13-SEMFIX.pdf)

## Contributors ##

Principal investigator:

* Abhik Roychoudhury

Developers:

* Sergey Mechtaev
* Jooyong Yi

Contributors:

* Shin Hwei Tan
* Yulis
