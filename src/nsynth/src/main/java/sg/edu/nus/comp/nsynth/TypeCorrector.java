package sg.edu.nus.comp.nsynth;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.smtlib.IExpr;
import sg.edu.nus.comp.nsynth.ast.*;
import sg.edu.nus.comp.nsynth.ast.theory.BoolConst;
import sg.edu.nus.comp.nsynth.ast.theory.IntConst;

import java.util.*;

/**
 * We assume that all variables and value in angelic forest and in the expressions are set to int
 * We need to (1) change all necessary variables and values to bool
 *            (2) wrap inconsistent usage with int-to-bool
 *
 * The only boolean that we can have is the angelic values, and they should be preserved.
 */
public class TypeCorrector {

    public static Pair<AngelicForest, Map<AngelixLocation, Expression>> correct(AngelicForest angelicForest,
                                                                                Map<AngelixLocation, Expression> original) {

        Map<AngelixTest, List<AngelicPath>> correctedPathsForTests = new HashMap<>();
        Map<AngelixLocation, Expression> correctedOriginal = new HashMap<>();

        Set<AngelixLocation> locations = angelicForest.getAllLocations();

        Map<AngelixTest, List<AngelicPath>> pathsForTests = angelicForest.getPaths();
        for (Map.Entry<AngelixTest, List<AngelicPath>> entry : pathsForTests.entrySet()) {
            AngelixTest test = entry.getKey();
            List<AngelicPath> paths = entry.getValue();
            List<AngelicPath> correctedPaths = new ArrayList<>();
            for (AngelicPath path : paths) {
                Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> angelicValues = path.getAngelicValues();
                Map<AngelixLocation, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> correctedAngelicValues = new HashMap<>();
                for (AngelixLocation loc : locations) {
                    Expression orig = original.get(loc);
                    Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>> angelic = angelicValues.get(loc);
                    Pair<Expression, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>> corrected =
                            correctAngelicAndExpression(orig, angelic);
                    correctedOriginal.put(loc, corrected.getLeft());
                    correctedAngelicValues.put(loc, corrected.getRight());
                }
                correctedPaths.add(new AngelicPath(correctedAngelicValues));
            }
            correctedPathsForTests.put(test, correctedPaths);
        }

        AngelicForest correctedAngelicForest = new AngelicForest(correctedPathsForTests);
        return new ImmutablePair<>(correctedAngelicForest, correctedOriginal);
    }

    private static Pair<Expression, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>>>
    correctAngelicAndExpression(Expression original, Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>> angelic) {
        Expression correctedOriginal = null;
        Map<Integer, Pair<Constant, Map<ProgramVariable, Constant>>> correctedAngelic = new HashMap<>();
        for (Map.Entry<Integer, Pair<Constant, Map<ProgramVariable, Constant>>> entry : angelic.entrySet()) {
            int instance = entry.getKey();
            Pair<Constant, Map<ProgramVariable, Constant>> values = entry.getValue();
            Pair<Expression, Pair<Constant, Map<ProgramVariable, Constant>>> corrected = correctValueAndExpression(original, values);
            correctedOriginal = corrected.getLeft();
            correctedAngelic.put(instance, corrected.getRight());
        }
        return new ImmutablePair<>(correctedOriginal, correctedAngelic);
    }

    private static Pair<Expression, Pair<Constant, Map<ProgramVariable, Constant>>>
    correctValueAndExpression(Expression original, Pair<Constant, Map<ProgramVariable, Constant>> values) {
        Constant correctedAngelic = values.getKey();
        Type topType = values.getLeft().getType();
        if (!topType.equals(BoolType.TYPE)) {
            topType = TypeInference.typeOf(original.getRoot());
            if (topType.equals(BoolType.TYPE)) {
                correctedAngelic = intToBool((IntConst) correctedAngelic);
            }
        }
        Pair<Set<ProgramVariable>, Set<ProgramVariable>> pair = inferVariableConstraints(topType, original);
        Set<ProgramVariable> boolVars = pair.getLeft();
        Set<ProgramVariable> intVars = pair.getRight();

        Set<ProgramVariable> onlyBool = new HashSet<>();
        for (ProgramVariable boolVar : boolVars) {
            if (!intVars.contains(boolVar)) {
                onlyBool.add(boolVar);
            }
        }

        Map<ProgramVariable, Constant> correctedEnv = new HashMap<>();
        for (Map.Entry<ProgramVariable, Constant> entry : values.getRight().entrySet()) {
            if (onlyBool.contains(entry.getKey())) {
                correctedEnv.put(ProgramVariable.mkBool(entry.getKey().getName()), intToBool((IntConst) entry.getValue()));
            } else {
                correctedEnv.put(entry.getKey(), entry.getValue());
            }
        }

        Expression withCorrectedVars = switchToBool(original, onlyBool);
        Expression correctedOriginal = correctInconsistent(withCorrectedVars, topType);
        return new ImmutablePair<>(correctedOriginal, new ImmutablePair<>(correctedAngelic, correctedEnv));
    }

    private static BoolConst intToBool(IntConst value) {
        return BoolConst.of(value.getValue() != 0);
    }

    /**
     * @return boolean constraints, integer constraints
     */
    private static Pair<Set<ProgramVariable>, Set<ProgramVariable>> inferVariableConstraints(Type required, Expression e) {
        Set<ProgramVariable> boolVars = new HashSet<>();
        Set<ProgramVariable> intVars = new HashSet<>();
        Node root = e.getRoot();
        if (root instanceof ProgramVariable) {
            if (required.equals(IntType.TYPE)) {
                intVars.add((ProgramVariable) root);
            } else {
                boolVars.add((ProgramVariable) root);
            }
        } else {
            for (Hole input : Expression.getComponentInputs(root)) {
                Pair<Set<ProgramVariable>, Set<ProgramVariable>> pair = inferVariableConstraints(input.getType(), e.getChildren().get(input));
                boolVars.addAll(pair.getLeft());
                intVars.addAll(pair.getRight());
            }
        }
        return new ImmutablePair<>(boolVars, intVars);
    }

    private static Expression switchToBool(Expression expression, Set<ProgramVariable> boolVars) {
        Node root = expression.getRoot();
        if (root instanceof ProgramVariable) {
            if (boolVars.contains(root)) {
                return Expression.leaf(ProgramVariable.mkBool(((ProgramVariable) root).getName()));
            } else {
                return expression;
            }
        } else {
            Map<Hole, Expression> children = new HashMap<>();
            for (Hole input : Expression.getComponentInputs(root)) {
                Expression newChild = switchToBool(expression.getChildren().get(input), boolVars);
                children.put(input, newChild);
            }
            return Expression.app(root, children);
        }
    }

    private static Expression correctInconsistent(Expression expression, Type required) {
        Node root = expression.getRoot();
        Expression withCorrectedChildren;
        if (root instanceof Leaf) {
            withCorrectedChildren = expression;
        } else {
            Map<Hole, Expression> children = new HashMap<>();
            for (Hole input : Expression.getComponentInputs(root)) {
                Expression newChild = correctInconsistent(expression.getChildren().get(input), input.getType());
                children.put(input, newChild);
            }
            withCorrectedChildren = Expression.app(root, children);
        }
        if (TypeInference.typeOf(root).equals(IntType.TYPE) && required.equals(BoolType.TYPE)) {
            Map<Hole, Expression> args = new HashMap<>();
            args.put(Expression.getComponentInputs(Library.INT_TO_BOOL).get(0), withCorrectedChildren);
            return Expression.app(Library.INT_TO_BOOL, args);
        } else {
            return withCorrectedChildren;
        }
    }

}
