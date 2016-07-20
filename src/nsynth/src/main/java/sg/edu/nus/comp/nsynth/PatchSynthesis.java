package sg.edu.nus.comp.nsynth;

import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This is a simple implementation of single-line patch synthesis used for testing
 */
public class PatchSynthesis {

    private List<Expression> antiPatterns;
    private Z3 solver;

    public PatchSynthesis() {
        this.antiPatterns = new ArrayList<>();
        this.solver = new Z3();
    }

    public PatchSynthesis(List<Expression> antiPatterns, Optional<Integer> bound) {
        this.antiPatterns = antiPatterns;
        this.solver = new Z3();
        if (bound.isPresent()) {
            this.solver.enableCustomMaxsatWithBound(bound.get());
        }
    }

    public Optional<Pair<Expression, Map<Parameter, Constant>>> repair(Expression original,
                                                                       List<? extends TestCase> testSuite,
                                                                       Multiset<Node> components,
                                                                       SynthesisLevel level) {

        TreeBoundedEncoder encoder = new TreeBoundedEncoder();
        RepairShape shape;

        if (antiPatterns.isEmpty()) {
            shape = new RepairShape(original, level);
        } else {
            shape = new RepairShape(original, level, antiPatterns);
        }

        Triple<Variable, Pair<List<Node>, List<Node>>, TreeBoundedEncoder.EncodingInfo> encoding = encoder.encode(shape, components);

        List<Node> hard = new ArrayList<>();
        for (TestCase test : testSuite) {
            for (Node node : encoding.getMiddle().getLeft()) {
                //FIXME: here we duplicate a lot of constraints:
                hard.add(node.instantiate(test));
            }
            hard.addAll(testToConstraint(test, encoding.getLeft()));
        }

        List<Node> soft = encoding.getMiddle().getRight();
        Optional<Map<Variable, Constant>> solverResult = solver.maxsat(hard, soft);
        if (solverResult.isPresent()) {
            Pair<Expression, Map<Parameter, Constant>> decoded =
                    encoder.decode(solverResult.get(), encoding.getLeft(), encoding.getRight());
            return Optional.of(decoded);
        } else {
            return Optional.empty();
        }
    }

    private List<Node> testToConstraint(TestCase testCase, Variable output) {
        List<Node> clauses = new ArrayList<>();
        List<Node> testClauses = testCase.getConstraints(output);
        for (Node clause : testClauses) {
            clauses.add(clause.instantiate(testCase));
        }
        return clauses;
    }

}


