package sg.edu.nus.comp.nsynth;

import com.google.common.collect.Multiset;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.*;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sg.edu.nus.comp.nsynth.SynthesisLevel.*;

/**
 * Created by Sergey Mechtaev on 2/5/2016.
 */
public class TreeBoundedEncoder {

    private Logger logger = LoggerFactory.getLogger(TreeBoundedEncoder.class);

    private boolean uniqueUsage = true;

    protected class EncodingInfo {
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

        //selectors corresponding to original program
        private List<Selector> originalSelectors;

        private List<Node> clauses;

        public EncodingInfo(Map<Variable, List<Variable>> tree,
                            Map<Variable, List<Selector>> nodeChoices,
                            Map<Selector, Node> selectedComponent,
                            Map<Variable, List<Selector>> branchDependencies,
                            Map<Node, List<Selector>> componentUsage,
                            Map<Expression, List<List<Selector>>> forbiddenSelectors,
                            List<Selector> originalSelectors,
                            List<Node> clauses) {
            this.tree = tree;
            this.nodeChoices = nodeChoices;
            this.selectedComponent = selectedComponent;
            this.branchDependencies = branchDependencies;
            this.componentUsage = componentUsage;
            this.forbiddenSelectors = forbiddenSelectors;
            this.originalSelectors = originalSelectors;
            this.clauses = clauses;
        }
    }

    public TreeBoundedEncoder() {
    }

    // NOTE: now forbidden checks prefixes if they are larger than size
    public TreeBoundedEncoder(boolean uniqueUsage) {
        this.uniqueUsage = uniqueUsage;
    }

    /**
     * @return output variable, synthesis constraints and encoding information
     */
    public Triple<Variable, Pair<List<Node>, List<Node>>, EncodingInfo> encode(Shape shape, Multiset<Node> components) {
        List<Node> uniqueComponents = new ArrayList<>(components.elementSet());
        ExpressionOutput root = new ExpressionOutput(shape.getOutputType());
        // top level -> current level
        Map<Expression, Expression> initialForbidden =
                shape.getForbidden().stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

        Optional<EncodingInfo> result;

        result = encodeBranch(root, shape, uniqueComponents, initialForbidden);

        if (!result.isPresent()) {
            throw new RuntimeException("wrong synthesis configuration");
        }

        List<Node> hard = new ArrayList<>();
        List<Node> soft = new ArrayList<>();

        // choice synthesis constraints:
        hard.addAll(result.get().clauses);

        // branch activation constraints:
        for (Map.Entry<Variable, List<Selector>> entry : result.get().nodeChoices.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Node precondition;
                if (result.get().branchDependencies.containsKey(entry.getKey())) {
                    precondition = Node.disjunction(result.get().branchDependencies.get(entry.getKey()));
                } else {
                    precondition = BoolConst.TRUE;
                }
                hard.add(new Impl(precondition, Node.disjunction(entry.getValue())));
            }
        }

        // forbidden constrains:
        for (List<List<Selector>> selectors : result.get().forbiddenSelectors.values()) {
            if (!selectors.isEmpty()) {
                hard.add(
                        Node.disjunction(selectors.stream().map(l ->
                                Node.conjunction(l.stream().map(Not::new).collect(Collectors.toList()))).collect(Collectors.toList())));
            }
        }

        // uniqueness constraints:
        if (uniqueUsage) {
            for (Node component : components.elementSet()) {
                if (result.get().componentUsage.containsKey(component)) {
                    hard.addAll(Cardinality.SortingNetwork.atMostK(components.count(component),
                            result.get().componentUsage.get(component)));
                }
            }
        }

        soft.addAll(result.get().originalSelectors);

        return new ImmutableTriple<>(root, new ImmutablePair<>(hard, soft), result.get());
    }

    private Optional<EncodingInfo> encodeBranch(Variable output, Shape shape, List<Node> components, Map<Expression, Expression> forbidden) {
        // Local results:
        List<Selector> currentChoices = new ArrayList<>();
        Map<Selector, Node> selectedComponent = new HashMap<>();
        Map<Variable, List<Selector>> branchDependencies = new HashMap<>();
        Map<Node, List<Selector>> componentUsage = new HashMap<>();

        List<Node> clauses = new ArrayList<>();

        List<Node> relevantComponents = selectRelevantComponents(components, shape);
        List<Node> leafComponents = new ArrayList<>(relevantComponents);
        leafComponents.removeIf(c -> !Expression.isLeaf(c));
        List<Node> functionComponents = new ArrayList<>(relevantComponents);
        functionComponents.removeIf(Expression::isLeaf);

        Set<Expression> localForbidden = new HashSet<>(forbidden.values());
        // mapping from current level to selectors
        Map<Expression, List<Selector>> localForbiddenSelectors = new HashMap<>();
        for (Expression expression : localForbidden) {
            localForbiddenSelectors.put(expression, new ArrayList<>());
        }
        Map<Expression, List<Selector>> localForbiddenLeavesSelectors = new HashMap<>();
        List<Selector> originalSelectors = new ArrayList<>();

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
            if (shape instanceof RepairShape) {
                if (component.equals(((RepairShape) shape).getOriginal().getRoot())) {
                    originalSelectors.add(selector);
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
        Map<Variable, EncodingInfo> subresults = new HashMap<>();

        List<Node> feasibleComponents = new ArrayList<>(functionComponents);

        boolean encodeSubnodes = false;
        if (shape instanceof BoundedShape) {
            int size = ((BoundedShape) shape).getBound();
            if (size > 1) {
                encodeSubnodes = true;
            }
        } else if (shape instanceof RepairShape) {
            Node original = ((RepairShape) shape).getOriginal().getRoot();
            SynthesisLevel level = ((RepairShape) shape).getLevel();
            if (((level == EMPTY || level == OPERATORS || level == LEAVES) && !Expression.isLeaf(original))
                    || (level == ARITHMETIC)){
                encodeSubnodes = true;
            }
        }

        if (encodeSubnodes) {
            Map<Node, Map<Hole, Variable>> branchMatching = new HashMap<>();
            // components dependent of the branch:
            Map<Variable, List<Node>> componentDependencies = new HashMap<>();
            // forbidden for each branch:
            Map<Variable, Map<Expression, Expression>> subnodeForbidden = new HashMap<>();
            // shape for each branch:
            Map<Variable, Shape> subnodeShape = new HashMap<>();
            // first we need to precompute all required branches and match them with subnodes of forbidden programs:
            for (Node component : functionComponents) {
                Map<Hole, Variable> args = new HashMap<>();
                List<Variable> availableChildren = new ArrayList<>(children);
                for (Hole input : Expression.getComponentInputs(component)) {
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

                    if (!subnodeShape.containsKey(child)) {
                        if (shape instanceof BoundedShape) {
                            int size = ((BoundedShape) shape).getBound();
                            //NOTE: don't care about forbidden in subshapes
                            Shape subshape = new BoundedShape(size - 1, child.getType());
                            subnodeShape.put(child, subshape);
                        } else if (shape instanceof RepairShape) {
                            Node original = ((RepairShape) shape).getOriginal().getRoot();
                            SynthesisLevel level = ((RepairShape) shape).getLevel();
                            Shape subshape;
                            if (level == ARITHMETIC && Expression.isLeaf(original)) {
                                subshape = new BoundedShape(2, child.getType());
                                subnodeShape.put(child, subshape);
                            } else if (component.equals(original)) {
                                Expression originalSubnode = ((RepairShape) shape).getOriginal().getChildren().get(input);
                                subshape = new RepairShape(originalSubnode, level);
                                subnodeShape.put(child, subshape);
                            }
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }

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
                Optional<EncodingInfo> subresult = encodeBranch(child, subnodeShape.get(child), components, subnodeForbidden.get(child));
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
                if (shape instanceof RepairShape) {
                    Node original = ((RepairShape) shape).getOriginal().getRoot();
                    if (original.equals(component)) {
                        originalSelectors.add(selector);
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
        for (EncodingInfo subresult: subresults.values()) {
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
            originalSelectors.addAll(subresult.originalSelectors);
        }

        Map<Expression, List<List<Selector>>> globalForbiddenResult = new HashMap<>();

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
                    for (Map.Entry<Variable, EncodingInfo> entry : subresults.entrySet()) {
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

        return Optional.of(new EncodingInfo(tree, nodeChoices, selectedComponent, branchDependencies, componentUsage, globalForbiddenResult, originalSelectors, clauses));
    }

    /**
     * Select all components that can be relevant at current encoding node for the current synthesis level.
     */
    private List<Node> selectRelevantComponents(List<Node> components, Shape shape) {
        List<Node> relevantComponents = new ArrayList<>();
        if (shape instanceof BoundedShape) {
            relevantComponents.addAll(components);
        } else if (shape instanceof RepairShape){
            RepairShape repairShape = (RepairShape) shape;
            Node root = repairShape.getOriginal().getRoot();
            assert components.contains(root);
            switch (repairShape.getLevel()) {
                case EMPTY:
                    relevantComponents.add(root);
                    break;
                case OPERATORS:
                    if (!Expression.isLeaf(root)) {
                        relevantComponents.addAll(filterComponentsByType(root, components));
                    } else {
                        relevantComponents.add(root);
                    }
                    break;
                case LEAVES:
                    if (Expression.isLeaf(root)) {
                        relevantComponents.addAll(filterComponentsByType(root, components));
                    } else {
                        relevantComponents.add(root);
                    }
                    break;
                case LOGIC:
                    // because we need special encoding for that
                    throw new UnsupportedOperationException();
                case ARITHMETIC:
                    if (Expression.isLeaf(root) && TypeInference.typeOf(root).equals(IntType.TYPE)) {
                        relevantComponents.addAll(components);
                    } else {
                        relevantComponents.add(root);
                    }
                    break;
                case CONDITIONAL:
                    // because we need special encoding for that
                    throw new UnsupportedOperationException();
            }
        } else {
            throw new UnsupportedOperationException();
        }
        relevantComponents.removeIf(c -> !TypeInference.typeOf(c).equals(shape.getOutputType()));
        return relevantComponents;
    }

    /**
     * @return have same inputs types (and order) and output type as pattern
     */
    private List<Node> filterComponentsByType(Node pattern, List<Node> components) {
        List<Node> relevant = new ArrayList<>();
        Type outputType = TypeInference.typeOf(pattern);
        List<Type> args = Expression.getComponentInputs(pattern).stream().map(Hole::getType).collect(Collectors.toList());
        for (Node node : components) {
            if (outputType.equals(TypeInference.typeOf(node)) &&
                    args.equals(Expression.getComponentInputs(node).stream().map(Hole::getType).collect(Collectors.toList()))) {
                relevant.add(node);
            }
        }
        return relevant;
    }

    protected Pair<Expression, Map<Parameter, Constant>> decode(Map<Variable, Constant> assignment,
                                                                Variable root,
                                                                EncodingInfo result) {
        List<Selector> nodeChoices = result.nodeChoices.get(root);
        Selector choice = nodeChoices.stream().filter(s -> assignment.get(s).equals(BoolConst.TRUE)).findFirst().get();
        Node component = result.selectedComponent.get(choice);
        Map<Parameter, Constant> parameterValuation = new HashMap<>();
        if (component instanceof Parameter) {
            Parameter p = (Parameter) component;
            parameterValuation.put(p, assignment.get(p));
        }

        if (Expression.isLeaf(component)) {
            return new ImmutablePair<>(Expression.leaf(component), parameterValuation);
        }

        Map<Hole, Expression> args = new HashMap<>();
        List<Variable> children = new ArrayList<>(result.tree.get(root));
        for (Hole input : Expression.getComponentInputs(component)) {
            Variable child = children.stream().filter(o -> o.getType().equals(input.getType())).findFirst().get();
            children.remove(child);
            Pair<Expression, Map<Parameter, Constant>> subresult = decode(assignment, child, result);
            parameterValuation.putAll(subresult.getRight());
            args.put(input, subresult.getLeft());
        }

        return new ImmutablePair<>(Expression.app(component, args), parameterValuation);
    }

}
