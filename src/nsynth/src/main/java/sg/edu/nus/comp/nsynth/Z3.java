package sg.edu.nus.comp.nsynth;

import com.microsoft.z3.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Z3 wrapper with custom and builtin Fu-Malik algorithm implementations
 */
public class Z3 implements Solver {

    private Logger logger = LoggerFactory.getLogger(Z3.class);

    private Context ctx;

    private Optional<Integer> customFuMalikBound = Optional.empty();

    public Z3() {
        HashMap<String, String> cfg = new HashMap<>();
        cfg.put("model", "true");
        this.ctx = new Context(cfg);
    }

    public void enableCustomMaxsatWithBound(int i){
        customFuMalikBound = Optional.of(i);
    }

    public void disableCustomMaxsat(){
        customFuMalikBound = Optional.empty();
    }

    private BoolExpr toZ3(VariableMarshaller marshaller, Node node) {
        NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
        node.accept(visitor);
        return (BoolExpr)visitor.getExpr();
    }

    @Override
    public Optional<Map<Variable, Constant>> maxsat(List<Node> hard, List<Node> soft) {
        if (customFuMalikBound.isPresent()) {
            return customMaxsat(hard, soft, customFuMalikBound.get());
        } else {
            return standardMaxsat(hard, soft);
        }
    }

    private Optional<Map<Variable, Constant>> standardMaxsat(List<Node> hard, List<Node> soft) {
        com.microsoft.z3.Optimize solver = ctx.mkOptimize();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : hard) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            clause.accept(visitor);
            solver.Add((BoolExpr)visitor.getExpr());
        }
        for (Node assumption : soft) {
            NodeTranslatorVisitor visitor = new NodeTranslatorVisitor(marshaller);
            assumption.accept(visitor);
            solver.AssertSoft((BoolExpr)visitor.getExpr(), 1, "default");
        }

        Status status = solver.Check();
        if (status.equals(Status.SATISFIABLE)) {
            Model model = solver.getModel();
            return Optional.of(getAssignment(model, marshaller));
        } else if (status.equals(Status.UNSATISFIABLE)) {
            return Optional.empty();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private Optional<Map<Variable, Constant>> customMaxsat(List<Node> hard, List<Node> soft, int bound) {
        com.microsoft.z3.Solver solver = ctx.mkSolver();
        VariableMarshaller marshaller = new VariableMarshaller();

        // adding hard constraints
        for (Node clause : hard) {
            solver.add(toZ3(marshaller, clause));
        }

        // soft * aux
        List<Pair<Node, Selector>> relaxedSoft = soft.stream().map(s ->
                new ImmutablePair<>(s, new Selector())).collect(Collectors.toList());

        int iteration = 0;

        while (true) {
            iteration++;

            // adding relaxed soft constraints
            for (Pair<Node, Selector> pair : relaxedSoft) {
                Node relax = new Or(pair.getLeft(), pair.getRight());
                solver.add(toZ3(marshaller, relax));
            }

            Map<Selector, BoolExpr> auxToSwitches = new HashMap<>();
            ArrayList<BoolExpr> assumptionExprs = new ArrayList<>();
            for (Pair<Node, Selector> pair : relaxedSoft) {
                Node notAux = new Not(pair.getRight());
                BoolExpr assumption = toZ3(marshaller, notAux);
                auxToSwitches.put(pair.getRight(), assumption);
                assumptionExprs.add(assumption);
            }
            BoolExpr[] assumptionArray = assumptionExprs.toArray(new BoolExpr[assumptionExprs.size()]);

            Status status = solver.check(assumptionArray);

            if (status.equals(Status.SATISFIABLE)) {
                break;
            } else if (iteration > bound) {
                return Optional.empty();
            } else if (status.equals(Status.UNSATISFIABLE)) {
                Expr[] unsatCoreArray = solver.getUnsatCore();
                Set<Expr> unsatCore = new HashSet<>(Arrays.asList(unsatCoreArray));
                List<Selector> blocks = new ArrayList<>();
                relaxedSoft = relaxedSoft.stream().map(elem -> {
                    if (unsatCore.contains(auxToSwitches.get(elem.getRight()))) {
                        Selector block = new Selector();
                        blocks.add(block);
                        Node newSoft = new Or(elem.getLeft(), block);
                        Selector newAux = new Selector();
                        return new ImmutablePair<Node, Selector>(newSoft, newAux);
                    } else {
                        return elem;
                    }
                }).collect(Collectors.toList());
                // adding at most 1
                for (Node node : Cardinality.SortingNetwork.atMostK(1, blocks)) {
                    solver.add(toZ3(marshaller, node));
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        Model model = solver.getModel();
        return Optional.of(getAssignment(model, marshaller));
    }

    @Override
    public Optional<Map<Variable, Constant>> sat(List<Node> clauses) {
        com.microsoft.z3.Solver solver = ctx.mkSolver();
        VariableMarshaller marshaller = new VariableMarshaller();
        for (Node clause : clauses) {
            solver.add(toZ3(marshaller, clause));
        }

        Status status = solver.check();
        if (status.equals(Status.SATISFIABLE)) {
            Model model = solver.getModel();
            return Optional.of(getAssignment(model, marshaller));
        } else if (status.equals(Status.UNSATISFIABLE)) {
            return Optional.empty();
        } else {
            throw new UnsupportedOperationException();
        }

    }

    private Map<Variable, Constant> getAssignment(Model model, VariableMarshaller marshaller) {
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

        NodeTranslatorVisitor(VariableMarshaller marshaller) {
            this.marshaller = marshaller;
            this.exprs = new Stack<>();
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
        public void visit(StatementInstance statementInstance) {
            processVariable(ctx, statementInstance);
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
        public void visit(ExpressionOutput expressionOutput) {
            processVariable(ctx, expressionOutput);
        }

        @Override
        public void visit(ExecutionInstance executionInstance) {
            processVariable(ctx, executionInstance);
        }

    }

}
