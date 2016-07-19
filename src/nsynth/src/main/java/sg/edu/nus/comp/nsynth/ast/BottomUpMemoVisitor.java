package sg.edu.nus.comp.nsynth.ast;

import sg.edu.nus.comp.nsynth.ast.theory.*;

/**
 * Created by Sergey Mechtaev on 26/6/2016.
 */
public interface BottomUpMemoVisitor {
    boolean alreadyVisited(Node node);

    void visitAgain(Node node);

    void visit(ProgramVariable programVariable);

    void visit(UIFApplication UIFApplication);

    void visit(Equal equal);

    void visit(Add add);

    void visit(Sub sub);

    void visit(Mult mult);

    void visit(Div div);

    void visit(And and);

    void visit(Or or);

    void visit(Iff iff);

    void visit(Impl impl);

    void visit(Greater greater);

    void visit(Less less);

    void visit(GreaterOrEqual greaterOrEqual);

    void visit(LessOrEqual lessOrEqual);

    void visit(Minus minus);

    void visit(Not not);

    void visit(IntConst intConst);

    void visit(BoolConst boolConst);

    void visit(TestInstance testInstance);

    void visit(Parameter parameter);

    void visit(Hole hole);

    void visit(ITE ite);

    void visit(Selector selector);

    void visit(BranchOutput branchOutput);

    void visit(ExpressionOutput expressionOutput);

    void visit(ExecutionInstance executionInstance);

    void visit(NotEqual notEqual);

    void visit(StatementInstance statementInstance);
}
