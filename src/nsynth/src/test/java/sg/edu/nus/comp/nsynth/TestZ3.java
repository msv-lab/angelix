package sg.edu.nus.comp.nsynth;

import fj.data.Either;
import org.junit.*;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sergey Mechtaev on 12/4/2016.
 */
public class TestZ3 {

    private static Solver solver;

    @BeforeClass
    public static void initSolver() {
        solver = new Z3();
    }

    @Test
    public void testEquality() {
        ArrayList<Node> clauses = new ArrayList<>();
        ProgramVariable x = ProgramVariable.mkInt("x");
        ProgramVariable y = ProgramVariable.mkInt("y");
        clauses.add(new Equal(x, y));
        clauses.add(new Equal(x, IntConst.of(5)));
        Optional<Map<Variable, Constant>> result = solver.sat(clauses);
        assertTrue(result.isPresent());
        assertEquals(result.get().get(y), IntConst.of(5));
    }

    @Test
    public void testMaxsat() {
        ArrayList<Node> hard = new ArrayList<>();
        ProgramVariable x = ProgramVariable.mkInt("x");
        ProgramVariable y = ProgramVariable.mkInt("y");
        ProgramVariable a = ProgramVariable.mkBool("a");
        ProgramVariable b = ProgramVariable.mkBool("b");
        hard.add(new Equal(x, IntConst.of(1)));
        hard.add(new Equal(y, IntConst.of(2)));
        hard.add(new Or(a, new Equal(x, y)));
        hard.add(new Or(b, new LessOrEqual(x, y)));
        ArrayList<Node> soft = new ArrayList<>();
        soft.add(new Not(a));
        soft.add(new Not(b));
        Optional<Map<Variable, Constant>> result = solver.maxsat(hard, soft);
        assertTrue(result.isPresent());
        assertTrue(((BoolConst) result.get().get(a)).getValue() || ((BoolConst) result.get().get(b)).getValue());
        assertFalse(((BoolConst) result.get().get(a)).getValue() && ((BoolConst) result.get().get(b)).getValue());
    }

}
