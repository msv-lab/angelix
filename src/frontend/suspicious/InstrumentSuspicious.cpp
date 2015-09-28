#include <iostream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <unordered_map>
#include <fstream>

#include "../AngelixCommon.h"
#include "SMTLIB2.h"


std::unordered_set<VarDecl*> collectVarsFromScope(const ast_type_traits::DynTypedNode node, ASTContext* context) {
  const FunctionDecl* fd;
  if ((fd = node.get<FunctionDecl>()) != NULL) {
    std::unordered_set<VarDecl*> set;
    for (auto it = fd->param_begin(); it != fd->param_end(); ++it) {
      auto vd = cast<VarDecl>(*it);
      set.insert(vd);
    }
    return set;
  } else {
    ArrayRef<ast_type_traits::DynTypedNode> parents = context->getParents(node);
    if (parents.size() > 0) {
      const ast_type_traits::DynTypedNode parent = *(parents.begin()); // TODO: for now only first
      return collectVarsFromScope(parent, context);
    } else {
      std::unordered_set<VarDecl*> set;
      return set;
    }
  }
}


class CollectVariables : public StmtVisitor<CollectVariables> {
  std::unordered_set<VarDecl*> *Set;
  
public:
  CollectVariables(std::unordered_set<VarDecl*> *set): Set(set) {}

  void Collect(Expr *E) {
    if (E)
      Visit(E);
  }

  void Visit(Stmt* S) {
    StmtVisitor<CollectVariables>::Visit(S);
  }

  void VisitBinaryOperator(BinaryOperator *Node) {  
    Collect(Node->getLHS());
    Collect(Node->getRHS());
  }

  void VisitUnaryOperator(UnaryOperator *Node) {
    Collect(Node->getSubExpr());
  }

  void VisitImplicitCastExpr(ImplicitCastExpr *Node) {
    Collect(Node->getSubExpr());
  }

  void VisitIntegerLiteral(IntegerLiteral *Node) {
  }

  void VisitCharacterLiteral(CharacterLiteral *Node) {
  }

  void VisitDeclRefExpr(DeclRefExpr *Node) {
    VarDecl* vd;
    if ((vd = cast<VarDecl>(Node->getDecl())) != NULL) { // TODO: other kinds of declarations?
      Set->insert(vd);
    }
  }

};

std::unordered_set<VarDecl*> collectVarsFromExpr(const Stmt* stmt) {
  std::unordered_set<VarDecl*> set;
  CollectVariables T(&set);
  T.Visit(const_cast<Stmt*>(stmt));
  return set;
}


class ConditionalHandler : public MatchFinder::MatchCallback {
public:
  ConditionalHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Expr *expr = Result.Nodes.getNodeAs<clang::Expr>("repairable")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();

      SourceRange expandedLoc = getExpandedLoc(expr, srcMgr);

      unsigned beginLine = srcMgr.getSpellingLineNumber(expandedLoc.getBegin());
      unsigned beginColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getBegin());
      unsigned endLine = srcMgr.getSpellingLineNumber(expandedLoc.getEnd());
      unsigned endColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getEnd());

      std::ostringstream exprId;
      exprId << beginLine << "-" << beginColumn << "-" << endLine << "-" << endColumn;
      std::string extractedDir(getenv("ANGELIX_EXTRACTED"));
      std::ofstream fs(extractedDir + "/" + exprId.str() + ".smt2");
      fs << "(assert " << toSMTLIB2(expr) << ")\n";

      const ast_type_traits::DynTypedNode node = ast_type_traits::DynTypedNode::create(*expr);
      std::unordered_set<VarDecl*> varsFromScope = collectVarsFromScope(node, Result.Context);
      std::unordered_set<VarDecl*> varsFromExpr = collectVarsFromExpr(expr);
      std::unordered_set<VarDecl*> vars;
      vars.insert(varsFromScope.begin(), varsFromScope.end());
      vars.insert(varsFromExpr.begin(), varsFromExpr.end());
      std::ostringstream varStream;
      std::ostringstream varNameStream;
      bool first = true;
      for (auto it = vars.begin(); it != vars.end(); ++it) {
        if (first) {
          first = false;
        } else {
          varStream << ", ";
          varNameStream << ", ";
        }
        VarDecl* var = *it;
        varStream << var->getName().str();
        varNameStream << "\"" << var->getName().str() << "\"";
      }

      std::ostringstream stringStream;
      stringStream << "ANGELIX_SUSPICIOUS("
                   << "bool" << ", "
                   << beginLine << ", "
                   << beginColumn << ", "
                   << endLine << ", "
                   << endColumn << ", "
                   << toString(expr) << ", "
                   << "((char*[]){" << varNameStream.str() << "}), "
                   << "((int[]){" << varStream.str() << "}), "
                   << vars.size()
                   << ")";

      std::string replacement = stringStream.str();
      Rewrite.ReplaceText(expandedLoc, replacement);
    }
  }

private:
  Rewriter &Rewrite;

};


class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForConditional(R) {

    Matcher.addMatcher(RepairableIfCondition, &HandlerForConditional);
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  ConditionalHandler HandlerForConditional;
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
