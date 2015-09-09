set(LLVM_LINK_COMPONENTS support)
set(LLVM_USED_LIBS clangTooling clangBasic clangAST)

add_clang_executable(instrument-repairable
  InstrumentRepairable.cpp
  )
target_link_libraries(instrument-repairable
  clangTooling
  clangBasic
  clangASTMatchers
  )
