from funcparserlib.lexer import make_tokenizer, Token
from funcparserlib.parser import some, a, maybe, many, finished, skip, forward_decl
from ast import Application, Variable, Constant, transform


default_type = 'integer'


def c_to_smt2(expr):
    conversion = dict()
    conversion['=='] = '='
    conversion['||'] = 'or'
    conversion['&&'] = 'and'
    conversion['!'] = 'not'

    def shallow(expr):
        if type(expr) is Application:
            if expr.symbol in ['+', '-', '*', '/', '>', '<', '>=', '<=', 'ite']:
                return expr
            if expr.symbol in conversion:
                return Application(conversion[expr.symbol], expr.args)
            raise Exception("unknown symbol: " + expr.symbol)
        return expr

    return transform(shallow, expr)


def tokenize(str):
    # for now support only numeric array indices
    name = r'{var}((->{var})|(\.{var})|(\[{index}\]))*'.format(var='[A-Za-z_][A-Za-z_0-9]*',
                                                               index='[0-9]')
    specs = [('space', (r'[ \t\r\n]+',)),
             ('name', (name,)),
             ('int', (r'[0-9]+',)),
             ('char', (r'\'.\'',)),
             ('(', (r'\(',)),
             (')', (r'\)',)),
             ('operator', (r'(<=)|(>=)|(!=)|(==)|(\|\|)|(&&)|[+-<>\*/%!]',))]
    useless = ['space']
    t = make_tokenizer(specs)
    return [x for x in t(str) if x.type not in useless]


def parse(seq):
    tokval = lambda x: x.value
    name = lambda s: a(Token('name', s)) >> tokval
    op = lambda s: a(Token('op', s)) >> tokval
    lbr = skip(lambda s: a(Token('(', s)))
