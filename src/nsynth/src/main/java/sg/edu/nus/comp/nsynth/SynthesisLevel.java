package sg.edu.nus.comp.nsynth;

/**
 * Repair synthesis levels:
 * OPERATORS:    a > b --> a >= b
 * LEAVES:       a + b --> a + 1
 * ARITHMETIC:   1     --> a + 1
 * LOGIC:        a > 0 --> a > 0 || b > 0
 * CONDITIONAL:  a     --> a > 0 ? a : b
 */
public enum SynthesisLevel {
    OPERATORS, LEAVES, ARITHMETIC, LOGIC, CONDITIONAL
}
