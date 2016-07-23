package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.BoolType;
import sg.edu.nus.comp.nsynth.ast.Hole;
import sg.edu.nus.comp.nsynth.ast.IntType;
import sg.edu.nus.comp.nsynth.ast.Node;
import sg.edu.nus.comp.nsynth.ast.theory.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class Simplifier {

    public static Node simplify(Node node) {
        Rewriter rewriter = new Rewriter();
        return rewriter.applyRules(node, simplificationRules);
    }

    private static ArrayList<RewriteRule> simplificationRules;

    static {
        simplificationRules = new ArrayList<>();

        Hole i = new Hole("i", IntType.TYPE, IntConst.class);
        Hole j = new Hole("j", IntType.TYPE, IntConst.class);
        Hole a = new Hole("a", BoolType.TYPE, BoolConst.class);
        Hole b = new Hole("b", BoolType.TYPE, BoolConst.class);

        Function<Map<Hole, Node>, Integer> getI = unifier -> ((IntConst)unifier.get(i)).getValue();
        Function<Map<Hole, Node>, Integer> getJ = unifier -> ((IntConst)unifier.get(j)).getValue();
        Function<Map<Hole, Node>, Boolean> getA = unifier -> ((BoolConst)unifier.get(a)).getValue();
        Function<Map<Hole, Node>, Boolean> getB = unifier -> ((BoolConst)unifier.get(b)).getValue();

        // Evaluation rules:
        simplificationRules.add(new RewriteRule(new Add(i, j), (unused, unifier) ->
                IntConst.of(getI.apply(unifier) + getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Sub(i, j), (unused, unifier) ->
                IntConst.of(getI.apply(unifier) - getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Mult(i, j), (unused, unifier) ->
                IntConst.of(getI.apply(unifier) * getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Div(i, j), (unused, unifier) ->
                IntConst.of(getI.apply(unifier) / getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Greater(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) > getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new GreaterOrEqual(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) >= getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Less(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) < getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new LessOrEqual(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) <= getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Equal(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) == getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new NotEqual(i, j), (unused, unifier) ->
                BoolConst.of(getI.apply(unifier) != getJ.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Minus(i), (unused, unifier) ->
                IntConst.of(- getI.apply(unifier))));

        simplificationRules.add(new RewriteRule(new And(a, b), (unused, unifier) ->
                BoolConst.of(getA.apply(unifier) && getB.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Or(a, b), (unused, unifier) ->
                BoolConst.of(getA.apply(unifier) || getB.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Iff(a, b), (unused, unifier) ->
                BoolConst.of((getA.apply(unifier) && getB.apply(unifier)) ||
                        (!getA.apply(unifier) && !getB.apply(unifier)))));

        simplificationRules.add(new RewriteRule(new Impl(a, b), (unused, unifier) ->
                BoolConst.of(!getA.apply(unifier) || getB.apply(unifier))));

        simplificationRules.add(new RewriteRule(new Not(a), (unused, unifier) ->
                BoolConst.of(!getA.apply(unifier))));

        Hole intHole = new Hole("int", IntType.TYPE, Node.class);
        Hole intHole2 = new Hole("int2", IntType.TYPE, Node.class);
        Hole boolHole = new Hole("bool", BoolType.TYPE, Node.class);
        Hole boolHole2 = new Hole("bool2", BoolType.TYPE, Node.class);

        simplificationRules.add(new RewriteRule(new Add(intHole, IntConst.of(0)),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Add(IntConst.of(0), intHole),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Add(intHole, new Minus(intHole2)),
                RewriteRule.transformInto(new Sub(intHole, intHole2))));

        simplificationRules.add(new RewriteRule(new Add(new Minus(intHole), intHole2),
                RewriteRule.transformInto(new Sub(intHole2, intHole))));

        simplificationRules.add(new RewriteRule(new Sub(intHole, IntConst.of(0)),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Sub(IntConst.of(0), intHole),
                RewriteRule.transformInto(new Minus(intHole))));

        simplificationRules.add(new RewriteRule(new Sub(intHole, intHole),
                RewriteRule.transformInto(IntConst.of(0))));

        simplificationRules.add(new RewriteRule(new Minus(new Minus(intHole)),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Minus(new Sub(intHole, intHole2)),
                RewriteRule.transformInto(new Sub(intHole2, intHole))));

        simplificationRules.add(new RewriteRule(new Sub(intHole, new Minus(intHole2)),
                RewriteRule.transformInto(new Add(intHole, intHole2))));

        simplificationRules.add(new RewriteRule(new Mult(intHole, IntConst.of(1)),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Mult(IntConst.of(1), intHole),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new Div(intHole, IntConst.of(1)),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new And(boolHole, BoolConst.TRUE),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new And(BoolConst.TRUE, boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new And(boolHole, BoolConst.FALSE),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new And(BoolConst.FALSE, boolHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new And(new Not(boolHole), boolHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new And(boolHole, new Not(boolHole)),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new And(boolHole, boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Or(boolHole, BoolConst.FALSE),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Or(BoolConst.FALSE, boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Or(boolHole, BoolConst.TRUE),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Or(BoolConst.TRUE, boolHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Or(new Not(boolHole), boolHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Or(boolHole, new Not(boolHole)),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Or(boolHole, boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Impl(BoolConst.TRUE, boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Impl(BoolConst.FALSE, boolHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Impl(boolHole, BoolConst.TRUE),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Impl(boolHole, BoolConst.FALSE),
                RewriteRule.transformInto(new Not(boolHole))));

        simplificationRules.add(new RewriteRule(new Impl(boolHole, boolHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Impl(new Not(boolHole), new Not(boolHole)),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Impl(new Not(boolHole), boolHole),
                RewriteRule.transformInto(boolHole)));

        simplificationRules.add(new RewriteRule(new Impl(boolHole, new Not(boolHole)),
                RewriteRule.transformInto(new Not(boolHole))));

        simplificationRules.add(new RewriteRule(new Iff(boolHole, boolHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Iff(new Not(boolHole), new Not(boolHole)),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Iff(new Not(boolHole), boolHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new Iff(boolHole, new Not(boolHole)),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new Greater(intHole, intHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new GreaterOrEqual(intHole, intHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Less(intHole, intHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new LessOrEqual(intHole, intHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new Equal(intHole, intHole),
                RewriteRule.transformInto(BoolConst.TRUE)));

        simplificationRules.add(new RewriteRule(new NotEqual(intHole, intHole),
                RewriteRule.transformInto(BoolConst.FALSE)));

        simplificationRules.add(new RewriteRule(new ITE(BoolConst.TRUE, intHole, intHole2),
                RewriteRule.transformInto(intHole)));

        simplificationRules.add(new RewriteRule(new ITE(BoolConst.FALSE, intHole, intHole2),
                RewriteRule.transformInto(intHole2)));
    }

}
