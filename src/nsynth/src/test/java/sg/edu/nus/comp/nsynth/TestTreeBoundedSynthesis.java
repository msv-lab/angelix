package sg.edu.nus.comp.nsynth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.BeforeClass;
import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.Add;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.IntConst;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sergey Mechtaev on 5/5/2016.
 */
public class TestTreeBoundedSynthesis {
    private static Synthesis intSynthesizer;
    private static Synthesis intSynthesizerUnique;
    private static Synthesis boolSynthesizer;
    private static Synthesis boolSynthesizerUnique;

    @BeforeClass
    public static void initSolver() {
        intSynthesizer = new Synthesis(new BoundedShape(2, IntType.TYPE), new TreeBoundedEncoder(false));
        boolSynthesizer = new Synthesis(new BoundedShape(2, BoolType.TYPE), new TreeBoundedEncoder(false));
        intSynthesizerUnique = new Synthesis(new BoundedShape(2, IntType.TYPE), new TreeBoundedEncoder());
        boolSynthesizerUnique = new Synthesis(new BoundedShape(2, BoolType.TYPE), new TreeBoundedEncoder());
    }

    private final ProgramVariable x = ProgramVariable.mkInt("x");
    private final ProgramVariable y = ProgramVariable.mkInt("y");

    @Test
    public void testAddition() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(3)));

        Optional<Pair<Expression, Map<Parameter, Constant>>> result = intSynthesizer.synthesize(testSuite, components);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        assertTrue(node.equals(new Add(x, y)) || node.equals(new Add(y, x)));
    }

    @Test
    public void testForbiddenChoice() {

        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(3)));

        List<Expression> forbidden = new ArrayList<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        forbidden.add(Expression.app(Library.ADD, args));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(2, forbidden), new TreeBoundedEncoder(false));
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        assertEquals(node, new Add(y, x));
    }

    @Test
    public void testForbiddenAll() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(3)));

        List<Expression> forbidden = new ArrayList<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        Map<Hole, Expression> args2 = new HashMap<>();
        args2.put((Hole) Library.ADD.getLeft(), Expression.leaf(y));
        args2.put((Hole) Library.ADD.getRight(), Expression.leaf(x));
        forbidden.add(Expression.app(Library.ADD, args));
        forbidden.add(Expression.app(Library.ADD, args2));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(2, forbidden), new TreeBoundedEncoder(false));
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }

    @Test
    public void testForbiddenParameter() {
        Multiset<Node> components = HashMultiset.create();
        Parameter p = Parameter.mkInt("p");
        components.add(p);
        //components.add(x);
        components.add(Library.ADD);
        components.add(Library.ITE);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(10));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        List<Expression> forbidden = new ArrayList<>();
        forbidden.add(Expression.leaf(p));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(2, forbidden), new TreeBoundedEncoder());
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }

    @Test
    public void testForbiddenDoubleNegation() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(Library.MINUS, 2);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        List<Expression> forbidden = new ArrayList<>();
        forbidden.add(Expression.leaf(x));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.MINUS.getArg(), Expression.leaf(x));
        Map<Hole, Expression> args2 = new HashMap<>();
        args2.put((Hole) Library.MINUS.getArg(), Expression.app(Library.MINUS, args));
        forbidden.add(Expression.app(Library.MINUS, args2));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(3, forbidden), new TreeBoundedEncoder());
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }


    @Test
    public void testForbiddenITE() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(IntConst.of(0));
        components.add(IntConst.of(1));
        components.add(Library.ITE);
        components.add(Library.GT);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(5));
        assignment1.put(y, IntConst.of(7));
        TestCase testCase1 = TestCase.ofAssignment(assignment1, IntConst.of(0));
        testCase1.setId("t1");
        testSuite.add(testCase1);

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(2));
        assignment2.put(y, IntConst.of(1));
        TestCase testCase2 = TestCase.ofAssignment(assignment2, IntConst.of(1));
        testCase2.setId("t2");
        testSuite.add(testCase2);


        Map<Hole, Expression> argsGT = new HashMap<>();
        argsGT.put((Hole) Library.GT.getLeft(), Expression.leaf(x));
        argsGT.put((Hole) Library.GT.getRight(), Expression.leaf(y));
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ITE.getArgs().get(0), Expression.app(Library.GT, argsGT));
        args.put((Hole) Library.ITE.getArgs().get(1), Expression.leaf(IntConst.of(1)));
        args.put((Hole) Library.ITE.getArgs().get(2), Expression.leaf(IntConst.of(0)));

        Map<Hole, Expression> argsGT2 = new HashMap<>();
        argsGT2.put((Hole) Library.GT.getLeft(), Expression.leaf(y));
        argsGT2.put((Hole) Library.GT.getRight(), Expression.leaf(x));
        Map<Hole, Expression> args2 = new HashMap<>();
        args2.put((Hole) Library.ITE.getArgs().get(0), Expression.app(Library.GT, argsGT2));
        args2.put((Hole) Library.ITE.getArgs().get(1), Expression.leaf(IntConst.of(0)));
        args2.put((Hole) Library.ITE.getArgs().get(2), Expression.leaf(IntConst.of(1)));

        List<Expression> forbidden = new ArrayList<>();
        forbidden.add(Expression.app(Library.ITE, args));
        forbidden.add(Expression.app(Library.ITE, args2));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(3, forbidden), new TreeBoundedEncoder());
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }

    @Test
    public void testSimpleITE() {
        Multiset<Node> components = HashMultiset.create();
        components.add(BoolConst.TRUE);
        components.add(IntConst.of(0));
        components.add(IntConst.of(1));
        components.add(Library.ITE);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        TestCase testCase1 = TestCase.ofAssignment(assignment1, IntConst.of(0));
        testCase1.setId("t1");
        testSuite.add(testCase1);

        Synthesis synthesizerWithForbidden = new Synthesis(new BoundedShape(3, IntType.TYPE), new TreeBoundedEncoder());
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertTrue(result.isPresent());
    }


    @Test
    public void testUnique() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Optional<Pair<Expression, Map<Parameter, Constant>>> result = intSynthesizerUnique.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }

    @Test
    public void testUniqueMultiple() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x, 2);
        components.add(Library.ADD, 1);
        components.add(Library.ITE);
        components.add(BoolConst.TRUE);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(x));
        List<Expression> forbidden = new ArrayList<>();
        forbidden.add(Expression.app(Library.ADD, args));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(3, forbidden), new TreeBoundedEncoder());
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertFalse(result.isPresent());
    }


    @Test
    public void testForbiddenNonexistent() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);

        ArrayList<TestCase> testSuite = new ArrayList<>();
        Map<ProgramVariable, Node> assignment1 = new HashMap<>();
        assignment1.put(x, IntConst.of(1));
        assignment1.put(y, IntConst.of(1));
        testSuite.add(TestCase.ofAssignment(assignment1, IntConst.of(2)));

        Map<ProgramVariable, Node> assignment2 = new HashMap<>();
        assignment2.put(x, IntConst.of(1));
        assignment2.put(y, IntConst.of(2));
        testSuite.add(TestCase.ofAssignment(assignment2, IntConst.of(3)));

        List<Expression> forbidden = new ArrayList<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        forbidden.add(Expression.app(Library.SUB, args));

        Synthesis synthesizerWithForbidden =
                new Synthesis(new BoundedShape(2, forbidden), new TreeBoundedEncoder(false));
        Optional<Pair<Expression, Map<Parameter, Constant>>> result = synthesizerWithForbidden.synthesize(testSuite, components);
        assertTrue(result.isPresent());
        Node node = result.get().getLeft().getSemantics(result.get().getRight());
        assertTrue(node.equals(new Add(x, y)) || node.equals(new Add(y, x)));
    }

}
