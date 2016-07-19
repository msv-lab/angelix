package sg.edu.nus.comp.nsynth;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Created by Sergey Mechtaev on 19/7/2016.
 */
public class AngelixLocation {
    private int beginLine;
    private int beginColumn;
    private int endLine;
    private int endColumn;

    public AngelixLocation(int beginLine, int beginColumn, int endLine, int endColumn) {
        this.beginLine = beginLine;
        this.beginColumn = beginColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public int getBeginLine() {
        return beginLine;
    }

    public int getBeginColumn() {
        return beginColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AngelixLocation))
            return false;
        if (obj == this)
            return true;

        AngelixLocation rhs = (AngelixLocation) obj;
        return new EqualsBuilder().
                append(beginLine, rhs.beginLine).
                append(beginColumn, rhs.beginColumn).
                append(endLine, rhs.endLine).
                append(endColumn, rhs.endColumn).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(beginLine).
                append(beginColumn).
                append(endLine).
                append(endColumn).
                toHashCode();
    }


    public static AngelixLocation parse(String str) {
        String[] s = str.split("-");
        return new AngelixLocation(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]));
    }

    @Override
    public String toString() {
        return beginLine + "-" + beginColumn + "-" + endLine + "-" + endColumn;
    }
}
