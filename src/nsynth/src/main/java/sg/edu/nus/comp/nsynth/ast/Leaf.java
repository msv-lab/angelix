package sg.edu.nus.comp.nsynth.ast;

import sg.edu.nus.comp.nsynth.ast.Node;

/**
 * Created by Sergey Mechtaev on 16/4/2016.
 */
public abstract class Leaf extends Node {
    public abstract Type getType();
}
