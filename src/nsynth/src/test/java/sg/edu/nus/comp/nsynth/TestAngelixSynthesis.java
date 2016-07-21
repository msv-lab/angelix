package sg.edu.nus.comp.nsynth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.io.*;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sergey Mechtaev on 21/7/2016.
 */
public class TestAngelixSynthesis {
    private static AngelixSynthesis synthesizer;

    @BeforeClass
    public static void initSolver() {
        synthesizer = new AngelixSynthesis();
    }

    private final ProgramVariable x = ProgramVariable.mkInt("x");
    private final ProgramVariable y = ProgramVariable.mkInt("y");
    private final ProgramVariable v = ProgramVariable.mkInt("v");
    private final AngelixLocation loc1 = new AngelixLocation(0, 0, 0, 1);
    private final AngelixLocation loc2 = new AngelixLocation(0, 0, 0, 2);

    @Test
    public void testOperatorSingleline() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);
        components.add(Library.SUB);
        Map<AngelixLocation, Multiset<Node>> componentsMap = new HashMap<>();
        componentsMap.put(loc1, components);

        AngelicForest angelicForest = null;
        try {
            InputStream is = this.getClass().getResourceAsStream("af1.json");
            angelicForest = AngelicForest.parse(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<AngelixLocation, Expression> original = new HashMap<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        original.put(loc1, Expression.app(Library.ADD, args));

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(original, angelicForest, componentsMap, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Node node = result.get().get(loc1);
        Assert.assertEquals(new Sub(x, y), node);
    }

    @Test
    public void testOperatorMultipathSingleline() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(Library.ADD);
        components.add(Library.SUB);
        Map<AngelixLocation, Multiset<Node>> componentsMap = new HashMap<>();
        componentsMap.put(loc1, components);

        AngelicForest angelicForest = null;
        try {
            InputStream is = this.getClass().getResourceAsStream("af2.json");
            angelicForest = AngelicForest.parse(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<AngelixLocation, Expression> original = new HashMap<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.ADD.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.ADD.getRight(), Expression.leaf(y));
        original.put(loc1, Expression.app(Library.ADD, args));

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(original, angelicForest, componentsMap, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Node node = result.get().get(loc1);
        Assert.assertEquals(new Sub(x, y), node);
    }

    @Test
    public void testOperatorMultiline() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(v);
        components.add(IntConst.of(1));
        components.add(Library.GT);
        components.add(Library.GE);
        Map<AngelixLocation, Multiset<Node>> componentsMap = new HashMap<>();
        componentsMap.put(loc1, components);
        componentsMap.put(loc2, components);

        AngelicForest angelicForest = null;
        try {
            InputStream is = this.getClass().getResourceAsStream("af3.json");
            angelicForest = AngelicForest.parse(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<AngelixLocation, Expression> original = new HashMap<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.GT.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.GT.getRight(), Expression.leaf(y));
        original.put(loc1, Expression.app(Library.GT, args));

        Map<Hole, Expression> args2 = new HashMap<>();
        args2.put((Hole) Library.GT.getLeft(), Expression.leaf(v));
        args2.put((Hole) Library.GT.getRight(), Expression.leaf(IntConst.of(1)));
        original.put(loc2, Expression.app(Library.GT, args2));

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(original, angelicForest, componentsMap, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Assert.assertEquals(new GreaterOrEqual(x, y), result.get().get(loc1));
        Assert.assertEquals(new GreaterOrEqual(v, IntConst.of(1)), result.get().get(loc2));
    }

    @Test
    public void testOperatorMultilineMultiinstance() {
        //same as previous but instead of test 3 we use another instance
        Multiset<Node> components = HashMultiset.create();
        components.add(x);
        components.add(y);
        components.add(v);
        components.add(IntConst.of(1));
        components.add(Library.GT);
        components.add(Library.GE);
        Map<AngelixLocation, Multiset<Node>> componentsMap = new HashMap<>();
        componentsMap.put(loc1, components);
        componentsMap.put(loc2, components);

        AngelicForest angelicForest = null;
        try {
            InputStream is = this.getClass().getResourceAsStream("af4.json");
            angelicForest = AngelicForest.parse(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<AngelixLocation, Expression> original = new HashMap<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.GT.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.GT.getRight(), Expression.leaf(y));
        original.put(loc1, Expression.app(Library.GT, args));

        Map<Hole, Expression> args2 = new HashMap<>();
        args2.put((Hole) Library.GT.getLeft(), Expression.leaf(v));
        args2.put((Hole) Library.GT.getRight(), Expression.leaf(IntConst.of(1)));
        original.put(loc2, Expression.app(Library.GT, args2));

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(original, angelicForest, componentsMap, SynthesisLevel.OPERATORS);
        assertTrue(result.isPresent());
        Assert.assertEquals(new GreaterOrEqual(x, y), result.get().get(loc1));
        Assert.assertEquals(new GreaterOrEqual(v, IntConst.of(1)), result.get().get(loc2));
    }

    @Test
    public void testConditional() {
        Multiset<Node> components = HashMultiset.create();
        components.add(x, 2);
        components.add(y);
        components.add(Parameter.mkInt("parameter"));
        components.add(Library.GT);
        components.add(Library.NEQ);
        Map<AngelixLocation, Multiset<Node>> componentsMap = new HashMap<>();
        componentsMap.put(loc1, components);

        AngelicForest angelicForest = null;
        try {
            InputStream is = this.getClass().getResourceAsStream("af5.json");
            angelicForest = AngelicForest.parse(is);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<AngelixLocation, Expression> original = new HashMap<>();
        Map<Hole, Expression> args = new HashMap<>();
        args.put((Hole) Library.GT.getLeft(), Expression.leaf(x));
        args.put((Hole) Library.GT.getRight(), Expression.leaf(y));
        original.put(loc1, Expression.app(Library.GT, args));

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(original, angelicForest, componentsMap, SynthesisLevel.CONDITIONAL);
        assertTrue(result.isPresent());
        Node node = result.get().get(loc1);
        Assert.assertTrue(new Or(new Greater(x, y), new NotEqual(x, IntConst.of(1))).equals(node) ||
                new Or(new Greater(x, y), new NotEqual(IntConst.of(1), x)).equals(node));
    }

}
