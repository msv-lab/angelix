package sg.edu.nus.comp.nsynth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.Add;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.IntConst;
import sg.edu.nus.comp.nsynth.ast.theory.Sub;

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
        components.add(Components.ADD);
        components.add(Components.SUB);

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
        args.put((Hole)Components.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole)Components.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Components.ADD, args);

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizer.repair(original, testSuite, components, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        Assert.assertEquals(new Sub(x, y), node);
    }

    @Test
    public void testLeaf() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(IntConst.of(1));
        components.add(Components.ADD);

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
        args.put((Hole)Components.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole)Components.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Components.ADD, args);

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
        components.add(Components.ADD);

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
        fargs.put((Hole)Components.ADD.getLeft(), Expression.leaf(x));
        fargs.put((Hole)Components.ADD.getRight(), Expression.leaf(IntConst.of(1)));
        forbidden.add(Expression.app(Components.ADD, fargs));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole)Components.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole)Components.ADD.getRight(), Expression.leaf(y));
        Expression original = Expression.app(Components.ADD, args);

        PatchSynthesis synthesizerWithForbidden = new PatchSynthesis(forbidden, Optional.of(1));

        Optional<Pair<Expression, Map<Parameter, Constant>>> result =
                synthesizerWithForbidden.repair(original, testSuite, components, SynthesisLevel.LEAVES);
        assertFalse(result.isPresent());
    }

}
