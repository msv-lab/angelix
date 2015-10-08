MODULES = llvm-gcc llvm2 minisat stp klee-uclibc klee z3 clang bear runtime frontend maxsmt synthesis
CLEAN_MODULES = $(addprefix clean-, $(MODULES))

help:
	@echo ~~~~~~~~~~~~~~~~~~ Angelix ~~~~~~~~~~~~~~~~~~
	@echo Semantics-based Automated Program Repair Tool
	@echo
	@echo \'make all\''                  'build all modules
	@echo \'make MODULE\''                    'build module
	@echo \'make clean-all\''            'clean all modules
	@echo \'make clean-MODULE\''              'clean module
	@echo \'make test\''                         'run tests
	@echo
	@echo Modules:
	@echo -n ' '$(foreach module, $(MODULES),"* $(module)\n")

all: $(MODULES)
clean-all: $(CLEAN_MODULES)

.PHONY: help all $(MODULES) clean-all $(CLEAN_MODULES) test

# Information for retrieving dependencies

LLVM_GCC_URL="http://llvm.org/releases/2.9/llvm-gcc4.2-2.9-x86_64-linux.tar.bz2"
LLVM_GCC_ARCHIVE="llvm-gcc4.2-2.9-x86_64-linux.tar.bz2"
LLVM2_PATCH_URL="http://www.mail-archive.com/klee-dev@imperial.ac.uk/msg01302/unistd-llvm-2.9-jit.patch"
LLVM2_PATCH="unistd-llvm-2.9-jit.patch"
LLVM2_URL="http://llvm.org/releases/2.9/llvm-2.9.tgz"
LLVM2_ARCHIVE="llvm-2.9.tgz"
LLVM3_URL="http://llvm.org/releases/3.7.0/llvm-3.7.0.src.tar.xz"
LLVM3_ARCHIVE="llvm-3.7.0.src.tar.xz"
CLANG_URL="http://llvm.org/releases/3.7.0/cfe-3.7.0.src.tar.xz"
CLANG_ARCHIVE="cfe-3.7.0.src.tar.xz"
CLANG_TOOLS_EXTRA_URL="http://llvm.org/git/clang-tools-extra.git"
COMPILER_RT_URL="http://llvm.org/releases/3.7.0/compiler-rt-3.7.0.src.tar.xz"
COMPILER_RT_ARCHIVE="compiler-rt-3.7.0.src.tar.xz"
CLANG_TOOLS_EXTRA_URL="http://llvm.org/git/clang-tools-extra.git"
STP_URL="https://github.com/stp/stp.git"
MINISAT_URL="https://github.com/niklasso/minisat.git"
Z3_URL="https://github.com/Z3Prover/z3.git"
KLEE_URL="https://github.com/klee/klee.git"
KLEE_UCLIBC_URL="https://github.com/klee/klee-uclibc.git"
BEAR_URL="https://github.com/rizsotto/Bear.git"
MAXSMT_URL="https://github.com/mechtaev/maxsmt-playground.git"

# Testing #

test:
	python3 tests/tests.py

# LLVM-GCC #

llvm-gcc: build/$(LLVM_GCC_ARCHIVE)
	cd build && tar xf $(LLVM_GCC_ARCHIVE)

build/$(LLVM_GCC_ARCHIVE):
	cd build && wget $(LLVM_GCC_URL)

clean-llvm-gcc:

# LLVM2 #

llvm2: build/$(LLVM2_ARCHIVE) build/$(LLVM2_PATCH)
	cd build && tar xf $(LLVM2_ARCHIVE)
	cd build && patch -p0 < $(LLVM2_PATCH)
	cd $(LLVM2_DIR) && ./configure --enable-optimized --enable-assertions && make

build/$(LLVM2_ARCHIVE):
	cd build && wget $(LLVM2_URL)

build/$(LLVM2_PATCH):
	cd build && wget $(LLVM2_PATCH_URL)

clean-llvm2:
	rm -rf $(LLVM2_DIR)/build

# STP #

stp: $(STP_DIR)
	cd $(STP_DIR) && mkdir -p build && cd build && cmake -DMINISAT_LIBRARY="$(MINISAT_DIR)/build/libminisat.so.2" -DMINISAT_INCLUDE_DIR="$(MINISAT_DIR)" -G 'Unix Makefiles' $(STP_DIR) && make

$(STP_DIR):
	cd build && git clone --depth=1 $(STP_URL)

clean-stp:
	rm -rf $(STP_DIR)/build

# MINISAT #

minisat: $(MINISAT_DIR)
	cd $(MINISAT_DIR) && mkdir -p build && cd build && cmake -G 'Unix Makefiles' $(MINISAT_DIR) && make

$(MINISAT_DIR):
	cd build && git clone --depth=1 $(MINISAT_URL)

clean-minisat:
	rm -rf $(MINISAT_DIR)/build

# KLEE-UCLIBC #

klee-uclibc: $(KLEE_UCLIBC_DIR)
	cd $(KLEE_UCLIBC_DIR) && ./configure --make-llvm-lib && make -j2

$(KLEE_UCLIBC_DIR):
	cd build && git clone --depth=1 $(KLEE_UCLIBC_URL)

clean-klee-uclibc:
	cd $(KLEE_UCLIBC_DIR) && make clean

# KLEE #

klee: $(KLEE_DIR)
	cd $(KLEE_DIR) && ./configure --with-llvm=$(LLVM2_DIR) --with-stp=$(STP_DIR)/build --with-uclibc=$(KLEE_UCLIBC_DIR) --enable-posix-runtime && make ENABLE_OPTIMIZED=1

$(KLEE_DIR):
	cd build && git clone --depth=1 $(KLEE_URL)

clean-klee:
	cd $(KLEE_DIR) && make clean

# Angelix runtime #

runtime:
	cd src/runtime && make
	mkdir -p $(ANGELIX_ROOT)/build/lib/klee
	mkdir -p $(ANGELIX_ROOT)/build/lib/test
	cp src/runtime/libangelix.test.a $(ANGELIX_ROOT)/build/lib/test/libangelix.a
	cp src/runtime/libangelix.klee.a $(ANGELIX_ROOT)/build/lib/klee/libangelix.a
	cd src/runtime && make clean

clean-runtime:
	rm -rf $(ANGELIX_ROOT)/build/lib
	cd src/runtime && make clean

# Z3 #

z3: $(Z3_DIR)
	cd $(Z3_DIR) && python scripts/mk_make.py --java && cd build && make

$(Z3_DIR):
	cd build && git clone --depth=1 $(Z3_URL)

clean-z3:
	rm -rf $(Z3_DIR)/build

# MAX-SAT #

maxsmt: $(MAXSMT_DIR)
	mkdir -p $(MAXSMT_DIR)/lib
	cp $(Z3_JAR) $(MAXSMT_DIR)/lib/
	mkdir -p $(MAXSMT_DIR)/log
	cd $(MAXSMT_DIR) && sbt package

$(MAXSMT_DIR):
	cd build && git clone --depth=1 $(MAXSMT_URL)

clean-maxsmt:
	cd $(MAXSMT_DIR) && sbt clean

distclean-maxsmt:
	rm -rf $(MAXSMT_DIR)

# Synthesis #

synthesis:
	mkdir -p $(SYNTHESIS_DIR)/lib
	cp $(MAXSMT_JAR) $(SYNTHESIS_DIR)/lib/
	cp $(Z3_JAR) $(SYNTHESIS_DIR)/lib/
	mkdir -p $(SYNTHESIS_DIR)/log
	cd $(SYNTHESIS_DIR) && sbt assembly

clean-synthesis:
	cd $(SYNTHESIS_DIR) && sbt clean

distclean-synthesis: clean-synthesis

# Clang #

clang: build/$(LLVM3_ARCHIVE) build/$(CLANG_ARCHIVE) build/$(COMPILER_RT_ARCHIVE)
	cd build && tar xf $(LLVM3_ARCHIVE)
	mkdir -p "$(LLVM3_DIR)/tools/clang"
	cd build && tar xf $(CLANG_ARCHIVE) --directory "$(LLVM3_DIR)/tools/clang" --strip-components=1
	mkdir -p "$(LLVM3_DIR)/projects/compiler-rt"
	cd build && tar xf $(COMPILER_RT_ARCHIVE) --directory "$(LLVM3_DIR)/projects/compiler-rt" --strip-components=1
	cd "$(LLVM3_DIR)/tools/clang/tools/" && git clone --branch release_37 $(CLANG_TOOLS_EXTRA_URL) extra
	mkdir -p "$(LLVM3_DIR)/build" && cd "$(LLVM3_DIR)/build" && cmake -G "Unix Makefiles" ../ && make

build/$(LLVM3_ARCHIVE):
	cd build && wget $(LLVM3_URL)

build/$(CLANG_ARCHIVE):
	cd build && wget $(CLANG_URL)

build/$(COMPILER_RT_ARCHIVE):
	cd build && wget $(COMPILER_RT_URL)

clean-clang:
	rm -rf "$(LLVM3_DIR)/build"

# Frontend #

frontend: $(LLVM3_DIR)/tools/clang/tools/angelix
	grep -q angelix "$(LLVM3_DIR)/tools/clang/tools/CMakeLists.txt" || echo 'add_subdirectory(angelix)' >> "$(LLVM3_DIR)/tools/clang/tools/CMakeLists.txt"
	cd "$(LLVM3_DIR)/build" && make
	mkdir -p "$(LLVM3_DIR)/build/bin/angelix"
	cp "$(LLVM3_DIR)/build/bin/instrument-repairable" "$(LLVM3_DIR)/build/bin/angelix"
	cp "$(LLVM3_DIR)/build/bin/instrument-suspicious" "$(LLVM3_DIR)/build/bin/angelix"
	cp "$(LLVM3_DIR)/build/bin/apply-patch" "$(LLVM3_DIR)/build/bin/angelix"

$(LLVM3_DIR)/tools/clang/tools/angelix:
	ln -f -s "$(ANGELIX_ROOT)/src/frontend" "$(LLVM3_DIR)/tools/clang/tools/angelix"

clean-frontend:

# Bear #

bear: $(BEAR_DIR)
	cd $(BEAR_DIR) && mkdir -p build && cd build && cmake ../ && make all
	mkdir -p "$(ANGELIX_ROOT)/build/tools"

$(BEAR_DIR):
	cd build && git clone --depth=1 $(BEAR_URL)

clean-bear:
	rm -rf "$(BEAR_DIR)/build"
