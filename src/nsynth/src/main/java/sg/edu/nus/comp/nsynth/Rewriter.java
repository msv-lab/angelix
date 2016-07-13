package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class Rewriter {

    private static final int MAX_ITERATIONS = 1000;

    private boolean modified;

    public Node applyRules(Node node, ArrayList<RewriteRule> rules) {
        modified = true;
        int count = 0;
        while (modified) {
            count++;
            if (count > MAX_ITERATIONS) {
                throw new RuntimeException("Rewriter hangs!");
            }
            modified = false;

            node = Traverse.transform(node, n -> {
                for (RewriteRule rule : rules) {
                    Optional<Map<Hole, Node>> unifier = Unifier.unify(rule.getPattern(), n);
                    if (unifier.isPresent()) {
                        modified = true;
                        return rule.apply(n, unifier.get());
                    }
                }
                return n;
            });
        }
        return node;
    }

}
