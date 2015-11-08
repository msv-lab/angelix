# Defect Classes #

Angelix supports the following defect classes:

* if-conditions
* loop-conditions
* assignments
* deletions
* guards

## Expressions ##

Classes "if-conditions", "loop-conditions" and "assignments" correspond to modifications of _repairable_ expressions. Repairable expressions are defined in the following way:

1. Integer and pointer variables, integer and character literals, member expressions are repairable.
2. If x and y are repairable expressions, then so are `x == y`, `x != y`, `x <= y`, `x >= y`, `x > y`, `x < y`, `x + y`, `x - y`, `x * y`, `x / y`, `x || y`, `x && y`, `!x`.
3. An expression is repairable if it can be shown to be repairable on the basis of conditions 1 and 2.

### if-conditions ###

This defect class includes modifications of all repairable if conditions or if whole if condition is not repairable, then all its repairable disjuncts and conjuncts.

### loop-conditions ###

Same as if-conditions, but for while loops and for loops. Note that this defect class can considerably increase the search space and usually requires setting KLEE forks bound (`--klee-max-forks`).

### assignments ###

Modifications of all repairable right-hand-sides of assignments, where an assignment is a binary operation with operator `=` and also an immediate child of a block statement.

## Statements ##

Classes "deletions" and "guards" correspond to statement-level modifications.

### deletions ###

Deletions of all assignments and function calls that are immediate children of a block statement.

### guards ###

Transformations from `S;` to `if (E) { S; }` where `E` is a synthesized expression and `S` is an assignments or a function call and also an immediate child of a block statement.


