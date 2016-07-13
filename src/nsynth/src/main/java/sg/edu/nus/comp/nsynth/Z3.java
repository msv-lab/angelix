package sg.edu.nus.comp.nsynth;

import com.microsoft.z3.*;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Z3 implements Solver {

    private Logger logger = LoggerFactory.getLogger(Z3.class);

    private Context globalContext;
    private com.microsoft.z3.Solver solver;

    private InterpolationContext globalIContext;

    public Z3() {
        HashMap<String, String> cfg = new HashMap<>();
        cfg.put("model", "true");
        this.globalContext = new Context(cfg);
        this.solver = globalContext.mkSolver();
    }

    public void dispose() {
        this.globalContext.dispose();
        this.globalIContext.dispose();
    }

    @Override
    public Optional<Map<Variable, Constant>> maxsat(List<Node> clauses, List<Node> assumptions) {

        solver.reset();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : clauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(globalContext, marshaller);
            clause.accept(visitor);
            solver.add((BoolExpr)visitor.getExpr());
        }
        ArrayList<BoolExpr> assumptionExprs = new ArrayList<>();
        for (Node assumption : assumptions) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(globalContext, marshaller);
            assumption.accept(visitor);
            assumptionExprs.add((BoolExpr)visitor.getExpr());
        }

        BoolExpr[] assumptionArray = assumptionExprs.toArray(new BoolExpr[assumptionExprs.size()]);

        Status status = solver.check(assumptionArray);
        if (status.equals(Status.SATISFIABLE)) {
            Model model = solver.getModel();
            return Optional.of(getAssignment(globalContext, model, marshaller));
        } else if (status.equals(Status.UNSATISFIABLE)) {
            ArrayList<Node> unsatCore = new ArrayList<>();
            Expr[] unsatCoreArray = solver.getUnsatCore();
            ArrayList<Expr> unsatCoreExprs = new ArrayList<>(Arrays.asList(unsatCoreArray));
            for (int i=0; i<assumptionExprs.size(); i++) {
                if (unsatCoreExprs.contains(assumptionExprs.get(i))) {
                    unsatCore.add(assumptions.get(i));
                }
            }
            return Optional.empty();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Optional<Map<Variable, Constant>> sat(List<Node> clauses) {
        solver.reset();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : clauses) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(globalContext, marshaller);
            clause.accept(visitor);
            solver.add((BoolExpr)visitor.getExpr());
        }

        Status status = solver.check();
        if (status.equals(Status.SATISFIABLE)) {
            Model model = solver.getModel();
            return Optional.of(getAssignment(globalContext, model, marshaller));
        } else if (status.equals(Status.UNSATISFIABLE)) {
            return Optional.empty();
        } else {
            throw new UnsupportedOperationException();
        }

    }

    private Map<Variable, Constant> getAssignment(Context ctx, Model model, VariableMarshaller marshaller) {
        HashMap<Variable, Constant> assignment = new HashMap<>();
        for (Variable variable: marshaller.getVariables()) {
            if (TypeInference.typeOf(variable).equals(IntType.TYPE)) {
                Expr result = model.eval(ctx.mkIntConst(marshaller.toString(variable)), true);
                if (result instanceof IntNum) {
                    int value = ((IntNum)result).getInt();
                    assignment.put(variable, IntConst.of(value));
                } else {
                    throw new RuntimeException("unsupported Z3 expression type");
                }
            } else if (TypeInference.typeOf(variable).equals(BoolType.TYPE)) {
                Expr result = model.eval(ctx.mkBoolConst(marshaller.toString(variable)), true);
                try {
                    boolean value = result.isTrue();
                    assignment.put(variable, BoolConst.of(value));
                } catch (Z3Exception ex){
                    throw new RuntimeException("wrong variable type");
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return assignment;
    }

    private class NodeTranslatorVisitor implements BottomUpVisitor {

        private Stack<Expr> exprs;

        private VariableMarshaller marshaller;
        private Context ctx;

        NodeTranslatorVisitor(Context ctx, VariableMarshaller marshaller) {
            this.marshaller = marshaller;
            this.exprs = new Stack<>();
            this.ctx = ctx;
        }

        Expr getExpr() {
            assert exprs.size() == 1;
            return exprs.peek();
        }

        private void processVariable(Context ctx, Variable variable) {
            if (TypeInference.typeOf(variable).equals(IntType.TYPE)) {
                exprs.push(ctx.mkIntConst(marshaller.toString(variable)));
            } else if (TypeInference.typeOf(variable).equals(BoolType.TYPE)) {
                exprs.push(ctx.mkBoolConst(marshaller.toString(variable)));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            processVariable(ctx, programVariable);
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            //TODO
            throw new UnsupportedOperationException();
        }

        @Override
        public void visit(Equal equal) {
            Expr right = exprs.pop();
            Expr left = exprs.pop();
            exprs.push(ctx.mkEq(left, right));
        }

        @Override
        public void visit(NotEqual notEqual) {
            Expr right = exprs.pop();
            Expr left = exprs.pop();
            exprs.push(ctx.mkNot(ctx.mkEq(left, right)));
        }

        @Override
        public void visit(Add add) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkAdd(left, right));
        }

        @Override
        public void visit(Sub sub) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkSub(left, right));
        }

        @Override
        public void visit(Mult mult) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkMul(left, right));
        }

        @Override
        public void visit(Div div) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkDiv(left, right));
        }

        @Override
        public void visit(And and) {
            BoolExpr right = (BoolExpr) exprs.pop();
            BoolExpr left = (BoolExpr) exprs.pop();
            exprs.push(ctx.mkAnd(left, right));
        }

        @Override
        public void visit(Or or) {
            BoolExpr right = (BoolExpr) exprs.pop();
            BoolExpr left = (BoolExpr) exprs.pop();
            exprs.push(ctx.mkOr(left, right));
        }

        @Override
        public void visit(Iff iff) {
            BoolExpr right = (BoolExpr) exprs.pop();
            BoolExpr left = (BoolExpr) exprs.pop();
            exprs.push(ctx.mkIff(left, right));
        }

        @Override
        public void visit(Impl impl) {
            BoolExpr right = (BoolExpr) exprs.pop();
            BoolExpr left = (BoolExpr) exprs.pop();
            exprs.push(ctx.mkImplies(left, right));
        }

        @Override
        public void visit(Greater greater) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkGt(left, right));
        }

        @Override
        public void visit(Less less) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkLt(left, right));
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkGe(left, right));
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            ArithExpr right = (ArithExpr) exprs.pop();
            ArithExpr left = (ArithExpr) exprs.pop();
            exprs.push(ctx.mkLe(left, right));
        }

        @Override
        public void visit(Minus minus) {
            exprs.push(ctx.mkUnaryMinus((ArithExpr) exprs.pop()));
        }

        @Override
        public void visit(Not not) {
            exprs.push(ctx.mkNot((BoolExpr) exprs.pop()));
        }

        @Override
        public void visit(IntConst intConst) {
            exprs.push(ctx.mkInt(intConst.getValue()));
        }

        @Override
        public void visit(BoolConst boolConst) {
            exprs.push(ctx.mkBool(boolConst.getValue()));
        }

        @Override
        public void visit(TestInstance testInstance) {
            processVariable(ctx, testInstance);
        }

        @Override
        public void visit(Parameter parameter) {
            processVariable(ctx, parameter);
        }

        @Override
        public void visit(Hole hole) {
            processVariable(ctx, hole);
        }

        @Override
        public void visit(ITE ite) {
            Expr elseBranch = exprs.pop();
            Expr thenBranch = exprs.pop();
            BoolExpr condition = (BoolExpr) exprs.pop();
            exprs.push(ctx.mkITE(condition, thenBranch, elseBranch));
        }

        @Override
        public void visit(Selector selector) {
            processVariable(ctx, selector);
        }

        @Override
        public void visit(BranchOutput branchOutput) {
            processVariable(ctx, branchOutput);
        }

        @Override
        public void visit(ProgramOutput programOutput) {
            processVariable(ctx, programOutput);
        }

        @Override
        public void visit(Indexed indexed) {
            processVariable(ctx, indexed);
        }

    }

}