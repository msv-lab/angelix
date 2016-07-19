package sg.edu.nus.comp.nsynth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class TestPatchSynthesis {
    private static PatchSynthesis synthesizer;

    @BeforeClass
    public static void initSolver() {
        synthesizer = new PatchSynthesis();
    }

    private final ProgramVariable x = ProgramVariable.mkInt("x");
    private final ProgramVariable y = ProgramVariable.mkInt("y");

    @Test
    public void testOperator() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);
        components.add(Library.SUB);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(1)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(-1)));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Library.ADD, args);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        Assert.assertEquals(new Sub(x, y), node);
    }

    @Test
    public void testEmpty() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);
        components.add(Library.SUB);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(1)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(-1)));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Library.ADD, args);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.EMPTY);
        assertFalse(result.isPresent());
    }

    @Test
    public void testLeaf() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(IntConst.of(1));
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(3)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(2)));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Library.ADD, args);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result1 =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.OPERATORS);
        assertFalse(result1.isPresent());

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.LEAVES);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        Assert.assertNotEquals(new Add(IntConst.of(1), x), node);
        Assert.assertEquals(new Add(x, IntConst.of(1)), node);
    }

    @Test
    public void testForbiddenLeaf() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(IntConst.of(1));
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(3)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(2)));

        List<Expression> forbidden = new ArrayList<>();
        Map<Hole, Expression> fargs = new HashMap<>();
        fargs.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        fargs.put((Hole) Library.ADD.getRight(), Expression.leaf(IntConst.of(1)));
        forbidden.add(Expression.app(Library.ADD, fargs));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Library.ADD, args);

        PatchSynthesis synthesizerWithForbidden = new PatchSynthesis(forbidden, Optional.of(1));

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizerWithForbidden.repair(original, testSuite, components, SynthesisLevel.LEAVES);
        assertFalse(result.isPresent());
    }

    @Test
    public void testSubstitution() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(IntConst.of(1));
        components.add(Library.ADD);
        components.add(Library.ITE); //NODE: for larger substitutions (true ? x + 1 : y) would be also possible
        components.add(BoolConst.TRUE);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(3)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(2)));

        Expression original = Expression.leaf(x);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.SUBSTITUTION);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        assertTrue(node.equals(new Add(x, IntConst.of(1))) || node.equals(new Add(IntConst.of(1), x)));
    }

    @Test
    public void testLogic() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x, 2);
        components.add(y, 2);
        components.add(Library.GT);
        components.add(Library.EQ);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, BoolConst.TRUE));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, BoolConst.FALSE));

        Map<ProgramVariable, Node> assignment3 = new HashMap<>();
        assignment3.put(x, IntConst.of(2));
        assignment3.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment3, BoolConst.TRUE));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.GT.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.GT.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Library.GT, args);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.CONDITIONAL);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        assertTrue(node.equals(new Or(new Greater(x, y), new Equal(x, y))) ||
                node.equals(new Or(new Greater(x, y), new Equal(y, x))));
    }

    @Test
    public void testConditional() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x, 2);
        components.add(y, 2);
        components.add(Library.GT);
        components.add(Library.SUB);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(2)));

        Expression original = Expression.leaf(x);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.CONDITIONAL);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        Assert.assertEquals(new ITE(new Greater(x, y), x, y), node);
    }

}
