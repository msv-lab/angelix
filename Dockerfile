FROM ubuntu:16.04
LABEL maintainer="mechtaev@gmail.com"
ARG DEBIAN_FRONTEND=noninteractive

RUN mkdir /angelix

RUN apt-get update && apt-get upgrade -y && apt-get autoremove -y

RUN apt-get install -y python3 python3-pip

RUN pip3 install --upgrade pip

RUN yes | pip3 install 'PySMT==0.8.0' 'wllvm==1.2.7'

RUN apt-get install -y wget unzip git

RUN cd /angelix && \
    wget https://github.com/Z3Prover/z3/releases/download/z3-4.7.1/z3-4.7.1-x64-ubuntu-16.04.zip && \
    unzip z3-4.7.1-x64-ubuntu-16.04.zip

RUN apt-get install -y build-essential \
                       curl \
                       libcap-dev \
                       cmake \
                       libncurses5-dev \
                       python-minimal \
                       python-pip \
                       zlib1g-dev \
                       libtcmalloc-minimal4 \
                       libgoogle-perftools-dev

RUN apt-get install -y clang-6.0 \
                       llvm-6.0 \
                       llvm-6.0-dev \
                       llvm-6.0-tools

RUN cd /angelix && \
    git clone https://github.com/klee/klee-uclibc.git && \  
    cd klee-uclibc && \
    git checkout 8ccd74cf69550a39b5e7bd40f4dbf1fdbdf0a4a3 && \
    ./configure --make-llvm-lib --with-llvm-config /usr/bin/llvm-config-6.0 && \
    make -j2

# TODO: I need to configure it for C++
RUN cd /angelix && \
    git clone https://github.com/klee/klee.git && \
    cd klee && \
    git checkout v2.0 && \
    mkdir build && \
    cd build && \
    cmake -DENABLE_SOLVER_Z3=ON \
          -DENABLE_POSIX_RUNTIME=ON \
          -DENABLE_KLEE_UCLIBC=ON \
          -DZ3_INCLUDE_DIRS=/angelix/z3-4.7.1-x64-ubuntu-16.04/include/ \
          -DZ3_LIBRARIES=/angelix/z3/z3-4.7.1-x64-ubuntu-16.04/bin/libz3.a \
          -DLLVM_CONFIG_BINARY=/usr/bin/llvm-config-6.0 \
          -DKLEE_UCLIBC_PATH=/angelix/klee-uclibc \
          -DENABLE_UNIT_TESTS=OFF \
          -DENABLE_SYSTEM_TESTS=OFF \
          .. && \
    make

# ADD repair /angelix/repair
