package sg.edu.nus.comp.nsynth.ast;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public abstract class Variable extends Leaf {
    public abstract boolean isTestInstantiable();
    public abstract boolean isStatementInstantiable();
    public abstract boolean isExecutionInstantiable();
}
