package sg.edu.nus.comp.nsynth;

import org.omg.PortableServer.LIFESPAN_POLICY_ID;
import org.smtlib.IExpr;
import org.smtlib.IVisitor;
import sg.edu.nus.comp.nsynth.ast.Expression;
import sg.edu.nus.comp.nsynth.ast.Hole;
import sg.edu.nus.comp.nsynth.ast.Node;
import sg.edu.nus.comp.nsynth.ast.ProgramVariable;
import sg.edu.nus.comp.nsynth.ast.theory.BinaryOp;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.IntConst;
import sg.edu.nus.comp.nsynth.ast.theory.UnaryOp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Convert from jSMTLIB to Expression
 */
public class ExpressionConverter {

    private static final Set<String> builtin = new HashSet<>();
    private static final Map<String, BinaryOp> bopBySmt = new HashMap<>();
    private static final Map<String, UnaryOp> uopBySmt = new HashMap<>();

    static {
        builtin.add("+");
        builtin.add("-");
        builtin.add("*");
        builtin.add("/");
        builtin.add(">");
        builtin.add(">=");
        builtin.add("<");
        builtin.add("<=");
        builtin.add("=");
        builtin.add("neq");
        builtin.add("and");
        builtin.add("or");
        builtin.add("ite");
        builtin.add("not");
        builtin.add("true");
        builtin.add("false");

        bopBySmt.put("+", Library.ADD);
        bopBySmt.put("-", Library.SUB);
        bopBySmt.put("*", Library.MUL);
        bopBySmt.put("/", Library.DIV);
        bopBySmt.put(">", Library.GT);
        bopBySmt.put(">=", Library.GE);
        bopBySmt.put("<", Library.LT);
        bopBySmt.put("<=", Library.LE);
        bopBySmt.put("=", Library.EQ);
        bopBySmt.put("neq", Library.NEQ);
        bopBySmt.put("and", Library.AND);
        bopBySmt.put("or", Library.OR);

        uopBySmt.put("-", Library.MINUS);
        uopBySmt.put("not", Library.NOT);
    }

    private static class RepairableTranslator extends IVisitor.NullVisitor<Expression> {
        @Override
        public Expression visit(IExpr.IFcnExpr e) throws VisitorException {
            Map<Hole, Expression> args = new HashMap<>();
            String op = e.head().headSymbol().value();
            if (builtin.contains(op)) {
                switch (e.args().size()) {
                    case 1:
                        UnaryOp component1 = uopBySmt.get(op);
                        args.put((Hole) component1.getArg(), e.args().get(0).accept(this));
                        return Expression.app(component1, args);
                    case 2:
                        BinaryOp component2 = bopBySmt.get(op);
                        args.put((Hole) component2.getLeft(), e.args().get(0).accept(this));
                        args.put((Hole) component2.getRight(), e.args().get(1).accept(this));
                        return Expression.app(component2, args);
                    case 3:
                        assert op.equals("ite");
                        args.put((Hole) Library.ITE.getCondition(), e.args().get(0).accept(this));
                        args.put((Hole) Library.ITE.getThenBranch(), e.args().get(1).accept(this));
                        args.put((Hole) Library.ITE.getElseBranch(), e.args().get(2).accept(this));
                        return Expression.app(Library.ITE, args);
                }
            }
            throw new UnsupportedOperationException("unknown symbol " + op);
        }
        @Override
        public Expression visit(IExpr.ISymbol e) throws VisitorException {
            if (e.value().equals("true")) return Expression.leaf(BoolConst.TRUE);
            if (e.value().equals("false")) return Expression.leaf(BoolConst.FALSE);
            return Expression.leaf(ProgramVariable.mkInt(e.value())); //NOTE: assume that all variables are integer here
        }
        @Override
        public Expression visit(IExpr.INumeral e) throws VisitorException {
            return Expression.leaf(IntConst.of(e.intValue()));
        }
    }

    public static Expression convert(IExpr expr) {
        Expression expression = null;
        try {
             expression = expr.accept(new RepairableTranslator());
        } catch (IVisitor.VisitorException e) {
            e.printStackTrace();
        }
        return expression;
    }
}
