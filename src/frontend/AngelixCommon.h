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
#include "clang/Lex/Preprocessor.h"


using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::tooling;
using namespace llvm;


#define INPLACE_MODIFICATION 1


unsigned getDeclExpandedLine(const clang::Decl* decl, SourceManager &srcMgr) {
  SourceLocation startLoc = decl->getLocStart();
  if(startLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(startLoc);
    // We're just interested in the start location
    startLoc = expansionRange.first;
  }

  return srcMgr.getExpansionLineNumber(startLoc);
}


bool insideMacro(const clang::Stmt* expr, SourceManager &srcMgr, const LangOptions &langOpts) {
  SourceLocation startLoc = expr->getLocStart();
  SourceLocation endLoc = expr->getLocEnd();

  if(startLoc.isMacroID() && !Lexer::isAtStartOfMacroExpansion(startLoc, srcMgr, langOpts))
    return true;
  
  if(endLoc.isMacroID() && !Lexer::isAtEndOfMacroExpansion(endLoc, srcMgr, langOpts))
    return true;

  return false;
}


SourceRange getExpandedLoc(const clang::Stmt* expr, SourceManager &srcMgr) {
  SourceLocation startLoc = expr->getLocStart();
  SourceLocation endLoc = expr->getLocEnd();

  if(startLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(startLoc);
    // We're just interested in the start location
    startLoc = expansionRange.first;
  }
  if(endLoc.isMacroID()) {
    // Get the start/end expansion locations
    std::pair<SourceLocation, SourceLocation> expansionRange = srcMgr.getExpansionRange(endLoc);
    // We're just interested in the start location
    endLoc = expansionRange.second; // TODO: I am not sure about it
  }
      
  SourceRange expandedLoc(startLoc, endLoc);

  return expandedLoc;
}


std::string toString(const clang::Stmt* stmt) {
  /* Special case for break and continue statement
     Reason: There were semicolon ; and newline found
     after break/continue statement was converted to string
  */
  if (dyn_cast<clang::BreakStmt>(stmt))
    return "break";
  
  if (dyn_cast<clang::ContinueStmt>(stmt))
    return "continue";

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
  std::error_code err_code;  
  llvm::raw_fd_ostream out(Entry->getName(), err_code, llvm::sys::fs::F_None);
  TheRewriter.getRewriteBufferFor(id)->write(out);
  out.close();
  return !AllWritten;
}


// Matchers for repairable expressions:

StatementMatcher RepairableNode =
  anyOf(unaryOperator(hasOperatorName("!")).bind("repairable"),
        // TODO: for pointer type we only interested if they are equal to 0 or other pointers:
        // pointers of various types are used in x->y expressions
        declRefExpr(to(varDecl(anyOf(hasType(isInteger()),
                                     hasType(pointerType()))))).bind("repairable"),
        declRefExpr(to(enumConstantDecl())).bind("repairable"),
        declRefExpr(to(namedDecl())), // no binding because it is only for member expression
        arraySubscriptExpr(hasIndex(ignoringImpCasts(anyOf(integerLiteral(), declRefExpr(), memberExpr()))),
                           hasBase(implicitCastExpr(hasSourceExpression(declRefExpr(hasType(arrayType(hasElementType(isInteger())))))))).bind("repairable"),
        integerLiteral().bind("repairable"),
        characterLiteral().bind("repairable"),
        // TODO: I need to make sure that base is a variable here:
        memberExpr().bind("repairable"), 
        castExpr(hasType(asString("void *")), hasDescendant(integerLiteral(equals(0)))).bind("repairable"), // NULL
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
#define isTopLevelStatement                     \
  allOf(anyOf(hasParent(compoundStmt()),        \
              hasParent(ifStmt()),              \
              hasParent(labelStmt()),           \
              hasParent(whileStmt()),           \
              hasParent(forStmt())),            \
        unless(has(forStmt())),                 \
        unless(has(whileStmt())),               \
        unless(has(ifStmt())))


StatementMatcher RepairableAssignment =
  binaryOperator(isTopLevelStatement,
                 anyOf(hasOperatorName("="), hasOperatorName("+="), hasOperatorName("-="), hasOperatorName("*=")),
                 anyOf(hasLHS(ignoringParenImpCasts(declRefExpr())),
                       hasLHS(ignoringParenImpCasts(memberExpr())),
                       hasLHS(ignoringParenImpCasts(arraySubscriptExpr()))),
                 hasRHS(ignoringParenImpCasts(RepairableExpression)));


StatementMatcher NonTrivialRepairableAssignment =
  binaryOperator(isTopLevelStatement,
                 anyOf(hasOperatorName("="), hasOperatorName("+="), hasOperatorName("-="), hasOperatorName("*=")),
                 anyOf(hasLHS(ignoringParenImpCasts(declRefExpr())),
                       hasLHS(ignoringParenImpCasts(memberExpr())),
                       hasLHS(ignoringParenImpCasts(arraySubscriptExpr()))),
                 hasRHS(ignoringParenImpCasts(NonTrivialRepairableExpression)));

//TODO: currently these selectors are not completely orthogonal
// for example, if RHS of assignment contains if condition like here:
// x = ({ if (...) {...}; 1; });
// it may fail


StatementMatcher InterestingRepairableCondition =
  anyOf(RepairableIfCondition,
        RepairableLoopCondition);


StatementMatcher InterestingRepairableExpression =
  anyOf(InterestingRepairableCondition,
        RepairableAssignment);


// TODO: make variable instead of macro
#define hasAngelixOutput\
 anyOf(hasDescendant(callExpr(callee(functionDecl(matchesName("angelix_ignore"))))), callExpr(callee(functionDecl(matchesName("angelix_ignore")))))


StatementMatcher InterestingCondition =
  anyOf(ifStmt(hasCondition(expr(unless(hasAngelixOutput)).bind("repairable"))),
        whileStmt(hasCondition(expr(unless(hasAngelixOutput)).bind("repairable"))),
        forStmt(hasCondition(expr(unless(hasAngelixOutput)).bind("repairable"))));

//TODO: do I need it?
StatementMatcher InterestingIntegerAssignment =
  binaryOperator(isTopLevelStatement,
                 anyOf(hasOperatorName("="), hasOperatorName("+="), hasOperatorName("-="), hasOperatorName("*=")),
                 anyOf(hasLHS(ignoringParenImpCasts(declRefExpr(hasType(isInteger())))),
                       hasLHS(ignoringParenImpCasts(memberExpr(hasType(isInteger()))))),
                 hasRHS(expr().bind("repairable")),
                 unless(hasAngelixOutput));


StatementMatcher InterestingAssignment =
  binaryOperator(isTopLevelStatement,
                 anyOf(hasOperatorName("="), hasOperatorName("+="), hasOperatorName("-="), hasOperatorName("*=")),
                 anyOf(hasLHS(ignoringParenImpCasts(declRefExpr())),
                       hasLHS(ignoringParenImpCasts(memberExpr())),
                       hasLHS(ignoringParenImpCasts(arraySubscriptExpr()))),
                 unless(hasAngelixOutput)).bind("repairable");


StatementMatcher InterestingCall =
  callExpr(isTopLevelStatement,
           unless(hasAngelixOutput)).bind("repairable");


StatementMatcher InterestingStatement =
  anyOf(InterestingAssignment, InterestingCall, breakStmt().bind("repairable"),continueStmt().bind("repairable"));

#endif // ANGELIX_COMMON_H
