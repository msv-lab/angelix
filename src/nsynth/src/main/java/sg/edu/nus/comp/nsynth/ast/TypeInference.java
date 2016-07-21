package sg.edu.nus.comp.nsynth.ast;

import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class TypeInference {

    public static Type typeOf(Node node) {
        if (node instanceof Leaf) {
            Leaf leaf = (Leaf) node;
            return leaf.getType();
        }

        if (node instanceof Add ||
                node instanceof Sub ||
                node instanceof Mult ||
                node instanceof Div ||
                node instanceof Minus) {
            return IntType.TYPE;
        }

        if (node instanceof And ||
                node instanceof Or ||
                node instanceof Iff ||
                node instanceof Impl ||
                node instanceof Greater ||
                node instanceof Equal ||
                node instanceof NotEqual ||
                node instanceof Less ||
                node instanceof GreaterOrEqual ||
                node instanceof LessOrEqual ||
                node instanceof Not) {
            return BoolType.TYPE;
        }


        if (node instanceof ITE) {
            Node thenBranch = ((ITE) node).getThenBranch();
            return typeOf(thenBranch);
        }

        throw new UnsupportedOperationException();
    }

    public static Type checkType(Node node) throws TypeInferenceException {
        TypeCheckVisitor visitor = new TypeCheckVisitor();
        node.accept(visitor);
        return visitor.getType();
    }

    private static class TypeCheckVisitor implements BottomUpVisitor {

        private Stack<Type> types;

        private boolean typeError;

        TypeCheckVisitor() {
            this.types = new Stack<>();
            this.typeError = false;
        }

        Type getType() throws TypeInferenceException {
            if (types.size() != 1 || typeError) {
                throw new TypeInferenceException();
            }
            return types.peek();
        }

        @Override
        public void visit(ProgramVariable programVariable) {
            if (typeError) return;
            types.push(programVariable.getType());
        }

        @Override
        public void visit(UIFApplication UIFApplication) {
            if (typeError) return;
            ArrayList<Type> argTypes = new ArrayList<>(UIFApplication.getUIF().getArgTypes());
            if (types.size() < argTypes.size()) {
                typeError = true;
                return;
            }
            Collections.reverse(argTypes);
            for (Type argType : argTypes) {
                if (!argType.equals(types.pop())) {
                    typeError = true;
                    return;
                }
            }
            types.push(UIFApplication.getUIF().getType());
        }

        @Override
        public void visit(Equal equal) {
            if (typeError) return;
            if (types.size() < 2 || !(types.pop().equals(types.pop()))) { // polymorphic equality
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(NotEqual notEqual) {
            if (typeError) return;
            if (types.size() < 2 || !(types.pop().equals(types.pop()))) { // polymorphic equality
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(StatementInstance statementInstance) {
            if (typeError) return;
            types.push(statementInstance.getType());
        }

        @Override
        public void visit(Add add) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(IntType.TYPE);
        }

        @Override
        public void visit(Sub sub) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(IntType.TYPE);

        }

        @Override
        public void visit(Mult mult) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(IntType.TYPE);

        }

        @Override
        public void visit(Div div) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(IntType.TYPE);
        }

        @Override
        public void visit(And and) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(BoolType.TYPE) || !types.pop().equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Or or) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(BoolType.TYPE) || !types.pop().equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Iff iff) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(BoolType.TYPE) || !types.pop().equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Impl impl) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(BoolType.TYPE) || !types.pop().equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Greater greater) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Less less) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(GreaterOrEqual greaterOrEqual) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(LessOrEqual lessOrEqual) {
            if (typeError) return;
            if (types.size() < 2 || !types.pop().equals(IntType.TYPE) || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(Minus minus) {
            if (typeError) return;
            if (types.size() < 1 || !types.pop().equals(IntType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(IntType.TYPE);
        }

        @Override
        public void visit(Not not) {
            if (typeError) return;
            if (types.size() < 1 || !types.pop().equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(IntConst intConst) {
            if (typeError) return;
            types.push(IntType.TYPE);
        }

        @Override
        public void visit(BoolConst boolConst) {
            if (typeError) return;
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(TestInstance testInstance) {
            if (typeError) return;
            types.push(typeOf(testInstance.getVariable()));
        }

        @Override
        public void visit(Parameter parameter) {
            if (typeError) return;
            types.push(parameter.getType());
        }

        @Override
        public void visit(Hole hole) {
            if (typeError) return;
            types.push(hole.getType());
        }

        @Override
        public void visit(ITE ite) {
            if (typeError) return;
            if (types.size() < 3) {
                typeError = true;
                return;
            }
            Type second = types.pop();
            Type first = types.pop();
            Type condition = types.pop();
            if (!first.equals(second) || !condition.equals(BoolType.TYPE)) {
                typeError = true;
                return;
            }
            types.push(first);
        }

        @Override
        public void visit(Selector selector) {
            if (typeError) return;
            types.push(BoolType.TYPE);
        }

        @Override
        public void visit(BranchOutput branchOutput) {
            if (typeError) return;
            types.push(branchOutput.getType());
        }

        @Override
        public void visit(ExpressionOutput expressionOutput) {
            if (typeError) return;
            types.push(expressionOutput.getType());
        }

        @Override
        public void visit(ExecutionInstance executionInstance) {
            if (typeError) return;
            types.push(executionInstance.getType());
        }

    }

}
