FROM ubuntu:14.04


MAINTAINER Sergey Mechtaev <mechtaev@gmail.com>

# Dependencies

RUN apt-get -y install apt-transport-https

RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823

RUN apt-get -y update

RUN apt-get -y install git wget xz-utils build-essential \
                       curl dejagnu subversion bison flex bc libcap-dev \
                       cmake libncurses5-dev libboost-all-dev \
                       default-jdk sbt


# Installing Angelix

RUN git clone --recursive https://github.com/mechtaev/angelix.git

WORKDIR angelix

ENV JAVA_TOOL_OPTIONS -Dfile.encoding=UTF8

RUN bash -c 'source activate && make z3 && make maxsmt && make synthesis && make all'

RUN rm -rf build/llvm-3.7.0.src

