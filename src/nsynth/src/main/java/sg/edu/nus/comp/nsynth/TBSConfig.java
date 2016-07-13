package sg.edu.nus.comp.nsynth;

import sg.edu.nus.comp.nsynth.ast.Program;
import sg.edu.nus.comp.nsynth.ast.ProgramOutput;
import sg.edu.nus.comp.nsynth.ast.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Sergey Mechtaev on 26/6/2016.
 */
public class TBSConfig {

    protected int bound;

    public TBSConfig(int bound) {
        this.bound = bound;
    }

    // default options:
    protected List<Program> forbidden = new ArrayList<>();
    protected boolean uniqueUsage = true;

    public TBSConfig setForbidden(List<Program> forbidden) {
        this.forbidden = forbidden;
        return this;
    }

    public TBSConfig disableUniqueUsage() {
        this.uniqueUsage = false;
        return this;
    }

}
