# Synthesis Levels #

Synthesis levels are sets of primitive components that are used to repair buggy program expressions. Angelix provides the following levels:

* alternatives
* integer-constants
* boolean-constants
* variables
* basic-arithmetic
* basic-logic
* basic-inequalities
* extended-arithmetic
* extended-logic
* extended-inequalities
* mixed-conditional
* conditional-arithmetic

## alternatives ##

Additinal components similar to existing ones:

Existing  | Additional
--------- | ----------
`||`      | `&&`
`&&`      | `||`
`<`       | `<=`
`<=`      | `<`
`>`       | `>=`
`>=`      | `>`
`+`       | `-`
`-`       | `+`

## integer-constants ##

Additional integer constant.

## boolean-constants ##

Additional boolean constant.

## variables ##

Visible variables.

## basic-arithmetic ##

Additional integer constant, `+`, `-`.

## basic-logic ##

Additional int-to-bool converter, `||`, `&&`.

## basic-inequalities ##

Additional integer constant, `=`, `>`, `>=`.

## extended-arithmetic ##

Additional visible variables, integer constant, `+`, `-`.

## extended-logic ##

Additional visible variables, int-to-bool converter, `||`, `&&`.

## extended-inequalities ##

Additional visible variables, integer constant, `=`, `>`, `>=`.

## mixed-conditional ##

Additional visible variables, integer constant, `>`, `>=`, `||`, `&&`, `+`, `-`.

## conditional-arithmetic ##

Additional visible variables, integer constant, `>`, `>=`, `+`, `-`, `ite`.

