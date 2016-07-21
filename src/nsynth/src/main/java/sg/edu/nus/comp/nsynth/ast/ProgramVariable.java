package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class ProgramVariable extends Variable {
    private String name;
    private Type type;

    public String getName() {
        return name;
    }

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

    public ProgramVariable(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public static ProgramVariable mkInt(String name) {
        return new ProgramVariable(name, IntType.TYPE);
    }

    public static ProgramVariable mkBool(String name) {
        return new ProgramVariable(name, BoolType.TYPE);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProgramVariable))
            return false;
        if (obj == this)
            return true;

        ProgramVariable rhs = (ProgramVariable) obj;
        return new EqualsBuilder().
                append(name, rhs.name).
                append(type, rhs.type).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(name).
                append(type).
                toHashCode();
    }

    @Override
    public String toString() {
        return name;
    }

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

}
