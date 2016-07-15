package sg.edu.nus.comp.nsynth;

import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Sergey Mechtaev on 2/5/2016.
 */
public class TreeBoundedSynthesis {

    private TBSConfig config;

    private Logger logger = LoggerFactory.getLogger(TreeBoundedSynthesis.class);

    private class EncodingResult {
        // branch values tree
        private Map<Variable, List<Variable>> tree;

        // possible choices for each branch
        private Map<Variable, List<Selector>> nodeChoices;

        // selected components
        private Map<Selector, Node> selectedComponent;

        // branch is activated by any of these selectors
        private Map<Variable, List<Selector>> branchDependencies;

        // selectors corresponding to the same component
        private Map<Node, List<Selector>> componentUsage;

        // from forbidden program to corresponding selectors
        // list of lists because at each node there can be several matches that must be disjoined
        private Map<Expression, List<List<Selector>>> forbiddenSelectors;

        private List<Node> clauses;

        public EncodingResult(Map<Variable, List<Variable>> tree,
                              Map<Variable, List<Selector>> nodeChoices,
                              Map<Selector, Node> selectedComponent,
                              Map<Variable, List<Selector>> branchDependencies,
                              Map<Node, List<Selector>> componentUsage,
                              Map<Expression, List<List<Selector>>> forbiddenSelectors,
                              List<Node> clauses) {
            this.tree = tree;
            this.nodeChoices = nodeChoices;
            this.selectedComponent = selectedComponent;
            this.branchDependencies = branchDependencies;
            this.componentUsage = componentUsage;
            this.forbiddenSelectors = forbiddenSelectors;
            this.clauses = clauses;
        }
    }

    private Solver solver;

    // NOTE: now forbidden checks prefixes if they are larger than size
    public TreeBoundedSynthesis(Solver solver, TBSConfig config) {
        this.solver = solver;
        this.config = config;
    }

    public Optional<Pair<Expression, Map<Parameter, Constant>>> synthesize(List<? extends TestCase> testSuite,

                                                                           Multiset<Node> components) {
        List<Node> uniqueComponents = new ArrayList<>(components.elementSet());
        ExpressionOutput root;
        root = new ExpressionOutput(testSuite.get(0).getOutputType());
        // top level -> current level
        Map<Expression, Expression> initialForbidden =
                config.forbidden.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

        Optional<EncodingResult> result = encodeBranch(root, config.bound, uniqueComponents, initialForbidden);

        if (!result.isPresent()) {
            throw new IllegalArgumentException("wrong synthesis input");
        }
        List<Node> synthesisClauses = new ArrayList<>();
        for (TestCase test : testSuite) {
            for (Node node : result.get().clauses) {
                synthesisClauses.add(node.instantiate(test));
            }
            synthesisClauses.addAll(testToConstraint(test, root));
        }

        for (Map.Entry<Variable, List<Selector>> entry : result.get().nodeChoices.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Node precondition;
                if (result.get().branchDependencies.containsKey(entry.getKey())) {
                    precondition = Node.disjunction(result.get().branchDependencies.get(entry.getKey()));
                } else {
                    precondition = BoolConst.TRUE;
                }
                synthesisClauses.add(new Impl(precondition, Node.disjunction(entry.getValue())));
            }
        }
        for (List<List<Selector>> selectors : result.get().forbiddenSelectors.values()) {
            if (!selectors.isEmpty()) {
                synthesisClauses.add(
                        Node.disjunction(selectors.stream().map(l ->
                                Node.conjunction(l.stream().map(Not::new).collect(Collectors.toList()))).collect(Collectors.toList())));
            }
        }
        if (config.uniqueUsage) {
            for (Node component : uniqueComponents) {
                if (result.get().componentUsage.containsKey(component)) {
                    synthesisClauses.addAll(Cardinality.SortingNetwork.atMostK(components.count(component),
                            result.get().componentUsage.get(component)));
                }
            }
        }

        Optional<Map<Variable, Constant>> solverResult = solver.sat(synthesisClauses);
        if (solverResult.isPresent()) {
            Pair<Expression, Map<Parameter, Constant>> decoded = decode(solverResult.get(), root, result.get());
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

    private Optional<EncodingResult> encodeBranch(Variable output, int size, List<Node> components, Map<Expression, Expression> forbidden) {
        // Local results:
        List<Selector> currentChoices = new ArrayList<>();
        Map<Selector, Node> selectedComponent = new HashMap<>();
        Map<Variable, List<Selector>> branchDependencies = new HashMap<>();
        Map<Node, List<Selector>> componentUsage = new HashMap<>();

        List<Node> clauses = new ArrayList<>();

        List<Node> relevantComponents = new ArrayList<>(components);
        relevantComponents.removeIf(c -> !TypeInference.typeOf(c).equals(output.getType()));
        List<Node> leafComponents = new ArrayList<>(relevantComponents);
        leafComponents.removeIf(c -> !Traverse.collectByType(c, Hole.class).isEmpty());
        List<Node> functionComponents = new ArrayList<>(relevantComponents);
        functionComponents.removeIf(c -> Traverse.collectByType(c, Hole.class).isEmpty());

        Set<Expression> localForbidden = new HashSet<>(forbidden.values());
        // mapping from current level to selectors
        Map<Expression, List<Selector>> localForbiddenSelectors = new HashMap<>();
        for (Expression expression : localForbidden) {
            localForbiddenSelectors.put(expression, new ArrayList<>());
        }
        Map<Expression, List<Selector>> localForbiddenLeavesSelectors = new HashMap<>();
        Map<Expression, List<List<Selector>>> globalForbiddenResult = new HashMap<>();

        for (Node component : leafComponents) {
            Selector selector = new Selector();
            for (Expression expression : localForbidden) {
                if (expression.getRoot().equals(component)) {
                    if (!localForbiddenLeavesSelectors.containsKey(expression)) {
                        localForbiddenLeavesSelectors.put(expression, new ArrayList<>());
                    }
                    localForbiddenLeavesSelectors.get(expression).add(selector);
                }
            }
            clauses.add(new Impl(selector, new Equal(output, component)));
            if (!componentUsage.containsKey(component)) {
                componentUsage.put(component, new ArrayList<>());
            }
            componentUsage.get(component).add(selector);
            selectedComponent.put(selector, component);
            currentChoices.add(selector);
        }

        List<Variable> children = new ArrayList<>();
        // from child branch to its encoding:
        Map<Variable, EncodingResult> subresults = new HashMap<>();

        List<Node> feasibleComponents = new ArrayList<>(functionComponents);

        if (size > 1) {
            Map<Node, Map<Hole, Variable>> branchMatching = new HashMap<>();
            // components dependent of the branch:
            Map<Variable, List<Node>> componentDependencies = new HashMap<>();
            // forbidden for each branch:
            Map<Variable, Map<Expression, Expression>> subnodeForbidden = new HashMap<>();
            // first we need to precompute all required branches and match them with subnodes of forbidden programs:
            for (Node component : functionComponents) {
                Map<Hole, Variable> args = new HashMap<>();
                List<Variable> availableChildren = new ArrayList<>(children);
                for (Hole input : Traverse.collectByType(component, Hole.class)) {
                    Variable child;
                    Optional<Variable> existingChild = availableChildren.stream().filter(o -> o.getType().equals(input.getType())).findFirst();
                    if (existingChild.isPresent()) {
                        child = existingChild.get();
                        availableChildren.remove(child);
                    } else {
                        child = new BranchOutput(input.getType());
                        componentDependencies.put(child, new ArrayList<>());
                    }
                    componentDependencies.get(child).add(component);
                    args.put(input, child);

                    subnodeForbidden.put(child, new HashMap<>());
                    for (Expression local : localForbidden) {
                        if (local.getRoot().equals(component)) {
                            for (Expression global : forbidden.keySet()) {
                                if (forbidden.get(global).equals(local)) {
                                    // NOTE: can be repetitions, but it is OK
                                    subnodeForbidden.get(child).put(global, local.getChildren().get(input));
                                }
                            }
                        }
                    }
                }
                for (Variable variable : args.values()) {
                    if (!children.contains(variable)) {
                        children.add(variable);
                    }
                }
                branchMatching.put(component, args);
            }

            List<Variable> infeasibleChildren = new ArrayList<>();
            // encoding subnodes and removing infeasible children and components:
            for (Variable child : children) {
                Optional<EncodingResult> subresult = encodeBranch(child, size - 1, components, subnodeForbidden.get(child));
                if (!subresult.isPresent()) {
                    feasibleComponents.removeAll(componentDependencies.get(child));
                    infeasibleChildren.add(child);
                } else {
                    subresults.put(child, subresult.get());
                }
            }
            children.removeAll(infeasibleChildren);

            // for all encoded components, creating node constraints:
            for (Node component : feasibleComponents) {
                Selector selector = new Selector();
                Collection<Variable> usedBranches = branchMatching.get(component).values();
                for (Variable child : usedBranches) {
                    if (!branchDependencies.containsKey(child)) {
                        branchDependencies.put(child, new ArrayList<>());
                    }
                    branchDependencies.get(child).add(selector);
                }
                for (Expression expression : localForbidden) {
                    if (expression.getRoot().equals(component)) {
                        localForbiddenSelectors.get(expression).add(selector);
                    }
                }
                clauses.add(new Impl(selector, new Equal(output, Traverse.substitute(component, branchMatching.get(component)))));
                if (!componentUsage.containsKey(component)) {
                    componentUsage.put(component, new ArrayList<>());
                }
                componentUsage.get(component).add(selector);
                selectedComponent.put(selector, component);
                currentChoices.add(selector);
            }

        }

        if (currentChoices.isEmpty()) {
            return Optional.empty();
        }

        Map<Variable, List<Selector>> nodeChoices = new HashMap<>();
        nodeChoices.put(output, currentChoices);
        Map<Variable, List<Variable>> tree = new HashMap<>();
        tree.put(output, new ArrayList<>());
        tree.put(output, children);

        // merging subnodes information:
        for (EncodingResult subresult: subresults.values()) {
            clauses.addAll(subresult.clauses);
            for (Map.Entry<Node, List<Selector>> usage : subresult.componentUsage.entrySet()) {
                if (componentUsage.containsKey(usage.getKey())) {
                    componentUsage.get(usage.getKey()).addAll(usage.getValue());
                } else {
                    componentUsage.put(usage.getKey(), usage.getValue());
                }
            }
            tree.putAll(subresult.tree);
            nodeChoices.putAll(subresult.nodeChoices);
            selectedComponent.putAll(subresult.selectedComponent);
            branchDependencies.putAll(subresult.branchDependencies);
        }

        for (Expression global : forbidden.keySet()) {
            Expression local = forbidden.get(global);
            if (localForbiddenLeavesSelectors.containsKey(local)) {
                globalForbiddenResult.put(global, new ArrayList<>());
                globalForbiddenResult.get(global).add(localForbiddenLeavesSelectors.get(local)); // matching leaves
            } else {
                if (localForbiddenSelectors.get(local).isEmpty()) {
                    globalForbiddenResult.put(global, new ArrayList<>()); //NOTE: even if subnode selectors are not empty
                } else {
                    globalForbiddenResult.put(global, new ArrayList<>());
                    globalForbiddenResult.get(global).add(localForbiddenSelectors.get(local));
                    boolean failed = false;
                    for (Map.Entry<Variable, EncodingResult> entry : subresults.entrySet()) {
                        Map<Expression, List<List<Selector>>> subnodeForbidden = entry.getValue().forbiddenSelectors;
                        if (!subnodeForbidden.containsKey(global)) { // means that it is not matched with local program
                            continue;
                        }
                        if (!subnodeForbidden.get(global).isEmpty()) {
                            globalForbiddenResult.get(global).addAll(subnodeForbidden.get(global));
                        } else {
                            failed = true;
                            break;
                        }
                    }
                    if (failed) {
                        globalForbiddenResult.put(global, new ArrayList<>()); //erasing
                    }
                }
            }
        }

        return Optional.of(new EncodingResult(tree, nodeChoices, selectedComponent, branchDependencies, componentUsage, globalForbiddenResult, clauses));
    }

    private Pair<Expression, Map<Parameter, Constant>> decode(Map<Variable, Constant> assignment,
                                                              Variable root,
                                                              EncodingResult result) {
        List<Selector> nodeChoices = result.nodeChoices.get(root);
        Selector choice = nodeChoices.stream().filter(s -> assignment.get(s).equals(BoolConst.TRUE)).findFirst().get();
        Node component = result.selectedComponent.get(choice);
        Map<Parameter, Constant> parameterValuation = new HashMap<>();
        if (component instanceof Parameter) {
            Parameter p = (Parameter) component;
            parameterValuation.put(p, assignment.get(p));
        }

        if (Traverse.collectByType(component, Hole.class).isEmpty()) {
            return new ImmutablePair<>(Expression.leaf(component), parameterValuation);
        }

        Map<Hole, Expression> args = new HashMap<>();
        List<Variable> children = new ArrayList<>(result.tree.get(root));
        for (Hole input : Traverse.collectByType(component, Hole.class)) {
            Variable child = children.stream().filter(o -> o.getType().equals(input.getType())).findFirst().get();
            children.remove(child);
            Pair<Expression, Map<Parameter, Constant>> subresult = decode(assignment, child, result);
            parameterValuation.putAll(subresult.getRight());
            args.put(input, subresult.getLeft());
        }

        return new ImmutablePair<>(Expression.app(component, args), parameterValuation);
    }

}
