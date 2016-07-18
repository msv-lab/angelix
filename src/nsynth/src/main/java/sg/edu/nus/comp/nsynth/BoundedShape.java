package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Expression;

import java.util.List;

/**
 * Created by Sergey Mechtaev on 18/7/2016.
 */
public class BoundedShape extends Shape {
    private int bound;

    public BoundedShape(int bound) {
        this.bound = bound;
    }

    public BoundedShape(int bound, List<Expression> forbidden) {
        this.bound = bound;
        this.forbidden = forbidden;
    }

    public int getBound() {
        return bound;
    }
}
