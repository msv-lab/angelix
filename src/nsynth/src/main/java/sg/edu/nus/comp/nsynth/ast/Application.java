package sg.edu.nus.comp.nsynth.ast;

import java.util.List;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public abstract class Application extends Node {
    public abstract List<Node> getArgs();
}
