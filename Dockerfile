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
                       sbt --no-install-recommends


# Installing Angelix

RUN apt-get install software-properties-common -y --no-install-recommends

RUN git clone --recursive https://github.com/mechtaev/angelix.git --depth 1


RUN apt-get purge icedtea-* openjdk-* -y
RUN add-apt-repository -y ppa:openjdk-r/ppa && apt-get update && sudo apt-get install -y openjdk-8-jdk
#check if java command is pointing to " link currently points to /opt/jdk/jdk1.8.0_05/bin/java"
RUN update-alternatives --display java

#check if java command is pointing to " link currently points to /opt/jdk/jdk1.8.0_05/bin/javac"
RUN update-alternatives --display javac


RUN java -version
RUN javac -version

WORKDIR angelix

ENV JAVA_TOOL_OPTIONS -Dfile.encoding=UTF8
RUN bash -c 'source activate && make -j8 llvm-gcc'
RUN bash -c 'source activate && make -j8 llvm2'
RUN bash -c 'source activate && make -j8 minisat'
RUN bash -c 'source activate && make -j8 stp'
RUN bash -c 'source activate && make -j8 klee-uclibc'
RUN bash -c 'source activate && make -j8 klee'
RUN bash -c 'source activate && make -j8 z3'
RUN bash -c 'source activate && make -j8 clang'
RUN bash -c 'source activate && make -j8 bear'
RUN bash -c 'source activate && make -j8 runtime'
RUN bash -c 'source activate && make -j8 frontend'
RUN /usr/bin/printf '\xfe\xed\xfe\xed\x00\x00\x00\x02\x00\x00\x00\x00\xe2\x68\x6e\x45\xfb\x43\xdf\xa4\xd9\x92\xdd\x41\xce\xb6\xb2\x1c\x63\x30\xd7\x92' > /etc/ssl/certs/java/cacerts
RUN update-ca-certificates -f
RUN /var/lib/dpkg/info/ca-certificates-java.postinst configure
RUN bash -c 'source activate && make -j8 maxsmt'
RUN bash -c 'source activate && make -j8 synthesis'
ENV VER=3.6.3
RUN wget http://www-eu.apache.org/dist/maven/maven-3/${VER}/binaries/apache-maven-${VER}-bin.tar.gz
RUN bash -c 'tar xvf apache-maven-${VER}-bin.tar.gz'
RUN bash -c 'rm apache-maven-${VER}-bin.tar.gz'
RUN bash -c 'mv apache-maven-${VER} /opt/maven'
RUN bash -c 'echo "export MAVEN_HOME=/opt/maven;export PATH=\$MAVEN_HOME/bin:\$PATH:\$MAVEN_HOME/bin" > /etc/profile.d/maven.sh'
RUN bash -c 'source /etc/profile.d/maven.sh && mvn -version'
RUN bash -c 'source activate && source /etc/profile.d/maven.sh && make -j8 nsynth'
RUN bash -c 'source activate && source /etc/profile.d/maven.sh && make -j8 semfix'

RUN rm -rf build/llvm-3.7.0.src
RUN rm -rf /opt/maven
