#ifndef __ANGELIX_RUNTIME_H__
#define __ANGELIX_RUNTIME_H__


#if defined ANGELIX_SYMBOLIC_RUNTIME_WITH_DEPS


#define ANGELIX_OUTPUT(type, expr, name)                  \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_symbolic_output_##type(expr, name) : (expr))

#define ANGELIX_CHOOSE(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_choose_##type##_with_deps(expr, bl, bc, el, ec, env_ids, env_vals, env_size) : (expr))

#define ANGELIX_CHOOSE_CONST(type, expr, bl, bc, el, ec)  \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_choose_const_##type(bl, bc, el, ec) : (expr))

#define ANGELIX_REACHABLE(name)                 \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_symbolic_reachable(name) : 1)

//TODO: trace and load with deps

#elif defined ANGELIX_SYMBOLIC_RUNTIME


#define ANGELIX_OUTPUT(type, expr, name)        \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_symbolic_output_##type(expr, name) : (expr))

#define ANGELIX_CHOOSE(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_choose_##type(bl, bc, el, ec, env_ids, env_vals, env_size) : (expr))

#define ANGELIX_CHOOSE_CONST(type, expr, bl, bc, el, ec)  \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_choose_const_##type(bl, bc, el, ec) : (expr))

#define ANGELIX_REACHABLE(name)                 \
  (getenv("ANGELIX_SYMBOLIC_RUNTIME") ? angelix_symbolic_reachable(name) : 1)


#elif defined ANGELIX_INSTRUMENTATION


#define ANGELIX_OUTPUT(type, expr, name)        \
  angelix_ignore()


#else


#define ANGELIX_OUTPUT(type, expr, name)        \
  angelix_dump_output_##type(expr, name)

#define ANGELIX_CHOOSE(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  (expr)

#define ANGELIX_CHOOSE_CONST(type, expr, bl, bc, el, ec)  \
  (expr)

#define ANGELIX_REACHABLE(name)                 \
  angelix_dump_reachable(name)

#define ANGELIX_TRACE_AND_LOAD(type, expr, instance)                 \
  angelix_trace_and_load_##type(expr, instance)

#endif /* ANGELIX_SYMBOLIC_RUNTIME */


int angelix_dump_output_int(int expr, char* id);
long angelix_dump_output_long(long expr, char* id);
int angelix_dump_output_bool(int expr, char* id);
char angelix_dump_output_char(char expr, char* id);
char* angelix_dump_output_str(char* expr, char* id);


int angelix_choose_int_with_deps(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);
int angelix_choose_bool_with_deps(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);
int angelix_choose_int(int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);
int angelix_choose_bool(int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);


int angelix_choose_const_int(int bl, int bc, int el, int ec);
int angelix_choose_const_bool(int bl, int bc, int el, int ec);


void angelix_dump_reachable(char* id);
void angelix_symbolic_reachable(char* id);


/* Stub */
int angelix_ignore();

/* for localization and validation */
int angelix_trace_and_load(int expr, int bl, int bc, int el, int ec);


#endif
