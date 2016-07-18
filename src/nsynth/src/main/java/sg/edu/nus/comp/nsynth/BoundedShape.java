package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Expression;
import sg.edu.nus.comp.nsynth.ast.Type;
import sg.edu.nus.comp.nsynth.ast.TypeInference;

import java.util.List;

/**
 * Created by Sergey Mechtaev on 18/7/2016.
 */
public class BoundedShape extends Shape {
    private int bound;

    public BoundedShape(int bound, Type outputType) {
        this.bound = bound;
        this.outputType = outputType;
    }

    public BoundedShape(int bound, List<Expression> forbidden) {
        this.bound = bound;
        this.forbidden = forbidden;
        this.outputType = TypeInference.typeOf(forbidden.get(0).getRoot());
    }

    public int getBound() {
        return bound;
    }
}
