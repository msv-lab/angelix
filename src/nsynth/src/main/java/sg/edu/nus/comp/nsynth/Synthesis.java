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
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class Synthesis {

    private TreeBoundedEncoder encoder;
    private Shape shape;
    private Solver solver = new Z3();

    public Synthesis(Shape shape, TreeBoundedEncoder encoder) {
        this.shape = shape;
        this.encoder = encoder;
    }

    public Optional<Pair<Expression, Map<Parameter, Constant>>> synthesize(List<? extends TestCase> testSuite,
                                                                           Multiset<Node> components) {

        Triple<Variable, Pair<List<Node>, List<Node>>, TreeBoundedEncoder.EncodingInfo> encoding = encoder.encode(shape, components);

        List<Node> synthesisClauses = new ArrayList<>();
        for (TestCase test : testSuite) {
            for (Node node : encoding.getMiddle().getLeft()) {
                //FIXME: here we duplicate a lot of constraints:
                synthesisClauses.add(node.instantiate(test));
            }
            synthesisClauses.addAll(testToConstraint(test, encoding.getLeft()));
        }

        Optional<Map<Variable, Constant>> solverResult = solver.sat(synthesisClauses);
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
