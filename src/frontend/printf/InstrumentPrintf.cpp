/*
  \file        InstrumentPrintf.cpp
  \description To instrument all printf call expressions with ANGELIX_OUTPUT
  
  \history
   Date     | Remarks
   -------------------------------------------------------------------------------
   15062016 | Initial version
   01072016 | Change char angelixStr[256] to char angelixStr[1024]
   04072016 | Change to ANGELIX_OUTPUT of strlen to sum of ASCII decimal values of
            | all characters in the output string (angelixStr).
            | angelix_ignore1(...) is added, the function implementation of 'int
            | angelix_ignore1(int x) {return x;} must be added later into the
            | the instrumented code to compile successfully.
            | Similarly for ANGELIX_OUTPUT and ANGELIX_REACHABLE, they must also
            | defined in the instrumented code before compilation.
   05072016 | Add puts(...) and putchar(...) matchers and corresponding handlers.
            | Rename initial class ExpressionHandler to PrintfExpressionHandler.
   16072016 | Simplify the instrumentation of printf and puts arguments.
   18072016 | Change the identifier format for ANGELIX_OUTPUT.
   20072016 | Change angelixOut identifier format for ANGELIX_OUTPUT to use
            | angelixOutCounter (incremented by 1 for every printf/puts/putchar).
   22072016 | Add statement matchers and handlers for conditional operator
            | expression involving printf, puts, and putchar in both true and
            | false expressions.
   29072016 | Commented handler and matcher for puts and conditional operator
            | expression with puts.
 
*/

#include <iostream>
#include <sstream>

#include "InstrumentPrintf.h"

static int angelixOutCounter = 0;  // Global variable


class PrintfExpressionHandler : public MatchFinder::MatchCallback {
public:
  PrintfExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("printf")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      // Increment angelixOutCounter by 1 if matched
      angelixOutCounter++;

      if (const CallExpr *ce = dyn_cast<clang::CallExpr>(expr)) {
        std::string replacement = "";
        // Transform expression
        replacement = transformPrintf(ce, angelixOutCounter);

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }


private:
  Rewriter &Rewrite;
};


class PutsExpressionHandler : public MatchFinder::MatchCallback {
public:
  PutsExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("puts")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      // Increment angelixOutCounter by 1 if matched
      angelixOutCounter++;

      if (const CallExpr *ce = dyn_cast<clang::CallExpr>(expr)) {
        std::string replacement = "";       
        // Transform expression
        replacement = transformPuts(ce, angelixOutCounter);

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }

private:
  Rewriter &Rewrite;
};


class PutCharExpressionHandler : public MatchFinder::MatchCallback {
public:
  PutCharExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("putchar")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      // Increment angelixOutCounter by 1 if matched
      angelixOutCounter++;

      if (const CallExpr *ce = dyn_cast<clang::CallExpr>(expr)) {
        std::string replacement = "";
        // Transform expression
        replacement = transformPutChar(ce, angelixOutCounter);

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }

private:
  Rewriter &Rewrite;
};


class ConditionalPrintfExpressionHandler : public MatchFinder::MatchCallback {
public:
  ConditionalPrintfExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("conditional with printf")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      if (const ConditionalOperator *co = dyn_cast<clang::ConditionalOperator>(expr)) {
        std::string replacement  = "";
        std::string trueExprStr  = "";
        std::string falseExprStr = "";
        int currentIndex;

        // Get true expression
        const CallExpr* trueExpr = dyn_cast<clang::CallExpr>(co->getTrueExpr());  // first printf expression
        angelixOutCounter++;
        currentIndex = angelixOutCounter;
        // Transform the printf in true expression
        trueExprStr = transformPrintf(trueExpr, currentIndex);

        // Get false expression
        const CallExpr* falseExpr = dyn_cast<clang::CallExpr>(co->getFalseExpr());  // second printf expression
        angelixOutCounter++;
        currentIndex = angelixOutCounter;
        // Transform the printf in true expression
        falseExprStr = transformPrintf(falseExpr, currentIndex);
        
        // Put all together
        std::ostringstream ss;
        ss << toString(co->getCond()) << "? "
           << trueExprStr  << " : "  // as compound statement
           << falseExprStr;   // as compound statement

        replacement = ss.str();

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }

private:
  Rewriter &Rewrite;
};


class ConditionalPutsExpressionHandler : public MatchFinder::MatchCallback {
public:
  ConditionalPutsExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("conditional with puts")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      if (const ConditionalOperator *co = dyn_cast<clang::ConditionalOperator>(expr)) {
        std::string replacement  = "";
        std::string trueExprStr  = "";
        std::string falseExprStr = "";

        // Get true expression
        const CallExpr* trueExpr = dyn_cast<clang::CallExpr>(co->getTrueExpr());  // first puts expression
        angelixOutCounter++;
        // Transform the puts in true expression
        trueExprStr = transformPuts(trueExpr, angelixOutCounter);

        // Get false expression
        const CallExpr* falseExpr = dyn_cast<clang::CallExpr>(co->getFalseExpr());  // second puts expression
        angelixOutCounter++;
        // Transform the puts in true expression
        falseExprStr = transformPuts(falseExpr, angelixOutCounter);
        
        // Put all together
        std::ostringstream ss;
        ss << toString(co->getCond()) << "? "
           << trueExprStr  << " : "  // as compound statement
           << falseExprStr;   // as compound statement

        replacement = ss.str();

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }

private:
  Rewriter &Rewrite;
};


class ConditionalPutCharExpressionHandler : public MatchFinder::MatchCallback {
public:
  ConditionalPutCharExpressionHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("conditional with putchar")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();
      const LangOptions &langOpts = Rewrite.getLangOpts();

      if (insideMacro(expr, srcMgr, langOpts))
        return;

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr, langOpts);

      std::pair<FileID, unsigned> decLoc = srcMgr.getDecomposedExpansionLoc(expandedLoc.getBegin());
      if (srcMgr.getMainFileID() != decLoc.first)
        return;

      if (const ConditionalOperator *co = dyn_cast<clang::ConditionalOperator>(expr)) {
        std::string replacement  = "";
        std::string trueExprStr  = "";
        std::string falseExprStr = "";

        // Get true expression
        const CallExpr* trueExpr = dyn_cast<clang::CallExpr>(co->getTrueExpr());  // first putchar expression
        angelixOutCounter++;
        // Transform the putchar in true expression
        trueExprStr = transformPutChar(trueExpr, angelixOutCounter);

        // Get false expression
        const CallExpr* falseExpr = dyn_cast<clang::CallExpr>(co->getFalseExpr());  // second putchar expression
        angelixOutCounter++;
        // Transform the putchar in true expression
        falseExprStr = transformPutChar(falseExpr, angelixOutCounter);
        
        // Put all together
        std::ostringstream ss;
        ss << toString(co->getCond()) << "? "
           << trueExprStr  << " : "  // as compound statement
           << falseExprStr;   // as compound statement

        replacement = ss.str();

        if (replacement == "") {
          return;
        }

        Rewrite.ReplaceText(expandedLoc, replacement);
      }
    }
  }

private:
  Rewriter &Rewrite;
};



class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForPrintfExpression(R),
                               //HandlerForPutsExpression(R),
                               HandlerForPutCharExpression(R),
                               HandlerForConditionalPrintfExpression(R),
                               //HandlerForConditionalPutsExpression(R),
                               HandlerForConditionalPutCharExpression(R) {
    Matcher.addMatcher(PrintfCall, &HandlerForPrintfExpression);
    //Matcher.addMatcher(PutsCall, &HandlerForPutsExpression);
    Matcher.addMatcher(PutCharCall, &HandlerForPutCharExpression);
    Matcher.addMatcher(ConditionalWithPrintfStatement, &HandlerForConditionalPrintfExpression);
    //Matcher.addMatcher(ConditionalWithPutsStatement, &HandlerForConditionalPutsExpression);
    Matcher.addMatcher(ConditionalWithPutCharStatement, &HandlerForConditionalPutCharExpression);
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  PrintfExpressionHandler HandlerForPrintfExpression;
  //PutsExpressionHandler HandlerForPutsExpression;
  PutCharExpressionHandler HandlerForPutCharExpression;
  ConditionalPrintfExpressionHandler HandlerForConditionalPrintfExpression;
  //ConditionalPutsExpressionHandler HandlerForConditionalPutsExpression;
  ConditionalPutCharExpressionHandler HandlerForConditionalPutCharExpression;
  MatchFinder Matcher;
};


class InstrumentPrintfAction : public ASTFrontendAction {
public:
  InstrumentPrintfAction() {}

  void EndSourceFileAction() override {
    FileID ID = TheRewriter.getSourceMgr().getMainFileID();
    if (INPLACE_MODIFICATION) {
      overwriteMainChangedFile(TheRewriter);
    }
    else {
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
static llvm::cl::OptionCategory MyToolCategory("instrument-printf options");

int main(int argc, const char **argv) {
  // CommonOptionParser constructor will parse arguments & create a
  // CompilationDatabase. In case of error, it will terminate the program.
  CommonOptionsParser OptionsParser(argc, argv, MyToolCategory);

  // We hand the CompilationDatabase we created & the sources to run over into the tool constructor.
  ClangTool Tool(OptionsParser.getCompilations(), OptionsParser.getSourcePathList());

  return Tool.run(newFrontendActionFactory<InstrumentPrintfAction>().get());
}
