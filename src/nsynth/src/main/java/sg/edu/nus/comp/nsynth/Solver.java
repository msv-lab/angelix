package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Constant;
import sg.edu.nus.comp.nsynth.ast.Node;
import sg.edu.nus.comp.nsynth.ast.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public interface Solver {
    Optional<Map<Variable, Constant>> maxsat(List<Node> hard, List<Node> soft);
    Optional<Map<Variable, Constant>> sat(List<Node> clauses);
}
