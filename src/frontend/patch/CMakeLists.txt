set(LLVM_LINK_COMPONENTS support)
set(LLVM_USED_LIBS clangTooling clangBasic clangAST)

add_clang_executable(apply-patch
  ApplyPatch.cpp
  )

target_link_libraries(apply-patch
  clangTooling
  clangBasic
  clangASTMatchers
  )
