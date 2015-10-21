#include <iostream>
#include <sstream>

#include "../AngelixCommon.h"


class Handler : public MatchFinder::MatchCallback {
public:
  Handler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("repairable")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr);

      unsigned beginLine = srcMgr.getSpellingLineNumber(expandedLoc.getBegin());
      unsigned beginColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getBegin());
      unsigned endLine = srcMgr.getSpellingLineNumber(expandedLoc.getEnd());
      unsigned endColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getEnd());

      std::cout << beginLine << " " << beginColumn << " " << endLine << " " << endColumn << "\n"
                << toString(expr) << "\n";

      std::ostringstream stringStream;
      stringStream << "({ angelix_trace("
                   << beginLine << ", "
                   << beginColumn << ", "
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


class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForRepairable(R) {

    if (getenv("ANGELIX_CONDITIONS_DEFECT_CLASS"))
      Matcher.addMatcher(RepairableCondition, &HandlerForRepairable);

    if (getenv("ANGELIX_ASSIGNMENTS_DEFECT_CLASS"))
      Matcher.addMatcher(RepairableAssignment, &HandlerForRepairable);
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  Handler HandlerForRepairable;
  MatchFinder Matcher;
};


class InstrumentRepairableAction : public ASTFrontendAction {
public:
  InstrumentRepairableAction() {}

  void EndSourceFileAction() override {
    FileID ID = TheRewriter.getSourceMgr().getMainFileID();
    if (INPLACE_MODIFICATION) {
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
