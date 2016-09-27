/*
  \file        InstrumentPrintf.h
  \description To instrument all printf call expressions with ANGELIX_OUTPUT
  
  \history
   Date     | Remarks
   -------------------------------------------------------------------------------
   15062016 | Initial version
   24062016 | Check if RewriteBuffer from getRewriteBufferFor(id) is NULL
   05072016 | Add puts(...) and putchar(...) matchers
   22072016 | Add few functions, i.e. toString, transformPrintf, transformPuts,
            | transformPutChar.
            | Add statement matchers for conditional operator expression involving
            | printf, puts, and putchar in both true and false expression.
   29072016 | Removed the ANGELIX_OUTPUT instrumentation for printf with only 1
            | 1 argument (a string).
            | Commented the use of realEndLoc.
   03082016 | Add if (1) for printf and putchar involving ANGELIX_OUTPUT.
   08082016 | Add an isThereAngelixOutput flag to set to true if there is at
            | 1 ANGELIX_OUTPUT in printf arguments.
            | Remove the checking for 1-argument case in printf (as no replacement
            | is needed).
   23082016 | Return original expression as string for <= 1-argument case in
            | transformPrintf function.
 
*/

#ifndef INSTRUMENT_PRINTF_H
#define INSTRUMENT_PRINTF_H

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
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"
#include "clang/Lex/Preprocessor.h"


using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::tooling;
using namespace llvm;


#define INPLACE_MODIFICATION 1


bool insideMacro(const clang::Stmt* expr, SourceManager &srcMgr, const LangOptions &langOpts) {
  SourceLocation startLoc = expr->getLocStart();
  SourceLocation endLoc = expr->getLocEnd();
  // Get the real end location
  //SourceLocation realEndLoc = clang::Lexer::getLocForEndOfToken(endLoc, /*offset=*/0, srcMgr, langOpts);

  if(startLoc.isMacroID() && !Lexer::isAtStartOfMacroExpansion(startLoc, srcMgr, langOpts))
    return true;
  
  //if(realEndLoc.isMacroID() && !Lexer::isAtEndOfMacroExpansion(realEndLoc, srcMgr, langOpts))
  if(endLoc.isMacroID() && !Lexer::isAtEndOfMacroExpansion(endLoc, srcMgr, langOpts))
    return true;

  return false;
}


SourceRange getExpandedLoc(const clang::Stmt* expr, SourceManager &srcMgr, const LangOptions &langOpts ) {
  SourceLocation startLoc = expr->getLocStart();
  SourceLocation endLoc = expr->getLocEnd();  // This returns (last-1)th token's ending location
  // Get the real end location
  //SourceLocation realEndLoc = clang::Lexer::getLocForEndOfToken(endLoc, /*offset=*/0, srcMgr, langOpts);

  if(startLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(startLoc);
    // We're just interested in the start location
    startLoc = expansionRange.first;
  }
  
  //if(realEndLoc.isMacroID()) {
  if(endLoc.isMacroID()) {
    // Get the start/end expansion locations
    //std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(realEndLoc);
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(endLoc);
    // We're just interested in the end location
    //realEndLoc = expansionRange.second;
    endLoc = expansionRange.second;
  }
  //SourceRange expandedLoc(startLoc, realEndLoc);
  SourceRange expandedLoc(startLoc, endLoc);

  return expandedLoc;
}


std::string toString(const clang::Stmt* stmt) {
  clang::LangOptions LangOpts;
  clang::PrintingPolicy Policy(LangOpts);
  std::string str;
  llvm::raw_string_ostream rso(str);

  stmt->printPretty(rso, nullptr, Policy);

  std::string stmtStr = rso.str();
  return stmtStr;
}


bool overwriteMainChangedFile(Rewriter &TheRewriter) {
  bool AllWritten = true;
  FileID id = TheRewriter.getSourceMgr().getMainFileID();
  const FileEntry *Entry = TheRewriter.getSourceMgr().getFileEntryForID(id);
  const RewriteBuffer *rewriteBuffer = TheRewriter.getRewriteBufferFor(id);
  if (rewriteBuffer != NULL) {  // This prevents empty rewriteBuffer gets written to file
    std::error_code err_code;
    llvm::raw_fd_ostream out(Entry->getName(), err_code, llvm::sys::fs::F_None);
    rewriteBuffer->write(out);
    out.close();
  }
  return !AllWritten;
}


std::string transformPrintf(const CallExpr* ce, int indexNum) {
  std::string replacementStr = "";
  std::vector<std::string> argVector;
  bool isThereAngelixOutput = false;  // A flag to indicate if there is at least an ANGELIX_OUTPUT in the argument

  if (ce->getNumArgs() > 1) { // > 1 argument case
    for (int i = 0, j = ce->getNumArgs(); i < j; i++) {
      std::string argStr, argTypeStr;
      // Store the argument in string
      argStr = toString(ce->getArg(i));
      
      // Store the type of argument
      argTypeStr = ce->getArg(i)->getType().getAsString();
      // Compare argument type
      if ((std::strcmp(argTypeStr.c_str(), "int") == 0) || (std::strcmp(argTypeStr.c_str(), "unsigned int") == 0)) {
        // Note: char type is also reported as "int" by AST
        // Insert the ANGELIX_OUTPUT(type, expr, id)
        std::ostringstream ss;
        ss << "ANGELIX_OUTPUT(int, " << argStr << ", \"angelixVar" << indexNum << i << "\")";
        argVector.push_back(ss.str());
        if (!isThereAngelixOutput)  // Set the flag if false
          isThereAngelixOutput = true;
      }
      else if ((std::strcmp(argTypeStr.c_str(), "long") == 0) || (std::strcmp(argTypeStr.c_str(), "unsigned long") == 0) || (std::strcmp(argTypeStr.c_str(), "long long") == 0) || (std::strcmp(argTypeStr.c_str(), "unsigned long long") == 0)){
        // Insert the ANGELIX_OUTPUT(type, expr, id)
        std::ostringstream ss;
        ss << "ANGELIX_OUTPUT(long, " << argStr << ", \"angelixVar" << indexNum << i << "\")";
        argVector.push_back(ss.str());
        if (!isThereAngelixOutput)  // Set the flag if false
          isThereAngelixOutput = true;
      }
      else {
        argVector.push_back(argStr);
      }
    }  // end for

    // Create replacement string for printf by putting all together
    std::stringstream ss2;
    if (isThereAngelixOutput) {
      ss2 << "({ if (1) printf(";  // Insert if (1) only if there is at least one ANGELIX_OUTPUT
    }
    else {
      ss2 << "printf(";  // Without ({...})
    }
    for (unsigned int k = 0; k < argVector.size(); k++) {
      ss2 << argVector.at(k).c_str();
      // Except for the last argument, add a comma after each argument
      if (k < argVector.size() - 1)
        ss2 << ", ";
    }
    if (isThereAngelixOutput) {
      ss2 << "); })";
    }
    else {
      ss2 << ")";
    }
 
    replacementStr = ss2.str();
  }
  else {  // No change for <= 1 argument
      replacementStr = toString(ce);  // Return the original expression as a string
  }
  return replacementStr;
}


std::string transformPuts(const CallExpr* ce, int indexNum) {
  std::string replacementStr = "";
  std::string argStr;
  // Get the only 1 argument in puts()
  argStr = toString(ce->getArg(0));

  std::stringstream ss;
  ss << "({ puts("
     << argStr
     << "); "
     << "ANGELIX_OUTPUT(bool, 1, \"angelixOut"
     << indexNum
     << "\"); })";
               
  replacementStr = ss.str();

  return replacementStr;
}


std::string transformPutChar(const CallExpr* ce, int indexNum) {
  std::string replacementStr = "";
  std::string argStr;
  // Get the only 1 argument in putchar()
  argStr = toString(ce->getArg(0));

  std::stringstream ss;
  ss << "({ if (1) putchar("
     << argStr
     << "); "
     << "ANGELIX_OUTPUT(int, (int)" << argStr
     << ", \"angelixOut"
     << indexNum
     << "\"); })";
 
  replacementStr = ss.str();
  
  return replacementStr;
}


// Matcher(s) for instrumentable expressions
StatementMatcher PrintfCall =
  callExpr(callee(functionDecl(hasName("printf"))), unless(hasParent(conditionalOperator()))).bind("printf");

StatementMatcher PutsCall =
  callExpr(callee(functionDecl(hasName("puts"))), unless(hasParent(conditionalOperator()))).bind("puts");

StatementMatcher PutCharCall =
  callExpr(callee(functionDecl(hasName("putchar"))), unless(hasParent(conditionalOperator()))).bind("putchar");

StatementMatcher ConditionalWithPrintfStatement =
  conditionalOperator(
  allOf(hasTrueExpression(callExpr(callee(functionDecl(hasName("printf"))))),
  hasFalseExpression(callExpr(callee(functionDecl(hasName("printf"))))))).bind("conditional with printf");

StatementMatcher ConditionalWithPutsStatement =
  conditionalOperator(
  allOf(hasTrueExpression(callExpr(callee(functionDecl(hasName("puts"))))),
  hasFalseExpression(callExpr(callee(functionDecl(hasName("puts"))))))).bind("conditional with puts");

StatementMatcher ConditionalWithPutCharStatement =
  conditionalOperator(
  allOf(hasTrueExpression(callExpr(callee(functionDecl(hasName("putchar"))))),
  hasFalseExpression(callExpr(callee(functionDecl(hasName("putchar"))))))).bind("conditional with putchar");


#endif // INSTRUMENT_PRINTF_H
