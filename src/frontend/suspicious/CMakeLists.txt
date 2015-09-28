set(LLVM_LINK_COMPONENTS support)
set(LLVM_USED_LIBS clangTooling clangBasic clangAST)

add_clang_executable(instrument-suspicious
  InstrumentSuspicious.cpp
  SMTLIB2.cpp
  )

target_link_libraries(instrument-suspicious
  clangTooling
  clangBasic
  clangASTMatchers
  )

