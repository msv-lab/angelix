package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.AngelixLocation;

/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class StatementInstance extends Instance {

    private Variable variable;

    public AngelixLocation getStmtId() {
        return stmtId;
    }

    private AngelixLocation stmtId;

    public Type getType() {
        return variable.getType();
    }

    @Override
    public boolean isTestInstantiable() {
        return variable.isTestInstantiable();
    }

    @Override
    public boolean isStatementInstantiable() {
        return false;
    }

    @Override
    public boolean isExecutionInstantiable() {
        return variable.isExecutionInstantiable();
    }

    public StatementInstance(Variable variable, AngelixLocation stmtId) {
        this.variable = variable;
        this.stmtId = stmtId;
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
        if (!(obj instanceof StatementInstance))
            return false;
        if (obj == this)
            return true;

        StatementInstance rhs = (StatementInstance) obj;
        return new EqualsBuilder().
                append(variable, rhs.variable).
                append(stmtId, rhs.stmtId).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(variable).
                append(stmtId).
                toHashCode();
    }


    @Override
    public String toString() {
        return variable + "@" + stmtId;
    }

    @Override
    public Variable getVariable() {
        return variable;
    }
}
