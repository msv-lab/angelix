package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Hole;
import sg.edu.nus.comp.nsynth.ast.Node;
import sg.edu.nus.comp.nsynth.ast.Traverse;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class RewriteRule {

    public static BiFunction<Node, Map<Hole, Node>, Node> transformInto(Node node) {
        return (unused, unifier) -> Traverse.substitute(node, unifier);
    }

    private Node pattern;

    private BiFunction<Node, Map<Hole, Node>, Node> action;

    public RewriteRule(Node pattern, BiFunction<Node, Map<Hole, Node>, Node> action) {
        this.pattern = pattern;
        this.action = action;
    }

    public Node getPattern() {
        return pattern;
    }

    public Node apply(Node node, Map<Hole, Node> unifier) {
        return action.apply(node, unifier);
    }
}
