package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Expression;

import java.util.List;

/**
 * Created by Sergey Mechtaev on 18/7/2016.
 */
public class RepairShape extends Shape {
    private Expression original;
    private SynthesisLevel level;

    public RepairShape(Expression original, SynthesisLevel level) {
        this.original = original;
        this.level = level;
    }

    public RepairShape(Expression original, SynthesisLevel level, List<Expression> forbidden) {
        this.original = original;
        this.level = level;
        this.forbidden = forbidden;
    }

    public Expression getOriginal() {
        return original;
    }

    public SynthesisLevel getLevel() {
        return level;
    }
}
