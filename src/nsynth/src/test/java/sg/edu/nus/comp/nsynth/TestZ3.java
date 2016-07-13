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
    public void testUnsatCore() {
        ArrayList<Node> clauses = new ArrayList<>();
        ProgramVariable x = ProgramVariable.mkInt("x");
        ProgramVariable y = ProgramVariable.mkInt("y");
        ProgramVariable a = ProgramVariable.mkBool("a");
        ProgramVariable b = ProgramVariable.mkBool("b");
        clauses.add(new Equal(x, IntConst.of(1)));
        clauses.add(new Equal(y, IntConst.of(2)));
        clauses.add(new Or(a, new Equal(x, y)));
        clauses.add(new Or(b, new LessOrEqual(x, y)));
        ArrayList<Node> assumptions = new ArrayList<>();
        assumptions.add(new Not(a));
        assumptions.add(new Not(b));
        Optional<Map<Variable, Constant>> unsatCore = solver.maxsat(clauses, assumptions);
//        assertTrue(unsatCore.isRight());
//        assertTrue(unsatCore.right().value().contains(new Not(a)));
//        assertFalse(unsatCore.right().value().contains(new Not(b)));
    }

}
