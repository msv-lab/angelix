package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Mechtaev on 18/7/2016.
 */
public abstract class Shape {
    protected List<Expression> forbidden = new ArrayList<>();
    public List<Expression> getForbidden() {
        return forbidden;
    }
}
