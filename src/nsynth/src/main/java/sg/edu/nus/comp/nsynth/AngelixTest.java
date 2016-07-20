package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Node;
import sg.edu.nus.comp.nsynth.ast.TestCase;
import sg.edu.nus.comp.nsynth.ast.Type;
import sg.edu.nus.comp.nsynth.ast.Variable;

import java.util.List;

/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class AngelixTest extends TestCase {
    private String name;
    private Type outputType;

    public AngelixTest(String name, Type type) {
        this.name = name;
        this.outputType = type;
    }

    public String getName() {
        return name;
    }

    @Override
    public List<Node> getConstraints(Variable output) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type getOutputType() {
        return outputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AngelixTest that = (AngelixTest) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
