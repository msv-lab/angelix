#include <iostream>
#include <sstream>
#include <fstream>

#include "../AngelixCommon.h"


std::string isBuggy(int beginLine, int beginColumn, int endLine, int endColumn) {
  std::string line;
  std::string suspiciousFile(getenv("ANGELIX_PATCH"));
  std::ifstream infile(suspiciousFile);
  int curBeginLine, curBeginColumn, curEndLine, curEndColumn;
  while (std::getline(infile, line)) {
    std::istringstream iss(line);
    iss >> curBeginLine >> curBeginColumn >> curEndLine >> curEndColumn;
    std::getline(infile, line);
    if (curBeginLine   == beginLine &&
        curBeginColumn == beginColumn &&
        curEndLine     == endLine &&
        curEndColumn   == endColumn) {
      return line;
    }
  }
  return "";
}


class ExpressionHandler : public MatchFinder::MatchCallback {
public:
  ExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("repairable")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr);

      unsigned beginLine = srcMgr.getExpansionLineNumber(expandedLoc.getBegin());
      unsigned beginColumn = srcMgr.getExpansionColumnNumber(expandedLoc.getBegin());
      unsigned endLine = srcMgr.getExpansionLineNumber(expandedLoc.getEnd());
      unsigned endColumn = srcMgr.getExpansionColumnNumber(expandedLoc.getEnd());

      std::string replacement = isBuggy(beginLine, beginColumn, endLine, endColumn);

      if (replacement == "") {
        return;
      }

      std::cout << beginLine << " " << beginColumn << " " << endLine << " " << endColumn << "\n"
                << "<   " << toString(expr) << "\n"
                << ">   " << replacement << "\n";

      Rewrite.ReplaceText(expandedLoc, replacement);
    }
  }

private:
  Rewriter &Rewrite;
};


class StatementHandler : public MatchFinder::MatchCallback {
public:
  StatementHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Stmt *stmt = Result.Nodes.getNodeAs<clang::Stmt>("repairable")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();

      SourceRange expandedLoc = getExpandedLoc(stmt, srcMgr);

      unsigned beginLine = srcMgr.getExpansionLineNumber(expandedLoc.getBegin());
      unsigned beginColumn = srcMgr.getExpansionColumnNumber(expandedLoc.getBegin());
      unsigned endLine = srcMgr.getExpansionLineNumber(expandedLoc.getEnd());
      unsigned endColumn = srcMgr.getExpansionColumnNumber(expandedLoc.getEnd());

      std::string guard = isBuggy(beginLine, beginColumn, endLine, endColumn);

      if (guard == "") {
        return;
      }

      if (guard == "1") {
        return;
      }

      std::stringstream replacement;

      replacement << "if (" << guard << ") " << toString(stmt);

      std::cout << beginLine << " " << beginColumn << " " << endLine << " " << endColumn << "\n"
                << "<   " << toString(stmt) << "\n"
                << ">   " << replacement.str() << "\n";

      
      Rewrite.ReplaceText(expandedLoc, replacement.str());
    }
  }

private:
  Rewriter &Rewrite;
};


class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForExpressions(R), HandlerForStatements(R) {
    if (getenv("ANGELIX_SEMFIX_MODE")) {
      Matcher.addMatcher(InterestingCondition, &HandlerForExpressions);
      Matcher.addMatcher(InterestingIntegerAssignment, &HandlerForExpressions);
    } else {
      Matcher.addMatcher(InterestingRepairableExpression, &HandlerForExpressions);
      Matcher.addMatcher(InterestingStatement, &HandlerForStatements);
    }
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  ExpressionHandler HandlerForExpressions;
  StatementHandler HandlerForStatements;
  MatchFinder Matcher;
};


class ApplyPatchAction : public ASTFrontendAction {
public:
  ApplyPatchAction() {}

  void EndSourceFileAction() override {
    FileID ID = TheRewriter.getSourceMgr().getMainFileID();
    if (INPLACE_MODIFICATION) {
      overwriteMainChangedFile(TheRewriter);      
      //      TheRewriter.overwriteChangedFiles();
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

  return Tool.run(newFrontendActionFactory<ApplyPatchAction>().get());
}
