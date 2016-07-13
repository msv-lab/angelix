package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.*;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class VariableMarshaller {

    private int index;

    private HashMap<String, Variable> stringToVariable;
    private HashMap<Variable, String> variableToString;

    public VariableMarshaller() {
        this.index = 0;
        this.stringToVariable = new HashMap<>();
        this.variableToString = new HashMap<>();
    }

    private static String getLiteral(Variable variable) {
        return variable.toString();

//        if (variable instanceof ProgramVariable) {
//            return "v";
//        } else if (variable instanceof Parameter) {
//            return "p";
//        } else if (variable instanceof Hole) {
//            return "h";
//        } else if (variable instanceof Location) {
//            return "l";
//        } else if (variable instanceof ComponentInput) {
//            return "ci";
//        } else if (variable instanceof ComponentOutput) {
//            return "co";
//        } else if (variable instanceof TestInstance) {
//            return "i";
//        } else {
//            return "unknown";
//        }
    }

    public String toString(Variable variable) {
        if (variableToString.containsKey(variable)) {
            return variableToString.get(variable);
        } else {
            index++;
            String repr = "v" + index;//getLiteral(variable) + index;
            variableToString.put(variable, repr);
            stringToVariable.put(repr, variable);
            return repr;
        }
    }

    public Variable toVariable(String string) {
        return stringToVariable.get(string);
    }

    public Set<Variable> getVariables() {
        return variableToString.keySet();
    }

}
