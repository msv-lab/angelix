#ifndef __ANGELIX_RUNTIME_H__
#define __ANGELIX_RUNTIME_H__

#ifdef ANGELIX_SYMBOLIC_RUNTIME

#define ANGELIX_OUTPUT(type, expr, name) \
  angelix_symbolic_output_##type(expr, name)

#define ANGELIX_CHOOSE(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  angelix_choose_##type(expr, bl, bc, el, ec, env_ids, env_vals, env_size)

#define ANGELIX_REACHABLE(name) \
  angelix_symbolic_reachable(name)

#else

#define ANGELIX_OUTPUT(type, expr, name) \
  angelix_dump_output_##type(expr, name)

#define ANGELIX_CHOOSE(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  expr

#define ANGELIX_REACHABLE(name) \
  angelix_dump_reachable(name)

#endif // ANGELIX_SYMBOLIC_RUNTIME

int angelix_symbolic_output_int(int expr, char* id);
int angelix_symbolic_output_bool(int expr, char* id);
int angelix_symbolic_output_char(char expr, char* id);
int angelix_symbolic_output_str(char* expr, char* id);

int angelix_dump_output_int(int expr, char* id);
int angelix_dump_output_bool(int expr, char* id);
int angelix_dump_output_char(char expr, char* id);
int angelix_dump_output_str(char* expr, char* id);

int angelix_choose_int(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);
int angelix_choose_bool(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);

void angelix_dump_reachable(char* id);
void angelix_symbolic_reachable(char* id);

// For fault localization
void angelix_trace(int bl, int bc, int el, int ec);

#endif
