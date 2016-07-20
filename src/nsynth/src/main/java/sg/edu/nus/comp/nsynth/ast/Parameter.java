package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Parameter extends Variable {
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
        return false;
    }

    @Override
    public boolean isStatementInstantiable() {
        return true;
    }

    @Override
    public boolean isExecutionInstantiable() {
        return false;
    }

    public Parameter(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public static Parameter mkInt(String name) {
        return new Parameter(name, IntType.TYPE);
    }

    public static Parameter mkBool(String name) {
        return new Parameter(name, BoolType.TYPE);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Parameter))
            return false;
        if (obj == this)
            return true;

        Parameter rhs = (Parameter) obj;
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
