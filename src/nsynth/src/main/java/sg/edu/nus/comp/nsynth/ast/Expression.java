package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Programs are either leafs of applications.
 */
public class Expression {

    private Node root;

    public Node getRoot() {
        return root;
    }

    private Map<Hole, Expression> children;

    public Map<Hole, Expression> getChildren() {
        return children;
    }

    private Expression(Node rootComponent, Map<Hole, Expression> children) {
        this.root = rootComponent;
        this.children = children;
    }

    public static Expression leaf(Node c) {
        return new Expression(c, new HashMap<>());
    }

    public static Expression app(Node function, Map<Hole, Expression> arguments) {
        return new Expression(function, arguments);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public Node getSemantics() {
        return getSemantics(new HashMap<>());
    }

    public Node getSemantics(Map<Parameter, Constant> parameterValuation) {
        Node semantics;
        if (isLeaf()) {
            semantics = root;
        } else {
            Map<Hole, Node> map = children.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                                                                        e -> e.getValue().getSemantics()));
            semantics = Traverse.substitute(root, map);
        }
        return Traverse.substitute(semantics, parameterValuation);
    }

    public List<Node> getLeaves() {
        List<Node> current = new ArrayList<>();
        if (this.isLeaf()) {
            current.add(this.getRoot());
        } else {
            for (Expression expression : this.getChildren().values()) {
                current.addAll(expression.getLeaves());
            }
        }
        return current;
    }

    public Expression substitute(Map<Node, Expression> mapping) {
        if (this.isLeaf()) {
            if (mapping.containsKey(this.getRoot())) {
                return mapping.get(this.getRoot());
            } else {
                return this;
            }
        } else {
            return Expression.app(this.getRoot(), this.getChildren().entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey(),
                            e -> e.getValue().substitute(mapping)
                    )));
        }
    }

    public List<Node> getAllComponents() {
        List<Node> list = new ArrayList<>();
        list.add(this.getRoot());
        for (Expression expression : this.getChildren().values()) {
            list.addAll(expression.getAllComponents());
        }
        return list;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Expression))
            return false;
        if (obj == this)
            return true;

        Expression rhs = (Expression) obj;
        return new EqualsBuilder().
                append(root, rhs.children).
                append(root, rhs.children).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(root).
                append(children).
                toHashCode();
    }

    @Override
    public String toString() {
        return this.getSemantics().toString();
    }

    public static boolean isLeaf(Node component) {
        return Traverse.collectByType(component, Hole.class).isEmpty();
    }

    public static List<Hole> getComponentInputs(Node component) {
        return Traverse.collectByType(component, Hole.class);
    }

}
