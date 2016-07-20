package sg.edu.nus.comp.nsynth;

import org.smtlib.*;
import org.smtlib.command.C_assert;

import java.io.IOException;

/**
 * Created by Sergey Mechtaev on 21/7/2016.
 */
public class SMTParser {

    /**
     * Extract expression from (assert EXPR)
     */
    public static IExpr parse(String string) {
        SMT smt = new SMT();
        ISource source = smt.smtConfig.smtFactory.createSource(new CharSequenceReader(new java.io.StringReader(string)),null);
        IParser parser = smt.smtConfig.smtFactory.createParser(smt.smtConfig,source);
        ICommand.IScript script = null;
        try {
            script = parser.parseScript();
        } catch (IOException | IParser.ParserException e) {
            e.printStackTrace();
        }
        return ((C_assert)script.commands().get(0)).expr();
    }
}
