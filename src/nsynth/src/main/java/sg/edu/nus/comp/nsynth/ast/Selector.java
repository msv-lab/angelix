package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.BottomUpVisitor;
import sg.edu.nus.comp.nsynth.ast.TopDownVisitor;
import sg.edu.nus.comp.nsynth.ast.Variable;

/**
 * Created by Sergey Mechtaev on 16/4/2016.
 *
 * Selector is a boolean variable primarily used for assumptions, not test-instantiated
 * Selectors use physical equality
 */
public class Selector extends Variable {

    @Override
    public void accept(BottomUpVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
    }

    private static int classCounter = 0;
    private final int objectCounter;

    public Selector() {
        objectCounter = classCounter;
        classCounter++;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Selector))
            return false;
        if (obj == this)
            return true;

        Selector rhs = (Selector) obj;
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
        return "Selector" + objectCounter;
    }

    @Override
    public Type getType() {
        return BoolType.TYPE;
    }

    @Override
    public boolean isTestInstantiable() {
        return false;
    }

    @Override
    public boolean isStatementInstantiable() {
        //NOTE: conceptually, selectors are statement-instantiated.
        // However, each selector is unique and they are not shared by statements,
        // so it is easier to assume that they are not instantiated
        return false;
    }

    @Override
    public boolean isExecutionInstantiable() {
        return false;
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
