package sg.edu.nus.comp.nsynth.ast.theory;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import sg.edu.nus.comp.nsynth.ast.*;

import java.util.ArrayList;

/**
 * Created by Sergey Mechtaev on 7/4/2016.
 */
public class UIFApplication extends Application {
    private ArrayList<Node> args;
    private UIF UIF;

    public UIFApplication(UIF UIF, ArrayList<Node> args) {
        this.args = args;
        this.UIF = UIF;
    }

    @Override
    public void accept(BottomUpVisitor visitor) {
        for (Node arg : args) {
            arg.accept(visitor);
        }
        visitor.visit(this);
    }

    @Override
    public void accept(TopDownVisitor visitor) {
        visitor.visit(this);
        for (Node arg : args) {
            arg.accept(visitor);
        }
    }

    @Override
    public void accept(BottomUpMemoVisitor visitor) {
        if (visitor.alreadyVisited(this)) {
            visitor.visitAgain(this);
        } else {
            for (Node arg : args) {
                arg.accept(visitor);
            }
            visitor.visit(this);
        }
    }


    public UIF getUIF() {
        return UIF;
    }

    @Override
    public ArrayList<Node> getArgs() {
        return args;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UIFApplication))
            return false;
        if (obj == this)
            return true;

        UIFApplication rhs = (UIFApplication) obj;
        return new EqualsBuilder().
                append(UIF, rhs.UIF).
                append(args, rhs.args).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(UIF).
                append(args).
                toHashCode();
    }

    @Override
    public String toString() {
        String argsString = "";
        if (args.size() > 0) {
            argsString += args.get(0);
            for (int i=1; i<args.size(); i++) {
                argsString += "," + args.get(i);
            }
        }
        return UIF + "(" + argsString + ")";
    }

}