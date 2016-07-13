package sg.edu.nus.comp.nsynth;

import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class TestSimplifier {

    @Test
    public void testNoSimplification() {
        Node n = new Add(ProgramVariable.mkInt("x"), IntConst.of(2));
        Node s = Simplifier.simplify(n);
        assertEquals(n, s);
    }

    @Test
    public void testEvaluation() {
        Node n = new Add(IntConst.of(1), IntConst.of(2));
        Node s = Simplifier.simplify(n);
        assertEquals(IntConst.of(3), s);
    }

    @Test
    public void testArithmetic() {
        Parameter a = Parameter.mkInt("a");
        Parameter p = Parameter.mkInt("p");
        Node n = new Add(p, new Sub(new Mult(IntConst.of(1), a), a));
        Node s = Simplifier.simplify(n);
        assertEquals(p, s);
    }

    @Test
    public void testLogic() {
        Parameter a = Parameter.mkBool("a");
        Parameter p = Parameter.mkBool("p");
        Node n = new Or(p, new Impl(BoolConst.TRUE, new And(a, new Not(a))));
        Node s = Simplifier.simplify(n);
        assertEquals(p, s);
    }

    @Test
    public void testMinusElimination() {
        Parameter a = Parameter.mkInt("a");
        Parameter b = Parameter.mkInt("b");
        Node n = new Minus(new Add(new Minus(a), b));
        Node s = Simplifier.simplify(n);
        assertEquals(new Sub(a, b), s);
    }


}
