package sg.edu.nus.comp.nsynth;

import fj.data.Either;
import org.junit.BeforeClass;
import org.junit.Test;
import sg.edu.nus.comp.nsynth.ast.theory.*;
import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by Sergey Mechtaev on 13/7/2016.
 */
public class TestSortingNetwork {

    private static Solver solver;

    private static Selector a = new Selector();
    private static Selector b = new Selector();
    private static Selector c = new Selector();
    private static Selector d = new Selector();
    private static Selector e = new Selector();

    @BeforeClass
    public static void initSolver() {
        solver = new Z3();
    }

    @Test
    public void testAtMostOne() {
        List<Selector> bits = new ArrayList<>();
        bits.add(a);
        ArrayList<Node> clauses = new ArrayList<>();
        clauses.add(Node.conjunction(bits));
        clauses.addAll(Cardinality.SortingNetwork.atMostK(1, bits));
        Optional<Map<Variable, Constant>> result = solver.sat(clauses);
        assertTrue(result.isPresent());

        bits.add(b);
        bits.add(c);
        clauses.add(Node.conjunction(bits));
        clauses.addAll(Cardinality.SortingNetwork.atMostK(1, bits));
        Optional<Map<Variable, Constant>> result2 = solver.sat(clauses);
        assertFalse(result2.isPresent());
    }

    @Test
    public void testWithPairwise() {
        List<Selector> bits = new ArrayList<>();
        bits.add(a);
        bits.add(b);
        bits.add(c);
        bits.add(d);
        bits.add(e);

        ArrayList<Node> clauses = new ArrayList<>();
        clauses.add(new Not(Node.conjunction(Cardinality.Pairwise.atMostOne(bits))));
        clauses.addAll(Cardinality.SortingNetwork.atMostK(1, bits));
        Optional<Map<Variable, Constant>> result = solver.sat(clauses);
        assertFalse(result.isPresent());
    }

    @Test
    public void testAtMostTwo() {
        List<Selector> bits = new ArrayList<>();
        bits.add(a);
        bits.add(b);
        bits.add(c);
        bits.add(d);

        ArrayList<Node> triples = new ArrayList<>();
        for (Selector bit : bits) {
            List<Selector> newBits = new ArrayList<>(bits);
            newBits.remove(bit);
            triples.add(Node.conjunction(newBits));
        }

        ArrayList<Node> clauses = new ArrayList<>();
        clauses.add(Node.conjunction(triples));
        clauses.addAll(Cardinality.SortingNetwork.atMostK(2, bits));
        Optional<Map<Variable, Constant>> result = solver.sat(clauses);
        assertFalse(result.isPresent());
    }

}
