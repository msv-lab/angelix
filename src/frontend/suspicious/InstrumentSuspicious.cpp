#include <iostream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <unordered_map>
#include <fstream>

#include "../AngelixCommon.h"
#include "SMTLIB2.h"


// TODO: currently
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
  std::unordered_set<VarDecl*> *VSet;
  std::unordered_set<MemberExpr*> *MSet;
  
public:
  CollectVariables(std::unordered_set<VarDecl*> *vset, std::unordered_set<MemberExpr*> *mset): VSet(vset), MSet(mset) {}

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

  void VisitMemberExpr(MemberExpr *Node) {
    if (MSet) {
      MSet->insert(Node);
    }
  }

  void VisitDeclRefExpr(DeclRefExpr *Node) {
    if (VSet) {
      VarDecl* vd;
      if ((vd = cast<VarDecl>(Node->getDecl())) != NULL) { // TODO: other kinds of declarations?
        VSet->insert(vd);
      }
    }
  }

};

std::unordered_set<VarDecl*> collectVarsFromExpr(const Stmt* stmt) {
  std::unordered_set<VarDecl*> set;
  CollectVariables T(&set, NULL);
  T.Visit(const_cast<Stmt*>(stmt));
  return set;
}

std::unordered_set<MemberExpr*> collectMemberExprFromExpr(const Stmt* stmt) {
  std::unordered_set<MemberExpr*> set;
  CollectVariables T(NULL, &set);
  T.Visit(const_cast<Stmt*>(stmt));
  return set;
}

bool isSuspicious(int beginLine, int beginColumn, int endLine, int endColumn) {
  std::string line;
  std::string suspiciousFile(getenv("ANGELIX_SUSPICIOUS"));
  std::ifstream infile(suspiciousFile);
  int curBeginLine, curBeginColumn, curEndLine, curEndColumn;
  while (std::getline(infile, line)) {
    std::istringstream iss(line);
    iss >> curBeginLine >> curBeginColumn >> curEndLine >> curEndColumn;
    if (curBeginLine   == beginLine &&
        curBeginColumn == beginColumn &&
        curEndLine     == endLine &&
        curEndColumn   == endColumn) {
      return true;
    }
  }
  return false;
}


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

      if (! isSuspicious(beginLine, beginColumn, endLine, endColumn)) {
        return;
      }

      std::cout << beginLine << " " << beginColumn << " " << endLine << " " << endColumn << "\n"
                << toString(expr) << "\n";

      std::ostringstream exprId;
      exprId << beginLine << "-" << beginColumn << "-" << endLine << "-" << endColumn;
      std::string extractedDir(getenv("ANGELIX_EXTRACTED"));
      std::ofstream fs(extractedDir + "/" + exprId.str() + ".smt2");
      fs << "(assert " << toSMTLIB2(expr) << ")\n";

      const ast_type_traits::DynTypedNode node = ast_type_traits::DynTypedNode::create(*expr);
      std::unordered_set<VarDecl*> varsFromScope = collectVarsFromScope(node, Result.Context);
      std::unordered_set<VarDecl*> varsFromExpr = collectVarsFromExpr(expr);
      std::unordered_set<MemberExpr*> memberFromExpr = collectMemberExprFromExpr(expr);
      std::unordered_set<VarDecl*> vars;
      vars.insert(varsFromScope.begin(), varsFromScope.end());
      vars.insert(varsFromExpr.begin(), varsFromExpr.end());
      std::ostringstream exprStream;
      std::ostringstream nameStream;
      bool first = true;
      for (auto it = vars.begin(); it != vars.end(); ++it) {
        if (first) {
          first = false;
        } else {
          exprStream << ", ";
          nameStream << ", ";
        }
        VarDecl* var = *it;
        exprStream << var->getName().str();
        nameStream << "\"" << var->getName().str() << "\"";
      }
      for (auto it = memberFromExpr.begin(); it != memberFromExpr.end(); ++it) {
        if (first) {
          first = false;
        } else {
          exprStream << ", ";
          nameStream << ", ";
        }
        MemberExpr* me = *it;
        exprStream << toString(me);
        nameStream << "\"" << toString(me) << "\"";
      }

      int size = vars.size() + memberFromExpr.size();

      std::ostringstream stringStream;
      stringStream << "ANGELIX_SUSPICIOUS("
                   << "bool" << ", "
                   << toString(expr) << ", "
                   << beginLine << ", "
                   << beginColumn << ", "
                   << endLine << ", "
                   << endColumn << ", "
                   << "((char*[]){" << nameStream.str() << "}), "
                   << "((int[]){" << exprStream.str() << "}), "
                   << size
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
  MyASTConsumer(Rewriter &R) : HandlerForSuspicious(R) {

    Matcher.addMatcher(Repairable, &HandlerForSuspicious);
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  Handler HandlerForSuspicious;
  MatchFinder Matcher;
};


class InstrumentSuspiciousAction : public ASTFrontendAction {
public:
  InstrumentSuspiciousAction() {}

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

  return Tool.run(newFrontendActionFactory<InstrumentSuspiciousAction>().get());
}
