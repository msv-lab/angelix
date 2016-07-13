package sg.edu.nus.comp.nsynth.ast.theory;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.Type;

import java.util.ArrayList;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class UIF {
    private String name;
    private Type type;
    private ArrayList<Type> argTypes;

    public UIF(String name, Type type, ArrayList<Type> argTypes) {
        this.name = name;
        this.type = type;
        this.argTypes = argTypes;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public ArrayList<Type> getArgTypes() {
        return argTypes;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UIF))
            return false;
        if (obj == this)
            return true;

        UIF rhs = (UIF) obj;
        return new EqualsBuilder().
                append(name, rhs.name).
                append(type, rhs.type).
                append(argTypes, rhs.argTypes).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(name).
                append(type).
                append(argTypes).
                toHashCode();
    }

    @Override
    public String toString() {
        return name;
    }

}
