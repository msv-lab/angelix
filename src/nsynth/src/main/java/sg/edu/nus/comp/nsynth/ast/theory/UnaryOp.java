package sg.edu.nus.comp.nsynth.ast.theory;

import sg.edu.nus.comp.nsynth.ast.Application;
import sg.edu.nus.comp.nsynth.ast.Node;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public abstract class UnaryOp extends Application {
    public abstract Node getArg();
}
