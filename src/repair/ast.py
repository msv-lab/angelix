from collections import namedtuple
from utils import IdGenerator, flatten, unique


# NOTE:
# 
# Currently, it is not in use, but if I have time, I will implement CBS from stratch based on this AST
# Particularly, I should do the following:
# 1. Use new z3 optimizing solvers
# 2. Don't use global components, because they slow down synthesis
# 3. Don't use phantom components, use tests-like encoding for execution instances instead
# 4. Use arrays of bytes instead of int and bool
# 5. I can get python AST directly from frontend + eval

# SMT expression:
Application = namedtuple('Application', ['symbol', 'args'])
Variable = namedtuple('Variable', ['symbol', 'type'])
Constant = namedtuple('Constant', ['value'])

type_of_builtin = dict()

for symbol in ['+', '-', '*', '/', 'ite']:
    type_of_builtin[symbol] = 'integer'
for symbol in ['>', '<', '>=', '<=', '=', 'and', 'or', 'iff', 'not', 'xor']:
    type_of_builtin[symbol] = 'boolean'

type_of_builtin_args = dict()

for symbol in ['+', '-', '*', '/', '>', '<', '>=', '<=', '=']:
    type_of_builtin_args[symbol] = ['integer', 'integer']
for symbol in ['and', 'or', 'iff', 'not', 'xor']:
    type_of_builtin_args[symbol] = ['boolean', 'boolean']
type_of_builtin_args['ite'] = ['boolean', 'integer', 'integer']

assert(set(type_of_builtin.keys()) == set(type_of_builtin_args.keys()))


class Component:

    _id_generator = IdGenerator()

    def __init__(self, expr):
        self.id = Component._id_generator.next()
        self.expr = expr

    def __len__(self):
        return len(set(collect(lambda e: e is Variable, self.expr)))

    def __getitem__(self, key):
        """index -> variable"""
        return unique(collect(lambda e: e is Variable, self.expr))[key]

    def connect(self, args):
        """expr list -> expr"""
        variables = unique(collect(lambda e: e is Variable, self.expr))
        return substitute(dict(zip(variables, args)), self.expr)


# Component variables:
Location = namedtuple('Location', ['instance'])
Instance = namedtuple('Instance', ['connection', 'test', 'stmt', 'exe'])
Input = namedtuple('Input', ['component', 'index'])
Output = namedtuple('Output', ['component'])

# Componentized expressions:
Node = namedtuple('Node', ['component', 'connections', 'is_soft'])


def type_of(object):
    """Type of expressions, components and componentized expressions"""
    if type(object) is Component:
        return type_of(object.expr)

    if type(object) is Node:
        return type_of(object.component)

    if type(object) is Constant:
        if type(object) is int:
            return 'integer'
        if type(object) is bool:
            return 'boolean'

    if type(object) is Application:
        if object.symbol in type_of_builtin:
            return type_of_builtin[object.symbol]

    if type(object) is Location:
        return type_of(object.var)

    if type(object) is Instance:
        return type_of(object.var)

    if type(object) is Output:
        return type_of(object.component)

    if type(object) is Input:
        return type_of(object.component[object.index])

    return None


# These functions work for expressions and componentized expressions

def fold(f, tree):
    """(tree -> 'a list -> 'a) -> tree -> 'a"""
    if type(tree) is Application:
        f(tree)([fold(f, subtree) for subtree in tree.args])
    elif type(tree) is Node:
        f(tree)([fold(f, subtree) for subtree in tree.connections])
    else:
        f(tree)([])


def collect(predicate, tree):
    """(tree -> bool) -> tree -> tree list"""
    def f(tree, acc):
        result = flatten(acc)
        if predicate(tree):
            result.append(tree)
        return result
    fold(f, tree)


def transform(shallow, tree):
    """(tree -> tree) -> tree -> tree"""
    def f(tree, acc):
        if type(tree) is Application:
            reconstructed = Application(tree.symbol, acc)
        elif type(tree) is Node:
            reconstructed = Node(tree.component, acc, tree.is_soft)
        else:
            reconstructed = tree
        return shallow(reconstructed)
    fold(f, tree)


def substitute(mapping, tree):
    """dict(variable -> tree) -> tree -> tree"""
    def shallow(tree):
        if type(tree) is Variable and tree in mapping:
            return mapping[tree]
        else:
            return tree
    return transform(shallow, tree)
