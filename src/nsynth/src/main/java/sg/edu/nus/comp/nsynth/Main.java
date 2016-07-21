package sg.edu.nus.comp.nsynth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import fj.P;
import org.apache.commons.lang3.tuple.Pair;
import org.smtlib.IExpr;
import sg.edu.nus.comp.nsynth.ast.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class Main {
    public static void main(String[] args) {
        String angelicForestFilePath = args[0];
        String extractedDirPath = args[1];
        String outputFilePath = args[2];
        String configFile = args[3]; //NODE: it is used only for synthesis level in this synthesizer

        AngelicForest angelicForest = null;

        File angelicForestFile = new File(angelicForestFilePath);
        try {
            FileInputStream fis = new FileInputStream(angelicForestFile);
            angelicForest = AngelicForest.parse(fis);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<File> smtFiles = Arrays.stream(new File(extractedDirPath).listFiles())
                .filter(f -> f.getName().endsWith(".smt2"))
                .collect(Collectors.toList());

        Map<AngelixLocation, Expression> original = new HashMap<>();

        for (File smtFile : smtFiles) {
            String name = smtFile.getName().substring(0, smtFile.getName().length() - 5); //without .smt2
            AngelixLocation loc = AngelixLocation.parse(name);
            Expression expression = null;
            try {
                IExpr expr = SMTParser.parse(new String(Files.readAllBytes(smtFile.toPath())));
                expression = ExpressionConverter.convert(expr);
            } catch (IOException e) {
                e.printStackTrace();
            }
            original.put(loc, expression);
        }

        SynthesisLevel level = extractSynthesisLevel(configFile);

        Pair<AngelicForest, Map<AngelixLocation, Expression>> corrected = TypeCorrector.correct(angelicForest, original);

        AngelixSynthesis synthesizer = new AngelixSynthesis();

        AngelicForest correctedAngelicForest = corrected.getLeft();
        Map<AngelixLocation, Expression> correctedOriginal = corrected.getRight();

        Map<AngelixLocation, Multiset<Node>> components = new HashMap<>();
        for (AngelixLocation loc : angelicForest.getAllLocations()) {
            components.put(loc, selectComponents(correctedOriginal.get(loc), correctedAngelicForest.getContextVariables(loc)));
        }

        System.err.println(correctedOriginal.toString());
        System.err.println(correctedAngelicForest.toString());

        Optional<Map<AngelixLocation, Node>> result =
                synthesizer.repair(correctedOriginal, correctedAngelicForest, components, level);

        if (result.isPresent()) {
            System.out.println("SUCCESS");
            List<String> patch = new ArrayList<>();
            for (AngelixLocation loc : correctedAngelicForest.getAllLocations()) {
                Node orig = correctedOriginal.get(loc).getSemantics();
                Node fixed = result.get().get(loc);
                Node simplified = Simplifier.simplify(fixed);
                System.err.println("Location: " + loc);
                System.err.println("Original: " + orig);
                System.err.println("Fixed: " + fixed);
                System.err.println("Simplified: " + simplified);
                if (!orig.equals(fixed)) {
                    patch.add(loc.toString());
                    patch.add(orig.toString());
                    patch.add(simplified.toString());
                }
                Path file = Paths.get(outputFilePath);
                try {
                    Files.write(file, patch, Charset.forName("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("FAIL");
        }
    }

    /**
     * This is only for backward compatibility
     */
    private static SynthesisLevel extractSynthesisLevel(String configFile) {
        File file = new File(configFile);
        String levelStr = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            JsonReader reader = Json.createReader(fis);
            JsonObject forestObj = reader.readObject();
            levelStr = forestObj.getString("componentLevel");
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (levelStr.equals("alternatives")) {
            return SynthesisLevel.OPERATORS;
        }
        if (levelStr.equals("variables")) {
            return SynthesisLevel.LEAVES;
        }
        if (levelStr.equals("mixed-conditional")) {
            return SynthesisLevel.SUBSTITUTION;
        }
        if (levelStr.equals("conditional-arithmetic")) {
            return SynthesisLevel.CONDITIONAL;
        }
        throw new UnsupportedOperationException();
    }

    private static Multiset<Node> selectComponents(Expression expression, Set<ProgramVariable> contextVariables) {
        Multiset<Node> components = HashMultiset.create();

        components.addAll(expression.getAllComponents());
        components.addAll(contextVariables);

        components.add(Library.AND);
        components.add(Library.OR);
        components.add(Library.NOT);

        components.add(Library.LE);
        components.add(Library.LT);
        components.add(Library.GT);
        components.add(Library.GE);
        components.add(Library.EQ);
        components.add(Library.NEQ);

        components.add(Library.ADD);
        components.add(Library.SUB);
        components.add(Library.MINUS);

        components.add(Library.ID(IntType.TYPE));
        components.add(Library.ID(BoolType.TYPE));

        components.add(Parameter.mkInt("parameter"));

        return components;
    }
}
