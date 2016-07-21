package sg.edu.nus.comp.nsynth.ast.theory;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Mechtaev on 15/4/2016.
 */
public class ITE extends Application {

    private Node condition;
    private Node thenBranch;
    private Node elseBranch;

    public ITE(Node condition, Node thenBranch, Node elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public Node getCondition() {
        return condition;
    }

    public Node getThenBranch() {
        return thenBranch;
    }

    public Node getElseBranch() {
        return elseBranch;
    }

    @Override
    public void accept(BottomUpVisitor visitor) {
        condition.accept(visitor);
        thenBranch.accept(visitor);
        elseBranch.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
        condition.accept(visitor);
        thenBranch.accept(visitor);
        elseBranch.accept(visitor);
    }

    @Override
    public void accept(BottomUpMemoVisitor visitor) {
        if (visitor.alreadyVisited(this)) {
            visitor.visitAgain(this);
        } else {
            condition.accept(visitor);
            thenBranch.accept(visitor);
            elseBranch.accept(visitor);
            visitor.visit(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ITE))
            return false;
        if (obj == this)
            return true;

        ITE rhs = (ITE) obj;
        return new EqualsBuilder().
                append(condition, rhs.condition).
                append(thenBranch, rhs.thenBranch).
                append(elseBranch, rhs.elseBranch).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(condition).
                append(thenBranch).
                append(elseBranch).
                toHashCode();
    }

    @Override
    public String toString() {
        return "(" + condition.toString() + " ? " + thenBranch.toString() + " : " + elseBranch.toString() + ")";
    }

    @Override
    public List<Node> getArgs() {
        List<Node> result = new ArrayList<>();
        result.add(condition);
        result.add(thenBranch);
        result.add(elseBranch);
        return result;
    }
}
