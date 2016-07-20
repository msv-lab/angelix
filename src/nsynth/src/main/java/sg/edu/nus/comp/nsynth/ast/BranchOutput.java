package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 2/5/2016.
 *
 * Used by bounded synthesis. Defines physical equality. Test-instantiated
 */
public class BranchOutput extends Variable {

    private Type type;

    public Type getType() {
        return type;
    }

    @Override
    public boolean isTestInstantiable() {
        return true;
    }

    @Override
    public boolean isStatementInstantiable() {
        return true;
    }

    @Override
    public boolean isExecutionInstantiable() {
        return true;
    }

    public BranchOutput(Type type) {
        this.type = type;
        objectCounter = classCounter;
        classCounter++;
    }

    @Override
    public void accept(BottomUpVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(BottomUpMemoVisitor visitor) {
        if (visitor.alreadyVisited(this)) {
            visitor.visitAgain(this);
        } else {
            visitor.visit(this);
        }
    }

    private static int classCounter = 0;
    private final int objectCounter;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BranchOutput))
            return false;
        if (obj == this)
            return true;

        BranchOutput rhs = (BranchOutput) obj;
        return new EqualsBuilder().
                append(objectCounter, rhs.objectCounter).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(objectCounter).
                toHashCode();
    }


    @Override
    public String toString() {
        return "Branch" + objectCounter;
    }

}
