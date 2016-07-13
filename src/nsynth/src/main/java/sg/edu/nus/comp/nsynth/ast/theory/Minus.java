package sg.edu.nus.comp.nsynth.ast.theory;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Minus extends UnaryOp {
    private Node arg;

    public Node getArg() {
        return arg;
    }

    public Minus(Node arg) {
        this.arg = arg;
    }

    @Override
    public void accept(BottomUpVisitor visitor) {
        arg.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
        arg.accept(visitor);
    }

    @Override
    public void accept(BottomUpMemoVisitor visitor) {
        if (visitor.alreadyVisited(this)) {
            visitor.visitAgain(this);
        } else {
            arg.accept(visitor);
            visitor.visit(this);
        }
    }


    @Override
    public ArrayList<Node> getArgs() {
        ArrayList<Node> result = new ArrayList<>();
        result.add(arg);
        return result;
    }

    @Override
    public String toString() {
        return "-" + arg.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Minus))
            return false;
        if (obj == this)
            return true;

        Minus rhs = (Minus) obj;
        return new EqualsBuilder().
                append(arg, rhs.arg).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(arg).
                toHashCode();
    }


}
