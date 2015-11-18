#include <iostream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <unordered_map>
#include <fstream>

#include "../AngelixCommon.h"
#include "SMTLIB2.h"


std::unordered_set<VarDecl*> collectVarsFromScope(const ast_type_traits::DynTypedNode node, ASTContext* context, unsigned line) {
  const FunctionDecl* fd;
  if ((fd = node.get<FunctionDecl>()) != NULL) {
    std::unordered_set<VarDecl*> set;
    for (auto it = fd->param_begin(); it != fd->param_end(); ++it) {
      auto vd = cast<VarDecl>(*it);
      set.insert(vd);
    }

    if (fd->hasBody()) {
      Stmt* body = fd->getBody();
      CompoundStmt* cstmt;
      if ((cstmt = cast<CompoundStmt>(body)) != NULL) {
        for (auto it = cstmt->body_begin(); it != cstmt->body_end(); ++it) {
          if (isa<BinaryOperator>(*it)) {
            BinaryOperator* op = cast<BinaryOperator>(*it);
            SourceRange expandedLoc = getExpandedLoc(op, context->getSourceManager());
            unsigned beginLine = context->getSourceManager().getSpellingLineNumber(expandedLoc.getBegin());          
            if (line > beginLine &&                
                BinaryOperator::getOpcodeStr(op->getOpcode()).lower() == "=" &&
                isa<DeclRefExpr>(op->getLHS())) {
              DeclRefExpr* dref = cast<DeclRefExpr>(op->getLHS());
              VarDecl* vd;
              if ((vd = cast<VarDecl>(dref->getDecl())) != NULL && vd->getType().getTypePtr()->isIntegerType()) {
                set.insert(vd);
              }
            }
          }
        }
      }
    }

    if (getenv("ANGELIX_GLOBAL_VARIABLES")) {
      ArrayRef<ast_type_traits::DynTypedNode> parents = context->getParents(node);
      if (parents.size() > 0) {
        const ast_type_traits::DynTypedNode parent = *(parents.begin()); // TODO: for now only first
        const TranslationUnitDecl* tu;
        if ((tu = parent.get<TranslationUnitDecl>()) != NULL) {
          for (auto it = tu->decls_begin(); it != tu->decls_end(); ++it) {
            if (isa<VarDecl>(*it)) {
              VarDecl* vd = cast<VarDecl>(*it);
              unsigned beginLine = getDeclExpandedLine(vd, context->getSourceManager());
              if (line > beginLine && vd->getType().getTypePtr()->isIntegerType()) set.insert(vd);
            }
          }
        }
      }
    }
    
    return set;
    
  } else {
    ArrayRef<ast_type_traits::DynTypedNode> parents = context->getParents(node);
    if (parents.size() > 0) {
      const ast_type_traits::DynTypedNode parent = *(parents.begin()); // TODO: for now only first
      return collectVarsFromScope(parent, context, line);
    } else {
      std::unordered_set<VarDecl*> set;
      return set;
    }
  }
}


bool isBooleanExpr(const Expr* expr) {
  if (isa<BinaryOperator>(expr)) {
    const BinaryOperator* op = cast<BinaryOperator>(expr);
    std::string opStr = BinaryOperator::getOpcodeStr(op->getOpcode()).lower();
    return opStr == "==" || opStr == "!=" || opStr == "<=" || opStr == ">=" || opStr == ">" || opStr == "<" || opStr == "||" || opStr == "&&";
  }
  return false;
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


class ExpressionHandler : public MatchFinder::MatchCallback {
public:
  ExpressionHandler(Rewriter &Rewrite, std::string t) : Rewrite(Rewrite), type(t) {}

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
      std::unordered_set<VarDecl*> varsFromScope = collectVarsFromScope(node, Result.Context, beginLine);
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
      stringStream << "ANGELIX_CHOOSE("
                   << type << ", "
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
  std::string type;

};


class SemfixExpressionHandler : public MatchFinder::MatchCallback {
public:
  SemfixExpressionHandler(Rewriter &Rewrite, std::string t) : Rewrite(Rewrite), type(t) {}

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
      fs << "(assert true)\n"; // this is a hack, but not dangerous

      const ast_type_traits::DynTypedNode node = ast_type_traits::DynTypedNode::create(*expr);
      std::unordered_set<VarDecl*> varsFromScope = collectVarsFromScope(node, Result.Context, beginLine);
      std::unordered_set<VarDecl*> vars;
      vars.insert(varsFromScope.begin(), varsFromScope.end());
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

      int size = vars.size();

      std::string correctedType = type;
      if (type == "int" && isBooleanExpr(expr)) {
        correctedType = "bool";
      }

      std::ostringstream stringStream;
      stringStream << "ANGELIX_CHOOSE("
                   << correctedType << ", "
                   << "1" << ", " // don't execute expression, because it can have side effects
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
  std::string type;

};


class StatementHandler : public MatchFinder::MatchCallback {
public:
  StatementHandler(Rewriter &Rewrite) : Rewrite(Rewrite) {}

  virtual void run(const MatchFinder::MatchResult &Result) {
    if (const Stmt *stmt = Result.Nodes.getNodeAs<clang::Stmt>("repairable")) {
      SourceManager &srcMgr = Rewrite.getSourceMgr();

      SourceRange expandedLoc = getExpandedLoc(stmt, srcMgr);

      unsigned beginLine = srcMgr.getSpellingLineNumber(expandedLoc.getBegin());
      unsigned beginColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getBegin());
      unsigned endLine = srcMgr.getSpellingLineNumber(expandedLoc.getEnd());
      unsigned endColumn = srcMgr.getSpellingColumnNumber(expandedLoc.getEnd());

      if (! isSuspicious(beginLine, beginColumn, endLine, endColumn)) {
        return;
      }

      std::cout << beginLine << " " << beginColumn << " " << endLine << " " << endColumn << "\n"
                << toString(stmt) << "\n";

      std::ostringstream stmtId;
      stmtId << beginLine << "-" << beginColumn << "-" << endLine << "-" << endColumn;
      std::string extractedDir(getenv("ANGELIX_EXTRACTED"));
      std::ofstream fs(extractedDir + "/" + stmtId.str() + ".smt2");
      fs << "(assert true)\n";

      const ast_type_traits::DynTypedNode node = ast_type_traits::DynTypedNode::create(*stmt);
      std::unordered_set<VarDecl*> varsFromScope = collectVarsFromScope(node, Result.Context, beginLine);
      std::unordered_set<VarDecl*> vars;
      vars.insert(varsFromScope.begin(), varsFromScope.end());
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

      int size = vars.size();

      std::ostringstream stringStream;
      stringStream << "if ("
                   << "ANGELIX_CHOOSE("
                   << "bool" << ", "
                   << 1 << ", "
                   << beginLine << ", "
                   << beginColumn << ", "
                   << endLine << ", "
                   << endColumn << ", "
                   << "((char*[]){" << nameStream.str() << "}), "
                   << "((int[]){" << exprStream.str() << "}), "
                   << size
                   << ")"
                   << ") "
                   << toString(stmt);
      std::string replacement = stringStream.str();

      Rewrite.ReplaceText(expandedLoc, replacement);
    }
  }

private:
  Rewriter &Rewrite;

};


class MyASTConsumer : public ASTConsumer {
public:
  MyASTConsumer(Rewriter &R) : HandlerForIntegerExpressions(R, "int"),
                               HandlerForBooleanExpressions(R, "bool"),
                               HandlerForStatements(R),
                               SemfixHandlerForIntegerExpressions(R, "int"),
                               SemfixHandlerForBooleanExpressions(R, "bool")  {
    if (getenv("ANGELIX_SEMFIX_MODE")) {
      Matcher.addMatcher(InterestingCondition, &SemfixHandlerForBooleanExpressions);
      Matcher.addMatcher(InterestingIntegerAssignment, &SemfixHandlerForIntegerExpressions);
    } else {
      Matcher.addMatcher(RepairableAssignment, &HandlerForIntegerExpressions);
      Matcher.addMatcher(InterestingRepairableCondition, &HandlerForBooleanExpressions);      
      Matcher.addMatcher(InterestingStatement, &HandlerForStatements);
    }
  }

  void HandleTranslationUnit(ASTContext &Context) override {
    Matcher.matchAST(Context);
  }

private:
  ExpressionHandler HandlerForIntegerExpressions;
  ExpressionHandler HandlerForBooleanExpressions;  
  StatementHandler HandlerForStatements;
  SemfixExpressionHandler SemfixHandlerForIntegerExpressions;
  SemfixExpressionHandler SemfixHandlerForBooleanExpressions;  
  
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
