package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class Unifier {

    public static Optional<Map<Hole, Node>> unify(Node pattern, Node node) {
        UnifyVisitor visitor = new UnifyVisitor(node);
        pattern.accept(visitor);
        return visitor.getUnifier();
    }

    private static class UnifyVisitor implements TopDownVisitor {

        private Map<Hole, Node> unifier;

        private Stack<Node> nodeStack;

        private boolean failed;

        private boolean updateUnifier(Hole hole, Node node) {
            if (unifier.containsKey(hole)) {
                return unifier.get(hole).equals(node);
            } else {
                unifier.put(hole, node);
                return true;
            }
        }

        private void processLeaf(Node leaf) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.equals(leaf))
                return;
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        private void processBinaryOp(BinaryOp node) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.getClass().equals(node.getClass())) {
                nodeStack.push(((BinaryOp)right).getRight());
                nodeStack.push(((BinaryOp)right).getLeft());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        private void processUnaryOp(UnaryOp node) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right.getClass().equals(node.getClass())) {
                nodeStack.push(((UnaryOp)right).getArg());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }


        public UnifyVisitor(Node right) {
            this.nodeStack = new Stack<>();
            this.nodeStack.push(right);
            failed = false;
            unifier = new HashMap<>();
        }

        public Optional<Map<Hole, Node>> getUnifier() {
            if (failed) {
                return Optional.empty();
            } else {
                return Optional.of(unifier);
            }
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            if (failed) return;
            processLeaf(programVariable);
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            if (failed) return;
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof UIFApplication &&
                    ((UIFApplication)right).getUIF().equals(UIFApplication.getUIF())) {
                ArrayList<Node> args = ((UIFApplication)right).getArgs();
                Collections.reverse(args);
                for (Node arg : args) {
                    nodeStack.push(arg);
                }
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        @Override
        public void visit(Equal equal) {
            if (failed) return;
            processBinaryOp(equal);
        }

        @Override
        public void visit(NotEqual notEqual) {
            if (failed) return;
            processBinaryOp(notEqual);
        }

        @Override
        public void visit(StatementInstance statementInstance) {
            if (failed) return;
            processLeaf(statementInstance);
        }

        @Override
        public void visit(Add add) {
            if (failed) return;
            processBinaryOp(add);
        }

        @Override
        public void visit(Sub sub) {
            if (failed) return;
            processBinaryOp(sub);
        }

        @Override
        public void visit(Mult mult) {
            if (failed) return;
            processBinaryOp(mult);
        }

        @Override
        public void visit(Div div) {
            if (failed) return;
            processBinaryOp(div);
        }

        @Override
        public void visit(And and) {
            if (failed) return;
            processBinaryOp(and);
        }

        @Override
        public void visit(Or or) {
            if (failed) return;
            processBinaryOp(or);
        }

        @Override
        public void visit(Iff iff) {
            if (failed) return;
            processBinaryOp(iff);
        }

        @Override
        public void visit(Impl impl) {
            if (failed) return;
            processBinaryOp(impl);
        }

        @Override
        public void visit(Greater greater) {
            if (failed) return;
            processBinaryOp(greater);
        }

        @Override
        public void visit(Less less) {
            if (failed) return;
            processBinaryOp(less);
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            if (failed) return;
            processBinaryOp(greaterOrEqual);
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            if (failed) return;
            processBinaryOp(lessOrEqual);
        }

        @Override
        public void visit(Minus minus) {
            if (failed) return;
            processUnaryOp(minus);
        }

        @Override
        public void visit(Not not) {
            if (failed) return;
            processUnaryOp(not);
        }

        @Override
        public void visit(IntConst intConst) {
            if (failed) return;
            processLeaf(intConst);
        }

        @Override
        public void visit(BoolConst boolConst) {
            if (failed) return;
            processLeaf(boolConst);
        }

        @Override
        public void visit(TestInstance testInstance) {
            if (failed) return;
            processLeaf(testInstance);
        }

        @Override
        public void visit(Parameter parameter) {
            if (failed) return;
            processLeaf(parameter);
        }

        @Override
        public void visit(Hole hole) {
            if (failed) return;
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            if (hole.getType().equals(TypeInference.typeOf(right)) &&
                    hole.getSuperclass().isInstance(right) &&
                    updateUnifier(hole, right)) {
                return;
            }
            failed = true;

        }

        @Override
        public void visit(ITE ite) {
            if (nodeStack.isEmpty()) {
                failed = true;
                return;
            }
            Node right = nodeStack.pop();
            if (right instanceof ITE) {
                nodeStack.push(((ITE)right).getElseBranch());
                nodeStack.push(((ITE)right).getThenBranch());
                nodeStack.push(((ITE)right).getCondition());
                return;
            }
            if (right instanceof Hole) {
                throw new UnsupportedOperationException("Right holes are not supported");
            }
            failed = true;
        }

        @Override
        public void visit(Selector selector) {
            if (failed) return;
            processLeaf(selector);
        }

        @Override
        public void visit(BranchOutput branchOutput) {
            if (failed) return;
            processLeaf(branchOutput);
        }

        @Override
        public void visit(ExpressionOutput expressionOutput) {
            if (failed) return;
            processLeaf(expressionOutput);
        }

        @Override
        public void visit(ExecutionInstance executionInstance) {
            if (failed) return;
            processLeaf(executionInstance);
        }

    }



}
