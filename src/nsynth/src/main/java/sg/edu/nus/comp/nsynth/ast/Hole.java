package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 8/4/2016.
 */
public class Hole extends Variable {

    private String name;
    private Type type;
    private Class superclass;

    public Class getSuperclass() {
        return superclass;
    }

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
        return false;
    }

    @Override
    public boolean isExecutionInstantiable() {
        return false;
    }

    public Hole(String name, Type type, Class superclass) {
        this.name = name;
        this.type = type;
        this.superclass = superclass;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Hole))
            return false;
        if (obj == this)
            return true;

        Hole rhs = (Hole) obj;
        return new EqualsBuilder().
                append(name, rhs.name).
                append(type, rhs.type).
                append(superclass, rhs.superclass).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(name).
                append(type).
                append(superclass).
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
