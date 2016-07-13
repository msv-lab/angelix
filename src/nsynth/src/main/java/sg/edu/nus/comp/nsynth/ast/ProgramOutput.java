package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Variable used for CODIS, conflict learning, evaluation. Test-instantiated. Physical equality.
 */
public class ProgramOutput extends Variable {

    private Type type;

    public Type getType() {
        return type;
    }

    @Override
    public boolean isTestInstantiable() {
        return true;
    }

    public ProgramOutput(Type type) {
        this.type = type;
        this.objectCounter = classCounter;
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
        if (!(obj instanceof ProgramOutput))
            return false;
        if (obj == this)
            return true;

        ProgramOutput rhs = (ProgramOutput) obj;
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
        return "Output" + objectCounter;
    }

}
