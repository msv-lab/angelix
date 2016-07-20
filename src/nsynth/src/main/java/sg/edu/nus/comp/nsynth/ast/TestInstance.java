package sg.edu.nus.comp.nsynth.ast;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class TestInstance extends Instance {

    private TestCase test;

    private Variable variable;

    public TestInstance(Variable variable, TestCase test) {
        this.variable = variable;
        this.test = test;
    }

    @Override
    public Variable getVariable() {
        return variable;
    }

    public TestCase getTest() {
        return test;
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
        if (!(obj instanceof TestInstance))
            return false;
        if (obj == this)
            return true;

        TestInstance rhs = (TestInstance) obj;
        return new EqualsBuilder().
                append(variable, rhs.variable).
                append(test, rhs.test).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(variable).
                append(test).
                toHashCode();
    }

    @Override
    public String toString() {
        return "T(" + variable + ")[" + test + "]";
    }


    @Override
    public Type getType() {
        return variable.getType();
    }

    @Override
    public boolean isTestInstantiable() {
        return false;
    }

    @Override
    public boolean isStatementInstantiable() {
        return variable.isStatementInstantiable();
    }

    @Override
    public boolean isExecutionInstantiable() {
        return variable.isExecutionInstantiable();
    }

}
