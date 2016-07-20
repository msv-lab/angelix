package sg.edu.nus.comp.nsynth.ast;

import sg.edu.nus.comp.nsynth.AngelixLocation;

import java.util.Optional;

/**
 * Created by Sergey Mechtaev on 20/7/2016.
 */
public abstract class Instance extends Variable {
    public abstract Variable getVariable();

    public Optional<AngelixLocation> getStatement() {
        Variable content = this;
        while (content instanceof Instance && !(content instanceof StatementInstance)) {
            content = ((Instance)content).getVariable();
        }
        if (content instanceof StatementInstance) {
            return Optional.of(((StatementInstance) content).getStmtId());
        }
        return Optional.empty();
    }
}
