from os.path import join, dirname, relpath, basename
from utils import cd
import subprocess
import logging
from glob import glob
import os
from pprint import pprint
import time
import statistics
import shutil


import z3
from z3 import Select, Concat, Array, BitVecSort, BitVecVal, Solver, BitVec


logger = logging.getLogger(__name__)


class InferenceError(Exception):
    pass

class NoSmtError(Exception):
    pass

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
                logger.warn('output instance {} for variable {} is missing'.format(i, name))
                raise InferenceError()
        outputs[name] = (type, len(output_instances[name]))

    choices = dict()
    for expr, type in choice_type.items():
        for i in range(0, len(choice_instances[expr])):
            if i not in choice_instances[expr]:
                logger.warn('choice instance {} for variable {} is missing'.format(i, name))
                raise InferenceError()
        choices[expr] = (type, len(choice_instances[expr]), list(choice_env[expr]))

    return outputs, choices, constants, reachable, original


class Inferrer:

    def __init__(self, config, tester, load):
        self.config = config
        self.run_test = tester
        self.load = load

    def _reduce_angelic_forest(self, angelic_paths):
        '''reduce the size of angelic forest (select shortest paths)'''
        logger.info('reducing angelic forest size from {} to {}'.format(len(angelic_paths),
                                                                        self.config['max_angelic_paths']))
        sorted_af = sorted(angelic_paths, key=len)
        return sorted_af[:self.config['max_angelic_paths']]

    def _boolean_angelic_forest(self, angelic_paths):
        '''convert all angelic values to booleans'''
        baf = []
        for path in angelic_paths:
            bpath = dict()
            for expr, instances in path.items():
                bpath[expr] = []
                for angelic, original, env_values in instances:
                    bpath[expr].append((bool(angelic), original, env_values))
            baf.append(bpath)
        return baf


    def __call__(self, project, test, dump, validation_project):
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

        klee_start_time = time.time()
        self.run_test(project, test, klee=True, env=environment)
        klee_end_time = time.time()
        klee_elapsed = klee_end_time - klee_start_time
        statistics.data['time']['klee'] += klee_elapsed
        statistics.save()

        logger.info('sleeping for 1 second...')
        time.sleep(1)

        smt_glob = join(project.dir, 'klee-out-0', '*.smt2')
        smt_files = glob(smt_glob)

        err_glob = join(project.dir, 'klee-out-0', '*.err')
        err_files = glob(err_glob)

        err_list = []
        for err in err_files:
            err_list.append(os.path.basename(err).split('.')[0])

        non_error_smt_files = []
        for smt in smt_files:
            smt_id = os.path.basename(smt).split('.')[0]
            if not smt_id in err_list:
                non_error_smt_files.append(smt)

        if not self.config['ignore_infer_errors']:
            smt_files = non_error_smt_files

        if len(smt_files) == 0 and len(err_list) == 0:
            logger.warning('No paths explored')
            raise NoSmtError()

        if len(smt_files) == 0:
            logger.warning('No non-error paths explored')
            raise NoSmtError()

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
        inference_start_time = time.time()

        angelic_paths = []

        z3.set_param("timeout", self.config['path_solving_timeout'])

        solver = Solver()

        for smt in smt_files:
            logger.info('solving path {}'.format(relpath(smt)))

            try:
                path = z3.parse_smt2_file(smt)
            except:
                logger.warning('failed to parse {}'.format(smt))
                continue

            variables = [str(var) for var in get_vars(path)
                         if str(var).startswith('int!')
                         or str(var).startswith('long!')
                         or str(var).startswith('bool!')
                         or str(var).startswith('char!')
                         or str(var).startswith('reachable!')]

            try:
                outputs, choices, constants, reachable, original_available = parse_variables(variables)
            except:
                continue

            # name -> value list (parsed)
            oracle_constraints = dict()

            def str_to_int(s):
                return int(s)

            def str_to_long(s):
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
            dump_parser_by_type['long'] = str_to_long
            dump_parser_by_type['bool'] = str_to_bool
            dump_parser_by_type['char'] = str_to_char

            def bool_to_bv(b):
                if b:
                    return BitVecVal(1, 32)
                else:
                    return BitVecVal(0, 32)

            def int_to_bv(i):
                return BitVecVal(i, 32)
            
            def long_to_bv(i):
                return BitVecVal(i, 64)

            def char_to_bv(c):
                return BitVecVal(ord(c), 32)

            to_bv_converter_by_type = dict()
            to_bv_converter_by_type['bool'] = bool_to_bv
            to_bv_converter_by_type['int'] = int_to_bv
            to_bv_converter_by_type['long'] = long_to_bv
            to_bv_converter_by_type['char'] = char_to_bv
            
            def bv_to_bool(bv):
                return bv.as_long() != 0

            def bv_to_int(bv):
                l = bv.as_long()
                if l >> 31 == 1:  # negative
                    l -= pow(2, 32)
                return l

            def bv_to_long(bv):
                l = bv.as_long()
                if l >> 63 == 1:  # negative
                    l -= pow(2, 64)
                return l

            def bv_to_char(bv):
                l = bv.as_long()
                return chr(l)

            from_bv_converter_by_type = dict()
            from_bv_converter_by_type['bool'] = bv_to_bool
            from_bv_converter_by_type['int'] = bv_to_int
            from_bv_converter_by_type['long'] = bv_to_long
            from_bv_converter_by_type['char'] = bv_to_char

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

            def array_to_bv64(array):
                return Concat(Select(array, BitVecVal(7, 32)),
                              Select(array, BitVecVal(6, 32)),
                              Select(array, BitVecVal(5, 32)),
                              Select(array, BitVecVal(4, 32)),
                              Select(array, BitVecVal(3, 32)),
                              Select(array, BitVecVal(2, 32)),
                              Select(array, BitVecVal(1, 32)),
                              Select(array, BitVecVal(0, 32)))

            def angelic_variable(type, expr, instance):
                pattern = '{}!choice!{}!{}!{}!{}!{}!angelic'
                s = pattern.format(type, expr[0], expr[1], expr[2], expr[3], instance)
                return Array(s, BitVecSort(32), BitVecSort(8))

            def original_variable(type, expr, instance):
                pattern = '{}!choice!{}!{}!{}!{}!{}!original'
                s = pattern.format(type, expr[0], expr[1], expr[2], expr[3], instance)
                return Array(s, BitVecSort(32), BitVecSort(8))

            def env_variable(expr, instance, name):
                pattern = 'int!choice!{}!{}!{}!{}!{}!env!{}'
                s = pattern.format(expr[0], expr[1], expr[2], expr[3], instance, name)
                return Array(s, BitVecSort(32), BitVecSort(8))

            def output_variable(type, name, instance):
                s = '{}!output!{}!{}'.format(type, name, instance)
                if type == 'long':
                    return Array(s, BitVecSort(32), BitVecSort(8))
                else:
                    return Array(s, BitVecSort(32), BitVecSort(8))

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
                    array = output_variable(type, name, i)
                    bv_value = to_bv_converter_by_type[type](value)
                    if type == 'long':
                        solver.add(bv_value == array_to_bv64(array))
                    else:
                        solver.add(bv_value == array_to_bv32(array))
                    

            for (expr, item) in choices.items():
                type, instances, env = item
                for instance in range(0, instances):
                    selector = angelic_selector(expr, instance)
                    array = angelic_variable(type, expr, instance)
                    solver.add(selector == array_to_bv32(array))

                    selector = original_selector(expr, instance)
                    array = original_variable(type, expr, instance)
                    solver.add(selector == array_to_bv32(array))

                    for name in env:
                        selector = env_selector(expr, instance, name)
                        array = env_variable(expr, instance, name)
                        solver.add(selector == array_to_bv32(array))


            result = solver.check()
            if result != z3.sat:
                logger.info('UNSAT') # TODO: can be timeout
                continue
            model = solver.model()

            # expr -> (angelic * original * env) list
            angelic_path = dict()

            if os.path.exists(self.load[test]):
                shutil.rmtree(self.load[test])
            os.mkdir(self.load[test])

            for (expr, item) in choices.items():
                angelic_path[expr] = []
                type, instances, env = item
                
                expr_str = '{}-{}-{}-{}'.format(expr[0], expr[1], expr[2], expr[3])
                expression_dir = join(self.load[test], expr_str)
                if not os.path.exists(expression_dir):
                    os.mkdir(expression_dir)

                for instance in range(0, instances):
                    bv_angelic = model[angelic_selector(expr, instance)]
                    angelic = from_bv_converter_by_type[type](bv_angelic)
                    bv_original = model[original_selector(expr, instance)]
                    original = from_bv_converter_by_type[type](bv_original)
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
                        value = from_bv_converter_by_type['int'](bv_env)
                        env_values[name] = value

                    if original_available:
                        angelic_path[expr].append((angelic, original, env_values))
                    else:
                        angelic_path[expr].append((angelic, None, env_values))

                    # Dump angelic path to dump folder
                    instance_file = join(expression_dir, str(instance))
                    with open(instance_file, 'w') as file:
                        if isinstance(angelic, bool):
                            if angelic:
                                file.write('1')
                            else:
                                file.write('0')
                        else:
                            file.write(str(angelic))
            

            # Run Tester to validate the dumped values
            validated = self.run_test(validation_project, test, load=self.load[test])
            if validated:
                angelic_paths.append(angelic_path)
            else:
                logger.info('spurious angelic path')

        if self.config['synthesis_bool_only']:
            angelic_paths = self._boolean_angelic_forest(angelic_paths)

        if self.config['max_angelic_paths'] is not None and \
           len(angelic_paths) > self.config['max_angelic_paths']:
            angelic_paths = self._reduce_angelic_forest(angelic_paths)
        else:
            logger.info('found {} angelic paths for test \'{}\''.format(len(angelic_paths), test))

        inference_end_time = time.time()
        inference_elapsed = inference_end_time - inference_start_time
        statistics.data['time']['inference'] += inference_elapsed

        iter_stat = dict()
        iter_stat['time'] = dict()
        iter_stat['time']['klee'] = klee_elapsed
        iter_stat['time']['inference'] = inference_elapsed
        iter_stat['paths'] = dict()
        iter_stat['paths']['explored'] = len(smt_files)
        iter_stat['paths']['angelic'] = len(angelic_paths)
        statistics.data['iterations']['klee'].append(iter_stat)
        statistics.save()

        return angelic_paths
