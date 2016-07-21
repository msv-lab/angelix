package sg.edu.nus.comp.nsynth;

import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.TreeBoundedEncoder.EncodingInfo;
import sg.edu.nus.comp.nsynth.ast.theory.Equal;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class AngelixSynthesis {
    private Z3 solver;

    public AngelixSynthesis() {
        this.solver = new Z3();
        solver.enableCustomMaxsatWithBound(3);
    }

    public Optional<Map<AngelixLocation, Node>> repair(Map<AngelixLocation, Expression> original,
                                                       AngelicForest angelicForest,
                                                       Map<AngelixLocation, Multiset<Node>> components,
                                                       SynthesisLevel level) {
        Set<AngelixLocation> locations = angelicForest.getAllLocations();
        assert locations.stream().filter(loc -> !original.containsKey(loc)).count() == 0; //no locations without original expr

        List<Node> hard = new ArrayList<>();
        List<Node> soft = new ArrayList<>();

        TreeBoundedEncoder encoder = new TreeBoundedEncoder();
        Map<AngelixLocation, Triple<Variable, Pair<List<Node>, List<Node>>, EncodingInfo>> encodings = new HashMap<>();

        for (AngelixLocation loc : locations) {
            Shape shape = new RepairShape(original.get(loc), level);
            encodings.put(loc, encoder.encode(shape, components.get(loc)));
        }

        for (Map.Entry<AngelixTest, List<AngelicPath>> entry : angelicForest.getPaths().entrySet()) {
            AngelixTest test = entry.getKey();
            List<AngelicPath> paths = entry.getValue();
            List<Node> testClauses = new ArrayList<>();
            for (AngelicPath path : paths) {
                Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> angelicForLoc = path.getAngelicValues();
                List<Node> pathClauses = new ArrayList<>();
                for (AngelixLocation loc : locations) {
                    List<Node> locationClauses = new ArrayList<>();
                    for (Map.Entry<Integer, Pair<Constant, Map<ProgramVariable, Constant>>> content : angelicForLoc.get(loc).entrySet()) {
                        int instance = content.getKey();
                        List<Node> synthesisClauses = new ArrayList<>(encodings.get(loc).getMiddle().getLeft());
                        synthesisClauses.addAll(angelicConstraints(encodings.get(loc).getLeft(), content.getValue()));
                        List<Node> instantiated = synthesisClauses.stream().map(n -> n.instantiate(instance)).collect(Collectors.toList());
                        locationClauses.addAll(instantiated);
                    }
                    List<Node> instantiated = locationClauses.stream().map(n -> n.instantiate(loc)).collect(Collectors.toList());
                    pathClauses.addAll(instantiated);
                }
                List<Node> instantiated = pathClauses.stream().map(n -> n.instantiate(test)).collect(Collectors.toList());
                testClauses.add(Node.conjunction(instantiated));
            }
            hard.add(Node.disjunction(testClauses));
        }

        for (AngelixLocation location : locations) {
            soft.addAll(encodings.get(location).getMiddle().getRight());
        }

        Optional<Map<Variable, Constant>> solverResult = solver.maxsat(hard, soft);
        if (solverResult.isPresent()) {
            Map<AngelixLocation, Node> result = new HashMap<>();
            for (AngelixLocation loc : locations) {
                Map<Variable, Constant> relevant = relevantSubmodel(solverResult.get(), loc);
                Pair<Expression, Map<Parameter, Constant>> decoded =
                        encoder.decode(relevant, encodings.get(loc).getLeft(), encodings.get(loc).getRight());
                result.put(loc, decoded.getLeft().getSemantics(decoded.getRight()));
            }
            return Optional.of(result);
        } else {
            return Optional.empty();
        }
    }

    private List<Node> angelicConstraints(Variable variable, Pair<Constant, Map<ProgramVariable, Constant>> value) {
        List<Node> clauses = new ArrayList<>();
        clauses.add(new Equal(variable, value.getLeft()));
        for (Map.Entry<ProgramVariable, Constant> entry : value.getRight().entrySet()) {
            clauses.add(new Equal(entry.getKey(), entry.getValue()));
        }
        return clauses;
    }

    private Map<Variable, Constant> relevantSubmodel(Map<Variable, Constant> model, AngelixLocation loc) {
        Map<Variable, Constant> relevant = new HashMap<>();
        for (Map.Entry<Variable, Constant> entry : model.entrySet()) {
            Variable v = entry.getKey();
            if (v instanceof Instance) {
                Optional<AngelixLocation> current = ((Instance) v).getStatement();
                if (current.isPresent() && !current.get().equals(loc)) {
                    continue;
                }
            }
            relevant.put(v, entry.getValue());
        }
        return relevant;
    }

}
