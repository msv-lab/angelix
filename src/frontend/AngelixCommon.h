#include <iostream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <unordered_map>
#include <fstream>
#include <cstdlib>
#include <stdlib.h>

#include "clang/AST/AST.h"
#include "clang/AST/ASTConsumer.h"
#include "clang/AST/RecursiveASTVisitor.h"
#include "clang/Frontend/ASTConsumers.h"
#include "clang/Frontend/FrontendActions.h"
#include "clang/Frontend/CompilerInstance.h"
#include "clang/Tooling/CommonOptionsParser.h"
#include "clang/Tooling/Tooling.h"
#include "clang/Rewrite/Core/Rewriter.h"
#include "llvm/Support/raw_ostream.h"

std::string toString(const clang::Stmt* stmt) {
  clang::LangOptions LangOpts;
  clang::PrintingPolicy Policy(LangOpts);
  std::string str;
  llvm::raw_string_ostream rso(str);

  stmt->printPretty(rso, nullptr, Policy);

  std::string stmtStr = rso.str();
  return stmtStr;
}
