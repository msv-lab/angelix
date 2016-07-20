package sg.edu.nus.comp.nsynth.ast;

import sg.edu.nus.comp.nsynth.AngelixLocation;
import sg.edu.nus.comp.nsynth.ast.theory.And;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.Or;

import java.util.List;
import java.util.function.Predicate;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public abstract class Node {
    public abstract void accept(BottomUpVisitor visitor);
    public abstract void accept(TopDownVisitor visitor);
    public abstract void accept(BottomUpMemoVisitor visitor);

    /**
     * Rename variables so that the formulas for different tests can be conjoined
     */
    public Node instantiate(TestCase testCase) {
        return Traverse.transform(this, n -> {
            if (n instanceof Variable && ((Variable)n).isTestInstantiable()) {
                return new TestInstance((Variable)n, testCase);
            }
            return n;
        });
    }

    public Node instantiate(AngelixLocation loc) {
        return Traverse.transform(this, n -> {
            if (n instanceof Variable && ((Variable)n).isStatementInstantiable()) {
                return new StatementInstance((Variable)n, loc);
            }
            return n;
        });
    }

    public Node instantiate(int instance) {
        return Traverse.transform(this, n -> {
            if (n instanceof Variable && ((Variable)n).isExecutionInstantiable()) {
                return new ExecutionInstance((Variable)n, instance);
            }
            return n;
        });
    }

    public Node deinstantiate() {
        return Traverse.transform(this, n -> {
            Node current = n;
            while (current instanceof Instance) {
                current = ((Instance) current).getVariable();
            }
            return current;
        });
    }

    private static boolean seen;

    public boolean contains(Node subnode) {
        seen = false;
        Traverse.transform(this, n -> {
            if (n.equals(subnode)) {
                seen = true;
            }
            return n;
        });
        return seen;
    }

    public static Node disjunction(List<? extends Node> clauses) {
        Node node = BoolConst.FALSE;
        for (Node clause : clauses) {
            node = new Or(node, clause);
        }
        return node;
    }

    public static Node conjunction(List<? extends Node> clauses) {
        Node node = BoolConst.TRUE;
        for (Node clause : clauses) {
            node = new And(node, clause);
        }
        return node;
    }

}
