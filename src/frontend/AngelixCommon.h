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
        // pointers of various types are used in x->y expressions
        declRefExpr(to(varDecl(anyOf(hasType(isInteger()),
                                     hasType(pointerType()))))).bind("repairable"),
        integerLiteral().bind("repairable"),
        characterLiteral().bind("repairable"),
        // TODO: I need to make sure that base is a variable here:
        memberExpr().bind("repairable"), 
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


StatementMatcher TrivialNode =
  anyOf(declRefExpr(), integerLiteral(), characterLiteral(), memberExpr());


StatementMatcher RepairableExpression =
  allOf(RepairableNode, unless(hasDescendant(expr(ignoringParenImpCasts(NonRepairableNode)))));


StatementMatcher NonTrivialRepairableExpression =
  allOf(RepairableExpression, unless(TrivialNode));


StatementMatcher NonRepairableExpression =
  anyOf(unless(RepairableNode), hasDescendant(expr(ignoringParenImpCasts(NonRepairableNode))));


StatementMatcher Splittable =
  anyOf(binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(RepairableExpression)),
                       hasRHS(ignoringParenImpCasts(NonRepairableExpression))),
        binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(NonRepairableExpression)),
                       hasRHS(ignoringParenImpCasts(RepairableExpression))));


StatementMatcher NonTrivialSplittable =
  anyOf(binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(NonTrivialRepairableExpression)),
                       hasRHS(ignoringParenImpCasts(NonRepairableExpression))),
        binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                       hasLHS(ignoringParenImpCasts(NonRepairableExpression)),
                       hasRHS(ignoringParenImpCasts(NonTrivialRepairableExpression))));


//TODO: I don't know how to create variables for these
// narrowing matchers (what is the type?)
#define hasSplittableCondition                                          \
  anyOf(hasCondition(ignoringParenImpCasts(RepairableExpression)),      \
        eachOf(hasCondition(Splittable), hasCondition(forEachDescendant(Splittable))))


#define hasNonTrivialSplittableCondition                                     \
  anyOf(hasCondition(ignoringParenImpCasts(NonTrivialRepairableExpression)), \
        eachOf(hasCondition(NonTrivialSplittable),                           \
               hasCondition(forEachDescendant(NonTrivialSplittable))))


StatementMatcher RepairableIfCondition =
  ifStmt(hasSplittableCondition);


StatementMatcher NonTrivialRepairableIfCondition =
  ifStmt(hasNonTrivialSplittableCondition);


StatementMatcher RepairableLoopCondition = 
  anyOf(whileStmt(hasSplittableCondition),
        forStmt(hasSplittableCondition));


StatementMatcher NonTrivialRepairableLoopCondition = 
  anyOf(whileStmt(hasNonTrivialSplittableCondition),
        forStmt(hasNonTrivialSplittableCondition));


//TODO: better to create a variables, but I don't know what the type is
#define isTopLevelStatement                                 \
  anyOf(hasParent(compoundStmt()),                          \
        hasParent(ifStmt()),                                \
        hasParent(whileStmt()),                             \
        hasParent(forStmt()))


StatementMatcher RepairableAssignment =
  binaryOperator(isTopLevelStatement,
                 hasOperatorName("="),
                 hasLHS(ignoringParenImpCasts(declRefExpr())),
                 hasRHS(ignoringParenImpCasts(RepairableExpression)));


StatementMatcher NonTrivialRepairableAssignment =
  binaryOperator(isTopLevelStatement,
                 hasOperatorName("="),
                 hasLHS(ignoringParenImpCasts(declRefExpr())),
                 hasRHS(ignoringParenImpCasts(NonTrivialRepairableExpression)));


// Interesting expression is a repairable selected in a particular way
StatementMatcher InterestingExpression = anyOf(RepairableIfCondition,
                                               RepairableLoopCondition,
                                               RepairableAssignment);

// TODO: make variable
#define hasAngelixOutput\
  hasDescendant(callExpr(callee(functionDecl(hasName("angelix_ignore")))))


StatementMatcher InterestingStatement =
  anyOf(binaryOperator(isTopLevelStatement,
                       hasOperatorName("="),
                       hasLHS(ignoringParenImpCasts(declRefExpr())),
                       unless(hasAngelixOutput)).bind("repairable"),
        callExpr(isTopLevelStatement,
                 unless(hasAngelixOutput)).bind("repairable"));

#endif // ANGELIX_COMMON_H
