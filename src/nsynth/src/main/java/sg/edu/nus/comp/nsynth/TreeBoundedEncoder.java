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

    private final static int SUBSTITUTION_SUBNODE_BOUND = 1;
    private final static int LOGIC_NODE_BOUND = 3;
    private final static int CONDITIONAL_COND_BOUND = 3;
    private final static int CONDITIONAL_ELSE_BOUND = 2;

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

        if (shape instanceof RepairShape && ((RepairShape) shape).getLevel() == CONDITIONAL) {
            result = encodeConditional(root, ((RepairShape) shape).getOriginal(), uniqueComponents, initialForbidden);
        } else {
            result = encodeBranch(root, shape, uniqueComponents, initialForbidden);
        }


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

    private Optional<EncodingInfo> encodeConditional(Variable output, Expression original, List<Node> components, Map<Expression, Expression> forbidden) {
        List<Node> clauses = new ArrayList<>();
        Map<Variable, List<Variable>> tree = new HashMap<>();
        Map<Variable, List<Selector>> nodeChoices = new HashMap<>();
        Map<Selector, Node> selectedComponent = new HashMap<>();
        List<Selector> originalSelectors = new ArrayList<>();
        Map<Variable, List<Selector>> branchDependencies = new HashMap<>();
        Map<Node, List<Selector>> componentUsage = new HashMap<>();
        Map<Expression, List<List<Selector>>> globalForbiddenResult = new HashMap<>(); //FIXME: unsupported

        if (output.getType().equals(IntType.TYPE)) {
            Selector idChoice = new Selector();
            Selector iteChoice = new Selector();

            BranchOutput condOut = new BranchOutput(BoolType.TYPE);
            BranchOutput thenOut = new BranchOutput(IntType.TYPE);
            BranchOutput elseOut = new BranchOutput(IntType.TYPE);

            //FIXME: forbidden not supported
            EncodingInfo condResult = encodeBranch(condOut, new BoundedShape(CONDITIONAL_COND_BOUND, BoolType.TYPE), components, new HashMap<>()).get();
            EncodingInfo thenResult = encodeBranch(thenOut, new RepairShape(original, EMPTY), components, new HashMap<>()).get();
            EncodingInfo elseResult = encodeBranch(elseOut, new BoundedShape(CONDITIONAL_ELSE_BOUND, IntType.TYPE), components, new HashMap<>()).get();

            List<EncodingInfo> results = new ArrayList<>();
            results.add(condResult);
            results.add(thenResult);
            results.add(elseResult);

            List<Variable> children = new ArrayList<>();
            children.add(condOut);
            children.add(thenOut);
            children.add(elseOut);

            tree.put(output, children);
            tree.putAll(condResult.tree);
            tree.putAll(thenResult.tree);
            tree.putAll(elseResult.tree);

            List<Selector> currentChoices = new ArrayList<>();
            currentChoices.add(idChoice);
            currentChoices.add(iteChoice);

            nodeChoices.put(output, currentChoices);
            nodeChoices.putAll(condResult.nodeChoices);
            nodeChoices.putAll(thenResult.nodeChoices);
            nodeChoices.putAll(elseResult.nodeChoices);

            selectedComponent.put(idChoice, Library.ID(BoolType.TYPE));
            selectedComponent.put(iteChoice, Library.ITE);
            selectedComponent.putAll(condResult.selectedComponent);
            selectedComponent.putAll(thenResult.selectedComponent);
            selectedComponent.putAll(elseResult.selectedComponent);

            originalSelectors.addAll(thenResult.originalSelectors); //NODE: only from then
            originalSelectors.add(idChoice);

            branchDependencies.put(thenOut, currentChoices);
            List<Selector> newChoices = new ArrayList<>();
            newChoices.add(iteChoice);
            branchDependencies.put(elseOut, newChoices);
            branchDependencies.put(condOut, newChoices);
            branchDependencies.putAll(condResult.branchDependencies);
            branchDependencies.putAll(thenResult.branchDependencies);
            branchDependencies.putAll(elseResult.branchDependencies);

            //NOTE: top components are not counted in usage
            for (EncodingInfo result : results) {
                for (Map.Entry<Node, List<Selector>> usage : result.componentUsage.entrySet()) {
                    if (componentUsage.containsKey(usage.getKey())) {
                        componentUsage.get(usage.getKey()).addAll(usage.getValue());
                    } else {
                        componentUsage.put(usage.getKey(), usage.getValue());
                    }
                }
            }

            Map<Hole, Variable> idBranchMatching = new HashMap<>();
            idBranchMatching.put(Library.ID(BoolType.TYPE), thenOut);
            Map<Hole, Variable> iteBranchMatching = new HashMap<>();
            iteBranchMatching.put((Hole) Library.ITE.getCondition(), condOut);
            iteBranchMatching.put((Hole) Library.ITE.getThenBranch(), thenOut);
            iteBranchMatching.put((Hole) Library.ITE.getElseBranch(), elseOut);

            clauses.add(new Impl(idChoice, new Equal(output,
                    Traverse.substitute(Library.ID(BoolType.TYPE), idBranchMatching))));
            clauses.add(new Impl(iteChoice, new Equal(output,
                    Traverse.substitute(Library.ITE, iteBranchMatching))));
            for (EncodingInfo result : results) {
                clauses.addAll(result.clauses);
            }
        } else {
            Selector idChoice = new Selector();
            Selector andChoice = new Selector();
            Selector orChoice = new Selector();

            BranchOutput leftOut = new BranchOutput(BoolType.TYPE);
            BranchOutput rightOut = new BranchOutput(BoolType.TYPE);

            //FIXME: forbidden not supported
            EncodingInfo leftResult = encodeBranch(leftOut, new RepairShape(original, EMPTY), components, new HashMap<>()).get();
            EncodingInfo rightResult = encodeBranch(rightOut, new BoundedShape(LOGIC_NODE_BOUND, BoolType.TYPE), components, new HashMap<>()).get();

            List<EncodingInfo> results = new ArrayList<>();
            results.add(leftResult);
            results.add(rightResult);

            List<Variable> children = new ArrayList<>();
            children.add(leftOut);
            children.add(rightOut);

            tree.put(output, children);
            tree.putAll(leftResult.tree);
            tree.putAll(rightResult.tree);

            List<Selector> currentChoices = new ArrayList<>();
            currentChoices.add(idChoice);
            currentChoices.add(andChoice);
            currentChoices.add(orChoice);

            nodeChoices.put(output, currentChoices);
            nodeChoices.putAll(leftResult.nodeChoices);
            nodeChoices.putAll(rightResult.nodeChoices);

            selectedComponent.put(idChoice, Library.ID(BoolType.TYPE));
            selectedComponent.put(andChoice, Library.AND);
            selectedComponent.put(orChoice, Library.OR);
            selectedComponent.putAll(leftResult.selectedComponent);
            selectedComponent.putAll(rightResult.selectedComponent);

            originalSelectors.addAll(leftResult.originalSelectors); //NODE: only from left
            originalSelectors.add(idChoice);

            branchDependencies.put(leftOut, currentChoices);
            List<Selector> rightChoices = new ArrayList<>();
            rightChoices.add(andChoice);
            rightChoices.add(orChoice);
            branchDependencies.put(rightOut, rightChoices);
            branchDependencies.putAll(leftResult.branchDependencies);
            branchDependencies.putAll(rightResult.branchDependencies);

            //NOTE: top components are not counted in usage
            for (EncodingInfo result : results) {
                for (Map.Entry<Node, List<Selector>> usage : result.componentUsage.entrySet()) {
                    if (componentUsage.containsKey(usage.getKey())) {
                        componentUsage.get(usage.getKey()).addAll(usage.getValue());
                    } else {
                        componentUsage.put(usage.getKey(), usage.getValue());
                    }
                }
            }

            Map<Hole, Variable> idBranchMatching = new HashMap<>();
            idBranchMatching.put(Library.ID(BoolType.TYPE), leftOut);
            Map<Hole, Variable> andBranchMatching = new HashMap<>();
            andBranchMatching.put((Hole) Library.AND.getLeft(), leftOut);
            andBranchMatching.put((Hole) Library.AND.getRight(), rightOut);
            Map<Hole, Variable> orBranchMatching = new HashMap<>();
            orBranchMatching.put((Hole) Library.OR.getLeft(), leftOut);
            orBranchMatching.put((Hole) Library.OR.getRight(), rightOut);

            clauses.add(new Impl(idChoice, new Equal(output,
                    Traverse.substitute(Library.ID(BoolType.TYPE), idBranchMatching))));
            clauses.add(new Impl(andChoice, new Equal(output,
                    Traverse.substitute(Library.AND, andBranchMatching))));
            clauses.add(new Impl(orChoice, new Equal(output,
                    Traverse.substitute(Library.OR, orBranchMatching))));
            for (EncodingInfo result : results) {
                clauses.addAll(result.clauses);
            }
        }
        return Optional.of(new EncodingInfo(tree, nodeChoices, selectedComponent, branchDependencies, componentUsage, globalForbiddenResult, originalSelectors, clauses));
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
                    || (level == SUBSTITUTION)){
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
                            if (level == SUBSTITUTION && Expression.isLeaf(original)) {
                                subshape = new BoundedShape(SUBSTITUTION_SUBNODE_BOUND, child.getType());
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
                //NOTE: preserving the order of children is necessary for decoding
                for (Hole input : Expression.getComponentInputs(component)) {
                    if (!children.contains(args.get(input))) {
                        children.add(args.get(input));
                    }
                }
                branchMatching.put(component, args);
            }

            //FIXME: I am not sure if we can actually get here, maybe when intermediate node needs additional children?
            for (Variable child : children) {
                if (!subnodeShape.containsKey(child)) {
                    assert shape instanceof RepairShape && ((RepairShape) shape).getLevel() == SUBSTITUTION;
                    subnodeShape.put(child, new BoundedShape(SUBSTITUTION_SUBNODE_BOUND, child.getType()));
                }
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
                case SUBSTITUTION:
                    relevantComponents.addAll(components);
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
        Map<Variable, Constant> deinstantiated = new HashMap<>();
        for (Map.Entry<Variable, Constant> entry : assignment.entrySet()) {
            deinstantiated.put((Variable) entry.getKey().deinstantiate(), entry.getValue());
        }
        return decodeAux(deinstantiated, root, result);
    }

    private Pair<Expression, Map<Parameter, Constant>> decodeAux(Map<Variable, Constant> assignment,
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
            Pair<Expression, Map<Parameter, Constant>> subresult = decodeAux(assignment, child, result);
            parameterValuation.putAll(subresult.getRight());
            args.put(input, subresult.getLeft());
        }

        return new ImmutablePair<>(Expression.app(component, args), parameterValuation);
    }

}
