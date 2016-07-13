package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Programs are either leafs of applications.
 */
public class Program {

    private Component root;

    public Component getRoot() {
        return root;
    }

    private Map<Hole, Program> children;

    public Map<Hole, Program> getChildren() {
        return children;
    }

    private Program(Component root, Map<Hole, Program> children) {
        this.root = root;
        this.children = children;
    }

    public static Program leaf(Component c) {
        return new Program(c, new HashMap<>());
    }

    public static Program app(Component function, Map<Hole, Program> arguments) {
        return new Program(function, arguments);
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
            semantics = root.getSemantics();
        } else {
            Map<Hole, Node> map = children.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
                                                                                        e -> e.getValue().getSemantics()));
            semantics = Traverse.substitute(root.getSemantics(), map);
        }
        return Traverse.substitute(semantics, parameterValuation);
    }

    public List<Component> getLeaves() {
        List<Component> current = new ArrayList<>();
        if (this.isLeaf()) {
            current.add(this.getRoot());
        } else {
            for (Program program : this.getChildren().values()) {
                current.addAll(program.getLeaves());
            }
        }
        return current;
    }

    public Program substitute(Map<Component, Program> mapping) {
        if (this.isLeaf()) {
            if (mapping.containsKey(this.getRoot())) {
                return mapping.get(this.getRoot());
            } else {
                return this;
            }
        } else {
            return Program.app(this.getRoot(), this.getChildren().entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey(),
                            e -> e.getValue().substitute(mapping)
                    )));
        }
    }

    public List<Component> getComponents() {
        List<Component> list = new ArrayList<>();
        list.add(this.getRoot());
        for (Program program : this.getChildren().values()) {
            list.addAll(program.getComponents());
        }
        return list;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Program))
            return false;
        if (obj == this)
            return true;

        Program rhs = (Program) obj;
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
}
