package sg.edu.nus.comp.nsynth;

/**
 * Repair synthesis levels:
 * EMPTY:        a > b --> a > b            (preserving original expression)
 * OPERATORS:    a > b --> a >= b           (changing all intermediate nodes)
 * LEAVES:       a + b --> a + 1            (changing constants and variables)
 * SUBSTITUTION: 1     --> a + 1            (substitute subtree with a new tree)
 * CONDITIONAL:  a     --> a > 0 ? a : b    (if-then-else pattern for integer)
 *               a > 0 --> a > 0 || b > 0   (orig || new or orig && new for boolean)
 */
public enum SynthesisLevel {
    EMPTY, OPERATORS, LEAVES, SUBSTITUTION, CONDITIONAL
}
