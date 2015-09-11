// Declares clang::SyntaxOnlyAction.
#include "clang/Frontend/FrontendActions.h"
#include "clang/Tooling/CommonOptionsParser.h"
#include "clang/Tooling/Tooling.h"
// Matchers.
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"

using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::tooling;
using namespace llvm;

// TODO: for pointer type we only interested if they are equal to 0 (== and !=)

StatementMatcher RepairableNode =
  anyOf(unaryOperator(hasOperatorName("!")),
          declRefExpr(to(varDecl(anyOf(hasType(isInteger()),
                                         hasType(pointerType(pointee(isInteger()))))))), 
          integerLiteral(),
          characterLiteral(),
          implicitCastExpr(), // We don't match it, but it can be in the repairable expression
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
                                 hasOperatorName("&&"))));

StatementMatcher NonRepairableNode = unless(RepairableNode);

StatementMatcher RepairableExpression =
  allOf(RepairableNode, unless(hasDescendant(expr(NonRepairableNode))));

StatementMatcher NonRepairableExpression =
  anyOf(unless(RepairableNode), hasDescendant(expr(NonRepairableNode)));

StatementMatcher BoundRepairableExpression =
  anyOf(unaryOperator(hasOperatorName("!"), hasUnaryOperand(RepairableExpression)).bind("repairable"),
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
                                 hasOperatorName("&&")),
                           allOf(hasLHS(ignoringParenImpCasts(RepairableExpression)),
                                   hasRHS(ignoringParenImpCasts(RepairableExpression)))).bind("repairable"));

StatementMatcher RepairableClauseNode =
  binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                   anyOf(allOf(hasLHS(ignoringParenImpCasts(BoundRepairableExpression)),
                                 hasRHS(ignoringParenImpCasts(NonRepairableExpression))),
                           allOf(hasLHS(ignoringParenImpCasts(NonRepairableExpression)),
                                   hasRHS(ignoringParenImpCasts(BoundRepairableExpression)))));

StatementMatcher RepairableClauses =
  anyOf(RepairableClauseNode, forEachDescendant(RepairableClauseNode));

StatementMatcher RepairableIfCondition =
  ifStmt(anyOf(hasCondition(ignoringParenImpCasts(BoundRepairableExpression)), hasCondition(ignoringParenImpCasts(RepairableClauses))));

class LoopPrinter : public MatchFinder::MatchCallback {
public :
  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *e = Result.Nodes.getNodeAs<clang::Expr>("repairable"))
      e->dump();
  }
};

// Apply a custom category to all command-line options so that they are the only ones displayed.
static llvm::cl::OptionCategory MyToolCategory("angelix options");

int main(int argc, const char **argv) {
  // CommonOptionsParser constructor will parse arguments and create a
  // CompilationDatabase.  In case of error it will terminate the program.
  CommonOptionsParser OptionsParser(argc, argv, MyToolCategory);

  // We hand the CompilationDatabase we created and the sources to run over into the tool constructor.
  ClangTool Tool(OptionsParser.getCompilations(), OptionsParser.getSourcePathList());

  LoopPrinter Printer;
  MatchFinder Finder;
  Finder.addMatcher(RepairableIfCondition, &Printer);

  return Tool.run(newFrontendActionFactory(&Finder).get());
}
