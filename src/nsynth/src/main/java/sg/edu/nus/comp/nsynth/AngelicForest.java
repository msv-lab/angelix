package sg.edu.nus.comp.nsynth;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.IntConst;

import javax.json.*;
import java.io.InputStream;
import java.util.*;

/**
 * Angelic forest is a set of angelic paths for each test
 */
public class AngelicForest {
    private Map<AngelixTest, List<AngelicPath>> paths;

    public AngelicForest(Map<AngelixTest, List<AngelicPath>> paths) {
        this.paths = paths;
    }

    public Map<AngelixTest, List<AngelicPath>> getPaths() {
        return paths;
    }

    public static AngelicForest parse(InputStream inputStream) {
        Map<AngelixTest, List<AngelicPath>> paths = new HashMap<>();
        JsonReader reader = Json.createReader(inputStream);
        JsonObject forestObj = reader.readObject();
        for (String testName : forestObj.keySet()) {
            List<JsonArray> rawPaths = forestObj.getJsonArray(testName).getValuesAs(JsonArray.class);
            List<AngelicPath> pathsForTest = new ArrayList<>();
            Type type = null;
            for (JsonArray rawPath : rawPaths) {
                Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> angelicValues = new HashMap<>();
                for (JsonObject valueObj : rawPath.getValuesAs(JsonObject.class)) {
                    JsonValue angelicVal = valueObj.getJsonObject("value").get("value");
                    Constant angelic;
                    if (angelicVal.getValueType().equals(JsonValue.ValueType.FALSE)) {
                        angelic = BoolConst.FALSE;
                        type = BoolType.TYPE;
                    } else if (angelicVal.getValueType().equals(JsonValue.ValueType.TRUE)) {
                        angelic = BoolConst.TRUE;
                        type = BoolType.TYPE;
                    } else if (angelicVal.getValueType().equals(JsonValue.ValueType.NUMBER)) {
                        angelic = IntConst.of(Integer.parseInt(angelicVal.toString()));
                        type = IntType.TYPE;
                    } else {
                        throw new RuntimeException("Unsupported value in angelic forest");
                    }
                    int instance = Integer.parseInt(valueObj.get("instId").toString());
                    AngelixLocation loc = AngelixLocation.parse(valueObj.get("expression").toString());
                    JsonArray context = valueObj.getJsonArray("context");
                    Map<ProgramVariable, Constant> env = new HashMap<>();
                    for (JsonObject binding : context.getValuesAs(JsonObject.class)) {
                        String variable = binding.get("name").toString();
                        Constant value = IntConst.of(Integer.parseInt(binding.get("value").toString()));
                        env.put(ProgramVariable.mkInt(variable), value);
                    }
                    if (!angelicValues.containsKey(loc)) {
                        angelicValues.put(loc, new HashMap<>());
                    }
                    angelicValues.get(loc).put(instance, new ImmutablePair<>(angelic, env));
                }
                pathsForTest.add(new AngelicPath(angelicValues));
            }
            if (type != null) {
                paths.put(new AngelixTest(testName, type), pathsForTest);
            } else {
                throw new RuntimeException("Inconsistent types in angelic forest");
            }
        }
        return new AngelicForest(paths);
    }

}
