from os.path import join, dirname, relpath, basename
from utils import cd
import subprocess
import logging
from glob import glob
import os
import shutil
from pprint import pprint
import re

import z3
from z3 import Select, Concat, Array, BitVecSort, BitVecVal, Solver, BitVec

from inference import InferenceError


logger = logging.getLogger(__name__)


# Temporary solution to implement get_vars. In principle, it should be available in z3util.py
class AstRefKey:
    def __init__(self, n):
        self.n = n
    def __hash__(self):
        return self.n.hash()
    def __eq__(self, other):
        return self.n.eq(other.n)
    def __repr__(self):
        return str(self.n)

def askey(n):
    assert isinstance(n, z3.AstRef)
    return AstRefKey(n)

def get_vars(f):
    r = set()
    def collect(f):
      if z3.is_const(f):
          if f.decl().kind() == z3.Z3_OP_UNINTERPRETED and not askey(f) in r:
              r.add(askey(f))
      else:
          for c in f.children():
              collect(c)
    collect(f)
    return r
# end


def parse_variables(vars):
    '''
    <type> ! choice ! <line> ! <column> ! <line> ! <column> ! <instance> ! angelic
    <type> ! choice ! <line> ! <column> ! <line> ! <column> ! <instance> ! original
    <type> ! choice ! <line> ! <column> ! <line> ! <column> ! <instance> ! env ! <name>
    <type> ! const ! <line> ! <column> ! <line> ! <column>
    <type> ! output ! <name> ! <instance>
    reachable ! <name> ! <instance>

    returns outputs, choices, constants, reachable, original

    outputs: name -> type * num of instances
    choices: expr -> type * num of instances * env-name list
    constants: expr list
    reachable: set of strings
    original: if original available

    Note: assume environment variables are always int
    '''
    output_type = dict()
    output_instances = dict()
    choice_type = dict()
    choice_instances = dict()
    choice_env = dict()
    reachable = set()
    constants = set()
    original = False
    for v in vars:
        tokens = v.split('!')
        first = tokens.pop(0)
        if first == 'reachable':
            label = tokens.pop(0)
            reachable.add(label)
        else:
            type = first
            kind = tokens.pop(0)
            if kind == 'output':
                name, instance = tokens.pop(0), int(tokens.pop(0))
                output_type[name] = type
                if name not in output_instances:
                    output_instances[name] = []
                output_instances[name].append(instance)
            elif kind == 'choice':
                expr = int(tokens.pop(0)), int(tokens.pop(0)), int(tokens.pop(0)), int(tokens.pop(0))
                instance = int(tokens.pop(0))
                value = tokens.pop(0)
                if value == 'angelic':
                    choice_type[expr] = type
                    if expr not in choice_env: # because it can be empty
                        choice_env[expr] = set()
                    if expr not in choice_instances:
                        choice_instances[expr] = []
                    choice_instances[expr].append(instance)
                elif value == 'original':
                    original = True
                elif value == 'env':
                    name = tokens.pop(0)
                    if expr not in choice_env:
                        choice_env[expr] = set()
                    choice_env[expr].add(name)
                else:
                    raise InferenceError()
            elif kind == 'const':
                logger.error('constant choices are not supported')
                raise InferenceError()
                if type == 'int':
                    logger.error('integer constant choices are not supported')
                    raise InferenceError()
                expr = int(tokens.pop(0)), int(tokens.pop(0)), int(tokens.pop(0)), int(tokens.pop(0))
                constants.add(expr)
            else:
                raise InferenceError()

    outputs = dict()
    for name, type in output_type.items():
        for i in range(0, len(output_instances[name])):
            if i not in output_instances[name]:
                logger.error('inconsistent variables')
                raise InferenceError()
        outputs[name] = (type, len(output_instances[name]))

    choices = dict()
    for expr, type in choice_type.items():
        for i in range(0, len(choice_instances[expr])):
            if i not in choice_instances[expr]:
                logger.error('inconsistent variables')
                raise InferenceError()
        choices[expr] = (type, len(choice_instances[expr]), list(choice_env[expr]))

    return outputs, choices, constants, reachable, original


class Semfix_Inferrer:

    def __init__(self, working_dir, config, tester):
        self.working_dir = working_dir
        self.config = config
        self.run_test = tester

    def _reduce_angelic_forest(self, angelic_paths):
        '''reduce the size of angelic forest (select shortest paths)'''
        logger.info('reducing angelic forest size from {} to {}'.format(len(angelic_paths),
                                                                        self.config['max_angelic_paths']))
        sorted_af = sorted(angelic_paths, key=len)
        return sorted_af[:self.config['max_angelic_paths']]

    def __call__(self, project, test, dump, fronend_source):
        logger.info('inferring specification for test \'{}\''.format(test))

        environment = dict(os.environ)
        if self.config['klee_max_forks'] is not None:
            environment['ANGELIX_KLEE_MAX_FORKS'] = str(self.config['klee_max_forks'])
        if self.config['klee_max_depth'] is not None:
            environment['ANGELIX_KLEE_MAX_DEPTH'] = str(self.config['klee_max_depth'])
        if self.config['klee_search'] is not None:
            environment['ANGELIX_KLEE_SEARCH'] = self.config['klee_search']
        if self.config['klee_timeout'] is not None:
            environment['ANGELIX_KLEE_MAX_TIME'] = str(self.config['klee_timeout'])
        if self.config['klee_solver_timeout'] is not None:
            environment['ANGELIX_KLEE_MAX_SOLVER_TIME'] = str(self.config['klee_solver_timeout'])
        if self.config['klee_debug']:
            environment['ANGELIX_KLEE_DEBUG'] = 'YES'
        if self.config['klee_ignore_errors']:
            environment['KLEE_DISABLE_MEMORY_ERROR'] = 'YES'
        if self.config['use_semfix_syn']:
            environment['ANGELIX_USE_SEMFIX_SYN'] = 'YES'
        environment['ANGELIX_KLEE_WORKDIR'] = project.dir

        test_dir = self.get_test_dir(test)
        shutil.rmtree(test_dir, ignore_errors='true')
        klee_dir = join(test_dir, 'klee')
        os.makedirs(klee_dir)

        self.run_test(project, test, klee=True, env=environment)

        # loading dump

        # name -> value list
        oracle = dict()

        vars = os.listdir(dump)
        for var in vars:
            instances = os.listdir(join(dump, var))
            for i in range(0, len(instances)):
                if str(i) not in instances:
                    logger.error('corrupted dump for test \'{}\''.format(test))
                    raise InferenceError()
            oracle[var] = []
            for i in range(0, len(instances)):
                file = join(dump, var, str(i))
                with open(file) as f:
                    content = f.read()
                oracle[var].append(content)

        # solving path constraints

        angelic_paths = []

        solver = Solver()

        smt_glob = join(project.dir, 'klee-out-0', '*.smt2')
        smt_files = glob(smt_glob)
        for smt in smt_files:
            logger.info('solving path {}'.format(relpath(smt)))

            try:
                path = z3.parse_smt2_file(smt)
            except:
                logger.warning('failed to parse {}'.format(smt))
                continue

            variables = [str(var) for var in get_vars(path)
                         if str(var).startswith('int!')
                         or str(var).startswith('bool!')
                         or str(var).startswith('char!')
                         or str(var).startswith('reachable!')]

            outputs, choices, constants, reachable, original_available = parse_variables(variables)

            # name -> value list (parsed)
            oracle_constraints = dict()

            def str_to_int(s):
                return int(s)

            def str_to_bool(s):
                if s == 'false':
                    return False
                if s == 'true':
                    return True
                raise InferenceError()

            def str_to_char(s):
                if len(s) != 1:
                    raise InferenceError()
                return s[0]

            dump_parser_by_type = dict()
            dump_parser_by_type['int'] = str_to_int
            dump_parser_by_type['bool'] = str_to_bool
            dump_parser_by_type['char'] = str_to_char

            def bool_to_bv32(b):
                if b:
                    return BitVecVal(1, 32)
                else:
                    return BitVecVal(0, 32)

            def int_to_bv32(i):
                return BitVecVal(i, 32)

            to_bv32_converter_by_type = dict()
            to_bv32_converter_by_type['bool'] = bool_to_bv32
            to_bv32_converter_by_type['int'] = int_to_bv32

            def bv32_to_bool(bv):
                return bv.as_long() != 0

            def bv32_to_int(bv):
                l = bv.as_long()
                if l >> 31 == 1:  # negative
                    l -= 4294967296
                return l

            from_bv32_converter_by_type = dict()
            from_bv32_converter_by_type['bool'] = bv32_to_bool
            from_bv32_converter_by_type['int'] = bv32_to_int

            matching_path = True

            for expected_variable, expected_values in oracle.items():
                if expected_variable == 'reachable':
                    expected_reachable = set(expected_values)
                    if not (expected_reachable == reachable):
                        logger.info('labels \'{}\' executed while {} required'.format(
                            list(reachable),
                            list(expected_reachable)))
                        matching_path = False
                        break
                    continue
                if expected_variable not in outputs.keys():
                    outputs[expected_variable] = (None, 0)  # unconstraint does not mean wrong
                required_executions = len(expected_values)
                actual_executions = outputs[expected_variable][1]
                if required_executions != actual_executions:
                    logger.info('value \'{}\' executed {} times while {} required'.format(
                        expected_variable,
                        actual_executions,
                        required_executions))
                    matching_path = False
                    break
                oracle_constraints[expected_variable] = []
                for i in range(0, required_executions):
                    type = outputs[expected_variable][0]
                    try:
                        value = dump_parser_by_type[type](expected_values[i])
                    except:
                        logger.error('variable \'{}\' has incompatible type {}'.format(expected_variable,
                                                                                       type))
                        raise InferenceError()
                    oracle_constraints[expected_variable].append(value)

            if not matching_path:
                continue

            solver.reset()
            solver.add(path)

            def array_to_bv32(array):
                return Concat(Select(array, BitVecVal(3, 32)),
                              Select(array, BitVecVal(2, 32)),
                              Select(array, BitVecVal(1, 32)),
                              Select(array, BitVecVal(0, 32)))

            def angelic_selector(expr, instance):
                s = 'angelic!{}!{}!{}!{}!{}'.format(expr[0], expr[1], expr[2], expr[3], instance)
                return BitVec(s, 32)

            def original_selector(expr, instance):
                s = 'original!{}!{}!{}!{}!{}'.format(expr[0], expr[1], expr[2], expr[3], instance)
                return BitVec(s, 32)

            def env_selector(expr, instance, name):
                s = 'env!{}!{}!{}!{}!{}!{}'.format(name, expr[0], expr[1], expr[2], expr[3], instance)
                return BitVec(s, 32)

            for name, values in oracle_constraints.items():
                type, _ = outputs[name]
                for i, value in enumerate(values):
                    array = self.output_variable(type, name, i)
                    bv_value = to_bv32_converter_by_type[type](value)
                    solver.add(bv_value == array_to_bv32(array))

            for (expr, item) in choices.items():
                type, instances, env = item
                for instance in range(0, instances):
                    selector = angelic_selector(expr, instance)
                    array = self.angelic_variable(type, expr, instance)
                    solver.add(selector == array_to_bv32(array))

                    selector = original_selector(expr, instance)
                    array = self.original_variable(type, expr, instance)
                    solver.add(selector == array_to_bv32(array))

                    for name in env:
                        selector = env_selector(expr, instance, name)
                        env_type = 'int' #FIXME
                        array = self.env_variable(env_type, expr, instance, name)
                        solver.add(selector == array_to_bv32(array))

            result = solver.check()
            if result != z3.sat:
                logger.info('UNSAT')
                continue
            model = solver.model()

            # store smt2 files
            shutil.copy(smt, klee_dir)

            # generate IO file
            self.generate_IO_file(test, choices, oracle_constraints, outputs)

            # expr -> (angelic * original * env) list
            angelic_path = dict()

            for (expr, item) in choices.items():
                angelic_path[expr] = []
                type, instances, env = item
                for instance in range(0, instances):
                    bv_angelic = model[angelic_selector(expr, instance)]
                    angelic = from_bv32_converter_by_type[type](bv_angelic)
                    bv_original = model[original_selector(expr, instance)]
                    original = from_bv32_converter_by_type[type](bv_original)
                    if original_available:
                        logger.info('expression {}[{}]: angelic = {}, original = {}'.format(expr,
                                                                                            instance,
                                                                                            angelic,
                                                                                            original))
                    else:
                        logger.info('expression {}[{}]: angelic = {}'.format(expr,
                                                                             instance,
                                                                             angelic))
                    env_values = dict()
                    for name in env:
                        bv_env = model[env_selector(expr, instance, name)]
                        value = from_bv32_converter_by_type['int'](bv_env)
                        env_values[name] = value

                    if original_available:
                        angelic_path[expr].append((angelic, original, env_values))
                    else:
                        angelic_path[expr].append((angelic, None, env_values))

            # TODO: add constants to angelic path

            angelic_paths.append(angelic_path)

        # update IO files
        for smt in glob(join(klee_dir, '*.smt2')):
            with open(smt) as f_smt:
                for line in f_smt.readlines():
                    if re.search("declare-fun [a-z]+!output!", line):
                        output_var = line.split(' ')[1]
                        output_var_type = output_var.split('!')[0]
                        for io_file in glob(join(test_dir, '*.IO')):
                            if not output_var in open(io_file).read():
                                with open(io_file, "a") as f_io:
                                    f_io.write("\n")
                                    f_io.write("@output\n")
                                    f_io.write('name {}\n'.format(output_var))
                                    f_io.write('type {}\n'.format(output_var_type))


        if self.config['max_angelic_paths'] is not None and \
           len(angelic_paths) > self.config['max_angelic_paths']:
            angelic_paths = self._reduce_angelic_forest(angelic_paths)
        else:
            logger.info('found {} angelic paths for test \'{}\''.format(len(angelic_paths), test))

        return angelic_paths

    def angelic_variable_name(self, type, expr, instance):
        pattern = '{}!choice!{}!{}!{}!{}!{}!angelic'
        s = pattern.format(type, expr[0], expr[1], expr[2], expr[3], instance)
        return s

    def angelic_variable(self, type, expr, instance):
        return Array(self.angelic_variable_name(type, expr, instance),
                     BitVecSort(32), BitVecSort(8))

    def original_variable_name(self, type, expr, instance):
        pattern = '{}!choice!{}!{}!{}!{}!{}!original'
        s = pattern.format(type, expr[0], expr[1], expr[2], expr[3], instance)
        return s

    def original_variable(self, type, expr, instance):
        return Array(self.original_variable_name(type, expr, instance),
                     BitVecSort(32), BitVecSort(8))

    def env_variable_name(self, type, expr, instance, name):
       pattern = '{}!choice!{}!{}!{}!{}!{}!env!{}'
       s = pattern.format(type, expr[0], expr[1], expr[2], expr[3], instance, name)
       return s

    def env_variable(self, type, expr, instance, name):
       return Array(self.env_variable_name(type, expr, instance, name),
                    BitVecSort(32), BitVecSort(8))

    def output_variable_name(self, type, name, instance):
       s = '{}!output!{}!{}'.format(type, name, instance)
       return s

    def output_variable(self, type, name, instance):
      return Array(self.output_variable_name(type, name, instance),
                   BitVecSort(32), BitVecSort(8))

    def get_test_dir(self, test):
        return join(self.working_dir, 'semfix-syn-input', 'tests', test)

    def generate_IO_file(self, test, choices, oracle_constraints, outputs):
        for choice_id, (expr, item) in enumerate(choices.items()):
            angel_type, instances, env = item
            for instance in range(0, instances):
                test_dir = self.get_test_dir(test)
                IO_file = open(join(test_dir,
                                    "choice" + '.{}.IO'.format(instance)), 'w')
                for name in env:
                    IO_file.write('@input\n')
                    input_type = 'int' # FIXME
                    IO_file.write('name {}\n'.
                                  format(self.env_variable_name(input_type, expr, instance, name)))
                    IO_file.write('init -\n')
                    IO_file.write('type {}\n'.format(input_type))
                    IO_file.write('\n')

                # logger.info('angelic type: {}'.format(angel_type))
                IO_file.write('@output\n')
                IO_file.write('name {}\n'.
                              format(self.angelic_variable_name(angel_type, expr, instance)))
                IO_file.write('type {}\n'.format(angel_type))
                IO_file.write('\n')

                IO_file.write('@output\n')
                IO_file.write('name {}\n'.
                                  format(self.original_variable_name(angel_type, expr, instance)))
                IO_file.write('type {}\n'.format(angel_type))
                IO_file.write('\n')

                for name, values in oracle_constraints.items():
                    constraint_type, _ = outputs[name]
                    for i, value in enumerate(values):
                        IO_file.write('@output\n')
                        IO_file.write('name {}\n'.
                                      format(self.output_variable_name(constraint_type, name, i)))
                        IO_file.write('type {}\n'.format(constraint_type))
                        IO_file.write('\n')

                IO_file.close()
