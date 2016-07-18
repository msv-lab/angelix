package sg.edu.nus.comp.nsynth;

/**
 * Repair synthesis levels:
 * EMPTY:        a > b --> a > b            (preserving original expression)
 * OPERATORS:    a > b --> a >= b           (changing all intermediate nodes)
 * LEAVES:       a + b --> a + 1            (changing constants and variables)
 * ARITHMETIC:   1     --> a + 1            (integer leaf expansion)
 * LOGIC:        a > 0 --> a > 0 || b > 0   (orig || new or orig && new)
 * CONDITIONAL:  a     --> a > 0 ? a : b    (if-then-else pattern)
 */
public enum SynthesisLevel {
    EMPTY, OPERATORS, LEAVES, ARITHMETIC, LOGIC, CONDITIONAL
}
