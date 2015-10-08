#ifndef __ANGELIX_RUNTIME_H__
#define __ANGELIX_RUNTIME_H__

#ifdef ANGELIX_SYMBOLIC_RUNTIME

#define ANGELIX_OUTPUT(type, expr, name) \
  angelix_symbolic_output_##type(expr, name)

#define ANGELIX_SUSPICIOUS(type, expr, bl, bc, el, ec, env_ids, env_vals, env_size) \
  angelix_suspicious_##type(expr, bl, bc, el, ec, env_ids, env_vals, env_size)

#else

#define ANGELIX_OUTPUT(type, expr, name) \
  angelix_dump_output_##type(expr, name)

#define ANGELIX_SUSPICIOUS(type, expr, id, env_ids, env_vals, env_size) \
  expr

#endif // ANGELIX_SYMBOLIC_RUNTIME

int angelix_symbolic_output_int(int expr, char* id);
int angelix_symbolic_output_bool(int expr, char* id);
int angelix_symbolic_output_char(char expr, char* id);
int angelix_symbolic_output_str(char* expr, char* id);

int angelix_dump_output_int(int expr, char* id);
int angelix_dump_output_bool(int expr, char* id);
int angelix_dump_output_char(char expr, char* id);
int angelix_dump_output_str(char* expr, char* id);

int angelix_suspicious_int(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);
int angelix_suspicious_bool(int expr, int bl, int bc, int el, int ec, char** env_ids, int* env_vals, int env_size);

// For fault localization
void angelix_trace(int begin_line, int begin_column, int end_line, int end_column);

#endif
