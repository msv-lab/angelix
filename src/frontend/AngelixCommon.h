#ifndef ANGELIX_COMMON_H
#define ANGELIX_COMMON_H

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


using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::tooling;
using namespace llvm;


#define INPLACE_MODIFICATION 1


SourceRange getExpandedLoc(const clang::Stmt* expr, SourceManager &srcMgr) {
  SourceLocation startLoc = expr->getLocStart();
  SourceLocation endLoc = expr->getLocEnd();

  if(startLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getImmediateExpansionRange(startLoc);
    // We're just interested in the start location
    startLoc = expansionRange.first;
  }
  if(endLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getImmediateExpansionRange(endLoc);
    // We're just interested in the start location
    endLoc = expansionRange.second; // TODO: I am not sure about it
  }
      
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


// Matchers for repairable expressions:

StatementMatcher RepairableNode =
  anyOf(unaryOperator(hasOperatorName("!")).bind("repairable"),
        // TODO: for pointer type we only interested if they are equal to 0 or other pointers:
        declRefExpr(to(varDecl(anyOf(hasType(isInteger()),
                                     hasType(pointerType(pointee(isInteger()))))))).bind("repairable"),
        integerLiteral().bind("repairable"),
        characterLiteral().bind("repairable"),
        binaryOperator(anyOf(hasOperatorName("=="),
                             hasOperatorName("!="),
                             hasOperatorName("<="),
                             hasOperatorName(">="),
                             hasOperatorName(">"),
                             hasOperatorName("<"),
                             hasOperatorName("+"),
                             hasOperatorName("-"),
                             hasOperatorName("*"),
                             hasOperatorName("/"),
                             hasOperatorName("||"),
                             hasOperatorName("&&"))).bind("repairable"));


StatementMatcher NonRepairableNode =
  unless(RepairableNode);


StatementMatcher RepairableExpression =
  allOf(RepairableNode, unless(hasDescendant(expr(ignoringParenImpCasts(NonRepairableNode)))));


StatementMatcher NonRepairableExpression =
  anyOf(unless(RepairableNode), hasDescendant(expr(ignoringParenImpCasts(NonRepairableNode))));


StatementMatcher Splittable =
  anyOf(binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(RepairableExpression)),
                       hasRHS(ignoringParenImpCasts(NonRepairableExpression))),
        binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(NonRepairableExpression)),
                       hasRHS(ignoringParenImpCasts(RepairableExpression))));


StatementMatcher RepairableIfCondition =
  anyOf(ifStmt(anyOf(hasCondition(ignoringParenImpCasts(RepairableExpression)),
                     eachOf(hasCondition(Splittable), hasCondition(forEachDescendant(Splittable))))),
        whileStmt(anyOf(hasCondition(ignoringParenImpCasts(RepairableExpression)),
                     eachOf(hasCondition(Splittable), hasCondition(forEachDescendant(Splittable))))));

#endif // ANGELIX_COMMON_H
