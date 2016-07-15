package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Mechtaev on 26/6/2016.
 */
public class TBSConfig {

    protected int bound;

    public TBSConfig(int bound) {
        this.bound = bound;
    }

    // default options:
    protected List<Expression> forbidden = new ArrayList<>();
    protected boolean uniqueUsage = true;

    public TBSConfig setForbidden(List<Expression> forbidden) {
        this.forbidden = forbidden;
        return this;
    }

    public TBSConfig disableUniqueUsage() {
        this.uniqueUsage = false;
        return this;
    }

}
