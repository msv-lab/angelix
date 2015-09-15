#include <iostream>
// Declares clang::SyntaxOnlyAction.
#include "clang/Frontend/FrontendActions.h"
#include "clang/Tooling/CommonOptionsParser.h"
#include "clang/Tooling/Tooling.h"
// Matchers.
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"

#include "AngelixCommon.h"


using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::tooling;
using namespace llvm;


StatementMatcher RepairableNode =
  anyOf(unaryOperator(hasOperatorName("!")).bind("repairable"),
        // TODO: for pointer type we only interested if they are equal to 0 or other pointers:
        declRefExpr(to(varDecl(anyOf(hasType(isInteger()),
                                     hasType(pointerType(pointee(isInteger()))))))).bind("repairable"),
        integerLiteral(),
        characterLiteral().bind("repairable"),
        // We don't capture it, but it can be in the repairable expression:
        implicitCastExpr().bind("repairable"),
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


StatementMatcher NonRepairableNode = unless(RepairableNode);


StatementMatcher RepairableExpression =
  allOf(RepairableNode, unless(hasDescendant(expr(NonRepairableNode))));


StatementMatcher NonRepairableExpression =
  anyOf(unless(RepairableNode), hasDescendant(expr(NonRepairableNode)));


StatementMatcher RepairableClauseNode =
  binaryOperator(anyOf(hasOperatorName("||"), hasOperatorName("&&")),
                 anyOf(allOf(hasLHS(ignoringParenImpCasts(RepairableExpression)),
                             hasRHS(ignoringParenImpCasts(NonRepairableExpression))),
                       allOf(hasLHS(ignoringParenImpCasts(NonRepairableExpression)),
                             hasRHS(ignoringParenImpCasts(RepairableExpression)))));


StatementMatcher RepairableClauses =
  anyOf(RepairableClauseNode, forEachDescendant(RepairableClauseNode)); // currently this forEach does not work


StatementMatcher RepairableIfCondition =
  ifStmt(anyOf(hasCondition(ignoringParenImpCasts(RepairableExpression)), hasCondition(ignoringParenImpCasts(RepairableClauses))));


class ConditionalHandler : public MatchFinder::MatchCallback {
public:
  ConditionalHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("repairable")) {
      SourceLocation startLoc = expr->getLocStart();
      SourceLocation endLoc = expr->getLocEnd();
      SourceManager &srcMgr = Rewrite.getSourceMgr();

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

      std::cout << toString(expr) << std::endl;

      unsigned startLine = srcMgr.getExpansionLineNumber(startLoc);
      unsigned startColumn = srcMgr.getExpansionColumnNumber(startLoc);
      unsigned endLine = srcMgr.getExpansionLineNumber(endLoc);
      unsigned endColumn = srcMgr.getExpansionColumnNumber(endLoc);

      std::cout << startLine << " " << startColumn << " " << endLine << " " << endColumn << std::endl;

      std::ostringstream stringStream;
      stringStream << "({ angelix_trace("
                   << startLine << ", "
                   << startColumn << ", "
                   << endLine << ", "
                   << endColumn
                   << "); "
                   << toString(expr)
                   << "; })";
      std::string replacement = stringStream.str();
      Rewrite.ReplaceText(expandedLoc, replacement);
    }
  }

private:
  Rewriter &Rewrite;
};


class AssignmentHandler : public MatchFinder::MatchCallback {
public:
  AssignmentHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("repairable")) {
      Rewrite.InsertText(expr->getLocStart(), "({ printf(\"\"); ", true, true);
      Rewrite.InsertText(expr->getLocEnd(), ";})", true, true);
    }
  }

private:
  Rewriter &Rewrite;
};

class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForConditional(R), HandlerForAssignment(R) {

    Matcher.addMatcher(RepairableIfCondition, &HandlerForConditional);
    // Matcher.addMatcher(RepairableAssignment, &HandlerForAssignment);
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  ConditionalHandler HandlerForConditional;
  AssignmentHandler HandlerForAssignment;
  MatchFinder Matcher;
};


class InstrumentRepairableAction : public ASTFrontendAction {
public:
  InstrumentRepairableAction() {}

  void EndSourceFileAction() override {
    bool inplace = false;
    FileID ID = TheRewriter.getSourceMgr().getMainFileID();
    if (inplace) {
      TheRewriter.overwriteChangedFiles();
    } else {
      TheRewriter.getEditBuffer(ID).write(llvm::outs());
    }
  }

  std::unique_ptr<ASTConsumer> CreateASTConsumer(CompilerInstance &CI, StringRef file) override {
    TheRewriter.setSourceMgr(CI.getSourceManager(), CI.getLangOpts());
    return llvm::make_unique<MyASTConsumer>(TheRewriter);
  }

private:
  Rewriter TheRewriter;
};


// Apply a custom category to all command-line options so that they are the only ones displayed.
static llvm::cl::OptionCategory MyToolCategory("angelix options");


int main(int argc, const char **argv) {
  // CommonOptionsParser constructor will parse arguments and create a
  // CompilationDatabase.  In case of error it will terminate the program.
  CommonOptionsParser OptionsParser(argc, argv, MyToolCategory);

  // We hand the CompilationDatabase we created and the sources to run over into the tool constructor.
  ClangTool Tool(OptionsParser.getCompilations(), OptionsParser.getSourcePathList());

  return Tool.run(newFrontendActionFactory<InstrumentRepairableAction>().get());
}
