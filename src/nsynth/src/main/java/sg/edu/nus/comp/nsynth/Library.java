package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

/**
 * Created by Sergey Mechtaev on 14/4/2016.
 */
public class Library {

    private static final Hole i = new Hole("i", IntType.TYPE, Node.class);
    private static final Hole j = new Hole("j", IntType.TYPE, Node.class);
    private static final Hole a = new Hole("a", BoolType.TYPE, Node.class);
    private static final Hole b = new Hole("b", BoolType.TYPE, Node.class);

    public static final BinaryOp ADD = new Add(i, j);
    public static final BinaryOp SUB = new Sub(i, j);
    public static final BinaryOp DIV = new Div(i, j);
    public static final BinaryOp MUL = new Mult(i, j);
    public static final UnaryOp MINUS = new Minus(i);

    public static final BinaryOp GT = new Greater(i, j);
    public static final BinaryOp GE = new GreaterOrEqual(i, j);
    public static final BinaryOp LT = new Less(i, j);
    public static final BinaryOp LE = new LessOrEqual(i, j);

    public static final BinaryOp EQ = new Equal(i, j);
    public static final BinaryOp NEQ = new NotEqual(i, j);

    public static final BinaryOp AND = new And(a, b);
    public static final BinaryOp OR = new Or(a, b);
    public static final BinaryOp IMP = new Impl(a, b);
    public static final BinaryOp IFF = new Iff(a, b);
    public static final UnaryOp NOT = new Not(a);

    public static final ITE ITE = new ITE(a, i, j);

    public static Hole ID(Type type) {
        if (type.equals(IntType.TYPE)) {
            return i;
        } else {
            return a;
        }
    }

    public static Node INT_TO_BOOL = new NotEqual(i, IntConst.of(0));
    public static Node ABS = new ITE(new GreaterOrEqual(i, IntConst.of(0)), i, new Minus(i));
    public static Node MAX = new ITE(new GreaterOrEqual(i, j), i, j);
    public static Node MIN = new ITE(new GreaterOrEqual(i, j), j, i);
}
