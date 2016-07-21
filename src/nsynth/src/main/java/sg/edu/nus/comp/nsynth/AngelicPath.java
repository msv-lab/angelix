package sg.edu.nus.comp.nsynth;

import org.apache.commons.lang3.tuple.Pair;
import sg.edu.nus.comp.nsynth.ast.Constant;
import sg.edu.nus.comp.nsynth.ast.ProgramVariable;

import java.util.List;
import java.util.Map;

/**
 * expr -> instance -> angelic value * angelic state
 */
public class AngelicPath {
    private Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> angelicValues;

    public AngelicPath(Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> angelicValues) {
        this.angelicValues = angelicValues;
    }

    public Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> getAngelicValues() {
        return angelicValues;
    }

    @Override
    public String toString() {
        return angelicValues.toString();
    }
}
