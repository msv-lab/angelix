#ifndef __ANGELICFIX_RUNTIME_H__
#define __ANGELICFIX_RUNTIME_H__

#ifdef AF_SYMBOLIC_RUNTIME

#define AF_INPUT(type, expr, name) af_symbolic_input_##type(name)
#define AF_OUTPUT(type, expr, name) af_symbolic_output_##type(expr, name)
#define AF_SUSPICIOUS(type, expr, id, env_ids, env_vals, env_size) af_suspicious_##type(expr, id, env_ids, env_vals, env_size)

#else

#define AF_INPUT(type, expr, name) af_dump_input_##type(expr, name)
#define AF_OUTPUT(type, expr, name) af_dump_output_##type(expr, name)
#define AF_SUSPICIOUS(type, expr, id, env_ids, env_vals, env_size) expr

#endif // AF_SYMBOLIC_RUNTIME

int af_symbolic_input_int(char* id);
int af_symbolic_input_bool(char* id);
int af_symbolic_input_char(char* id);
int af_symbolic_input_str(char* id);

int af_symbolic_output_int(int expr, char* id);
int af_symbolic_output_bool(int expr, char* id);
int af_symbolic_output_char(char expr, char* id);
int af_symbolic_output_str(char* expr, char* id);

int af_dump_input_int(int expr, char* id);
int af_dump_input_bool(int expr, char* id);
int af_dump_input_char(char expr, char* id);
int af_dump_input_str(char* expr, char* id);

int af_dump_output_int(int expr, char* id);
int af_dump_output_bool(int expr, char* id);
int af_dump_output_char(char expr, char* id);
int af_dump_output_str(char* expr, char* id);

int af_suspicious_int(int expr, int id, char** env_ids, int* env_vals, int env_size);
int af_suspicious_bool(int expr, int id, char** env_ids, int* env_vals, int env_size);

// For fault localization
void af_loc_printf(const char* loc_msg, const char* file_name);

#endif
