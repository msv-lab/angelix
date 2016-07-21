package sg.edu.nus.comp.nsynth.ast.theory;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.*;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class IntConst extends Constant {
    private int value;

    public int getValue() {
        return value;
    }

    public static IntConst of(int value) {
        return new IntConst(value);
    }

    private IntConst(int value) {
        this.value = value;
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


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IntConst))
            return false;
        if (obj == this)
            return true;

        IntConst rhs = (IntConst) obj;
        return new EqualsBuilder().
                append(value, rhs.value).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(value).
                toHashCode();
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    public Type getType() {
        return IntType.TYPE;
    }
}
