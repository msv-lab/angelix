#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <time.h>
#include "klee/klee.h"
#include "angelix/runtime.h"

typedef char* str;

typedef int bool;
#define true 1
#define false 0

/*
  Hashtable implementation (for positive integers)
 */

struct entry_s {
	char *key;
	int value;
	struct entry_s *next;
};
 
typedef struct entry_s entry_t;
 
struct hashtable_s {
	int size;
	struct entry_s **table;	
};
 
typedef struct hashtable_s hashtable_t;
 
 
/* Create a new hashtable. */
hashtable_t *ht_create( int size ) {
 
	hashtable_t *hashtable = NULL;
	int i;
 
	if( size < 1 ) return NULL;
 
	/* Allocate the table itself. */
	if( ( hashtable = malloc( sizeof( hashtable_t ) ) ) == NULL ) {
		return NULL;
	}
 
	/* Allocate pointers to the head nodes. */
	if( ( hashtable->table = malloc( sizeof( entry_t * ) * size ) ) == NULL ) {
		return NULL;
	}
	for( i = 0; i < size; i++ ) {
		hashtable->table[i] = NULL;
	}
 
	hashtable->size = size;
 
	return hashtable;	
}
 
/* Hash a string for a particular hash table. */
int ht_hash( hashtable_t *hashtable, char *key ) {
 
	unsigned long int hashval;
	int i = 0;
 
	/* Convert our string to an integer */
	while( hashval < ULONG_MAX && i < strlen( key ) ) {
		hashval = hashval << 8;
		hashval += key[ i ];
		i++;
	}
 
	return hashval % hashtable->size;
}
 
/* Create a key-value pair. */
entry_t *ht_newpair( char *key, int value ) {
	entry_t *newpair;
 
	if( ( newpair = malloc( sizeof( entry_t ) ) ) == NULL ) {
		return NULL;
	}
 
	if( ( newpair->key = strdup( key ) ) == NULL ) {
		return NULL;
	}

  newpair->value = value;
 
	newpair->next = NULL;
 
	return newpair;
}
 
/* Insert a key-value pair into a hash table. */
void ht_set( hashtable_t *hashtable, char *key, int value) {
	int bin = 0;
	entry_t *newpair = NULL;
	entry_t *next = NULL;
	entry_t *last = NULL;
 
	bin = ht_hash( hashtable, key );
 
	next = hashtable->table[ bin ];
 
	while( next != NULL && next->key != NULL && strcmp( key, next->key ) > 0 ) {
		last = next;
		next = next->next;
	}
 
	/* There's already a pair.  Let's replace that string. */
	if( next != NULL && next->key != NULL && strcmp( key, next->key ) == 0 ) {
 
		next->value = value;
 
	/* Nope, could't find it.  Time to grow a pair. */
	} else {
		newpair = ht_newpair( key, value );
 
		/* We're at the start of the linked list in this bin. */
		if( next == hashtable->table[ bin ] ) {
			newpair->next = next;
			hashtable->table[ bin ] = newpair;
	
		/* We're at the end of the linked list in this bin. */
		} else if ( next == NULL ) {
			last->next = newpair;
	
		/* We're in the middle of the list. */
		} else  {
			newpair->next = next;
			last->next = newpair;
		}
	}
}
 
/* Retrieve a key-value pair from a hash table. */
int ht_get( hashtable_t *hashtable, char *key ) {
	int bin = 0;
	entry_t *pair;
 
	bin = ht_hash( hashtable, key );
 
	/* Step through the bin, looking for our value. */
	pair = hashtable->table[ bin ];
	while( pair != NULL && pair->key != NULL && strcmp( key, pair->key ) > 0 ) {
		pair = pair->next;
	}
 
	/* Did we actually find anything? */
	if( pair == NULL || pair->key == NULL || strcmp( key, pair->key ) != 0 ) {
		return -1; //FIXME: this is bad
 
	} else {
		return pair->value;
	}
	
}
 
/*
  End of hashtable implementation
 */

void log(char* message) {
  time_t rawtime;
  struct tm * timeinfo;
  time ( &rawtime );
  timeinfo = localtime ( &rawtime );
  char* t = asctime (timeinfo);
  char *pos;
  if ((pos=strchr(t, '\n')) != NULL)
    *pos = '\0';
  FILE *fp = fopen("/tmp/af-runtime.log", "a");
  if (fp != NULL) {
    fprintf(fp, "[%s] %s\n", t, message);
  }
  fclose(fp);
}

#define MAX_NAME_LENGTH 100
#define MAX_TEST_SUITE_SIZE 1000
#define MAX_PATH_LENGTH 1000
#define MAX_MESSAGE_LENGTH 2000

int test_suite_size = -1;
bool test_dump_initialized = false;
char test_suite[MAX_TEST_SUITE_SIZE][MAX_NAME_LENGTH];
char test_dump_dir[MAX_PATH_LENGTH];
char current_test[MAX_NAME_LENGTH];

char message[MAX_MESSAGE_LENGTH];

// for counting instances
hashtable_t *inputs;
hashtable_t *outputs;
hashtable_t *suspicious;

void init_tables() {
  inputs = ht_create(65536);
  outputs = ht_create(65536);
  suspicious = ht_create(65536);
}

void init_test_suite() {
  test_suite_size = 0;
  char* test_suite_str = getenv("AF_TEST_SUITE");
  char* token = strtok(test_suite_str, " \n\t");
  if (token == NULL) {
    strcpy(test_suite[test_suite_size], test_suite_str);
    test_suite_size = 1;
  }
  while (token != NULL) {
    strcpy(test_suite[test_suite_size], token);
    test_suite_size++;
    token = strtok(NULL, " \n\t");
  }
}

void init_test_dump() {
  char* dump = getenv("AF_DUMP_DIR");
  strcpy(test_dump_dir, dump);
  test_dump_initialized = true;
}

void init_current_test() {
  char* ct = getenv("AF_CURRENT_TEST");
  strcpy(current_test, ct);
}

// parsing
int parse_int(char* str) {
  return atoi(str);
}

bool parse_bool(char* str) {
  if (strncmp(str, "true", 4) == 0) {
    return true;
  }
  if (strncmp(str, "false", 5) == 0) {
    return false;
  }
  fprintf(stderr, "[runtime] wrong boolean format: %s\n", str);
  exit(1);
}

char parse_char(char* str) {
  if (strlen(str) != 3 || str[0] != '\'' || str[2] != '\'') {
    fprintf(stderr, "[runtime] wrong character format: %s\n", str);
    exit(1);
  }
  return str[1];
}

/*
  Supports both c='a' and c[0]='a' for the first element  
 */
int parse_instance(char* str) {
  if (str && strlen(str) == 0) return 0;
  str++;
  return atoi(str);
}

// printing
void print_int(char* buffer, char* name, int inst, int i) {
  sprintf(buffer, "%s^%d=%d", name, inst, i);
}

void print_bool(char* buffer, char* name, int inst, bool b) {
  if (b) {
    sprintf(buffer, "%s^%d=true", name, inst);
  } else {
    sprintf(buffer, "%s^%d=false", name, inst);
  }
}

void print_char(char* buffer, char* name, int inst, char c) {
  sprintf(buffer, "%s^%d='%c'", name, inst, c);
}

void print_str(char* buffer, char* name, int inst, str s) {
  sprintf(buffer, "%s^%d=\"%s\"", name, inst, s);
}


// reading test data

#define LOOKUP_RESULT_PROTO(type) \
  struct type##_lookup_result { bool succeed; type value; };

LOOKUP_RESULT_PROTO(int)
LOOKUP_RESULT_PROTO(bool)
LOOKUP_RESULT_PROTO(char)

#undef LOOKUP_RESULT_PROTO


#define GET_FROM_FILE_PROTO(type)                                       \
  struct type##_lookup_result get_##type##_from_file(char* name, int inst, char* test_file) { \
    sprintf(message, "looking for %s value in %s", name, test_file);    \
    log(message);                                                       \
    struct type##_lookup_result result;                                 \
    result.succeed = false;                                             \
    result.value = 0;                                                   \
                                                                        \
    FILE* fp;                                                           \
    char* line_ptr = NULL;                                              \
    size_t len = 0;                                                     \
    ssize_t read;                                                       \
                                                                        \
    fp = fopen(test_file, "r");                                         \
    if (fp == NULL) {                                                   \
      sprintf(message, "wrong test file %s", test_file);                \
      log(message);                                                     \
      exit(1);                                                          \
    }                                                                   \
                                                                        \
    while ((read = getline(&line_ptr, &len, fp)) != -1) {               \
      char line[MAX_NAME_LENGTH];                                       \
      strcpy(line, line_ptr);                                           \
      char* left = strtok(line, "=");                                   \
      char* right = strtok(NULL, "=");                                  \
      size_t name_len = strlen(name);                                   \
      size_t left_len = strlen(left);                                   \
      if (left_len < name_len) continue;                                \
      if (strcmp(left, name) == 0) {                                    \
        result.succeed = true;                                          \
        result.value = parse_##type(right);                             \
        break;                                                          \
      } else {                                                          \
        if (left_len == name_len ||                                     \
            strncmp(left, name, name_len) != 0 ||                       \
            left[name_len] != '^')                                      \
          continue;                                                     \
        left = left + name_len;                                         \
        if (inst == parse_instance(left)) {                             \
          result.succeed = true;                                        \
          result.value = parse_##type(right);                           \
          break;                                                        \
        }                                                               \
      }                                                                 \
    }                                                                   \
                                                                        \
    fclose(fp);                                                         \
                                                                        \
    sprintf(message, "WARNING: value of %s is not found", name);        \
    log(message);                                                       \
                                                                        \
    return result;                                                      \
}

GET_FROM_FILE_PROTO(int)
GET_FROM_FILE_PROTO(bool)
GET_FROM_FILE_PROTO(char)

#undef GET_FROM_FILE_PROTO


#define WRITE_TO_FILE_PROTO(type)                                       \
  void write_##type##_to_file(type value, char* name, int inst, char* test_file) { \
    sprintf(message, "writing %s value to %s", name, test_file);        \
    log(message);                                                       \
                                                                        \
    FILE* fp;                                                           \
                                                                        \
    fp = fopen(test_file, "a");                                         \
    if (fp == NULL) {                                                   \
      sprintf(message, "ERROR: cannot open/create file %s", test_file); \
      log(message);                                                     \
      exit(1);                                                          \
    }                                                                   \
                                                                        \
    char buffer[MAX_NAME_LENGTH];                                       \
    print_##type(buffer, name, inst, value);                            \
    fprintf(fp, "%s\n", buffer);                                        \
                                                                        \
    fclose(fp);                                                         \
  }

WRITE_TO_FILE_PROTO(int)
WRITE_TO_FILE_PROTO(bool)
WRITE_TO_FILE_PROTO(char)
WRITE_TO_FILE_PROTO(str)

#undef WRITE_TO_FILE_PROTO

//TODO: for multiple instances we need to manage index manually
#define SYMBOLIC_INPUT_PROTO(type, typestr)                             \
  int af_symbolic_input_##type(char* id) {                              \
    sprintf(message, "symbolic input %s", id);                          \
    log(message);                                                       \
    if (!test_dump_initialized) {                                       \
      init_test_dump();                                                 \
      init_test_suite();                                                \
    }                                                                   \
    if (!inputs) init_tables();                                         \
    int prev = ht_get(inputs, id);                                      \
    int inst;                                                           \
    if (prev == -1) {                                                   \
      inst = 0;                                                         \
    } else {                                                            \
      inst = prev + 1;                                                  \
    }                                                                   \
    ht_set(inputs, id, inst);                                           \
    char name[MAX_NAME_LENGTH];                                         \
    sprintf(name, "%s!input!%s!%d", typestr, id, inst);                 \
    type s;                                                             \
    klee_make_symbolic(&s, sizeof(s), name);                            \
    bool assumption = false;                                            \
    int i;                                                              \
    for(i = 0; i < test_suite_size; i++) {                              \
      char test_file[MAX_PATH_LENGTH];                                  \
      sprintf(test_file, "%s/%s.in", test_dump_dir, test_suite[i]);     \
      struct type##_lookup_result result = get_##type##_from_file(id, inst, test_file); \
      if (result.succeed) {                                             \
        assumption = (s == result.value) | assumption;                  \
      }                                                                 \
    }                                                                   \
    klee_assume(assumption);                                            \
    return s;                                                           \
  }

SYMBOLIC_INPUT_PROTO(int, "int")
SYMBOLIC_INPUT_PROTO(bool, "bool")
SYMBOLIC_INPUT_PROTO(char, "char")

#undef SYMBOLIC_INPUT_PROTO


#define DUMP_INPUT_PROTO(type)                                          \
  int af_dump_input_##type(type expr, char* id) {                       \
    if (getenv("AF_DUMP")) {                                            \
      if (!test_dump_initialized) {                                     \
        init_test_dump();                                               \
        init_current_test();                                            \
      }                                                                 \
      if (!inputs) init_tables();                                       \
      int prev = ht_get(inputs, id);                                    \
      int inst;                                                         \
      if (prev == -1) {                                                 \
        inst = 0;                                                       \
      } else {                                                          \
        inst = prev + 1;                                                \
      }                                                                 \
      ht_set(inputs, id, inst);                                         \
      char test_file[MAX_PATH_LENGTH];                                  \
      sprintf(test_file, "%s/%s.in", test_dump_dir, current_test);      \
      write_##type##_to_file(expr, id, inst, test_file);                \
      return expr;                                                      \
    } else {                                                            \
      return expr;                                                      \
    }                                                                   \
  }

DUMP_INPUT_PROTO(int)
DUMP_INPUT_PROTO(bool)
DUMP_INPUT_PROTO(char)
DUMP_INPUT_PROTO(str)

#undef DUMP_INPUT_PROTO

#define SYMBOLIC_OUTPUT_PROTO(type, typestr)                            \
  int af_symbolic_output_##type(type expr, char* id) {                  \
    sprintf(message, "symbolic output %s", id);                         \
    log(message);                                                       \
    if (!inputs) init_tables();                                         \
    int prev = ht_get(outputs, id);                                     \
    int inst;                                                           \
    if (prev == -1) {                                                   \
      inst = 0;                                                         \
    } else {                                                            \
      inst = prev + 1;                                                  \
    }                                                                   \
    ht_set(outputs, id, inst);                                          \
    char name[MAX_NAME_LENGTH];                                         \
    sprintf(name, "%s!output!%s!%d", typestr, id, inst);                \
    type s;                                                             \
    klee_make_symbolic(&s, sizeof(s), name);                            \
    klee_assume(s == expr);                                             \
    return s;                                                           \
  }

SYMBOLIC_OUTPUT_PROTO(int, "int")
SYMBOLIC_OUTPUT_PROTO(bool, "bool")
SYMBOLIC_OUTPUT_PROTO(char, "char")

#undef SYMBOLIC_OUTPUT_PROTO


#define DUMP_OUTPUT_PROTO(type)                                     \
  int af_dump_output_##type(type expr, char* id) {                  \
    if (getenv("AF_DUMP")) {                                        \
      if (!test_dump_initialized) {                                 \
        init_test_dump();                                           \
        init_current_test();                                        \
      }                                                             \
      if (!inputs) init_tables();                                   \
      int prev = ht_get(outputs, id);                               \
      int inst;                                                     \
      if (prev == -1) {                                             \
        inst = 0;                                                   \
      } else {                                                      \
        inst = prev + 1;                                            \
      }                                                             \
      ht_set(outputs, id, inst);                                    \
      char test_file[MAX_PATH_LENGTH];                              \
      sprintf(test_file, "%s/%s.out", test_dump_dir, current_test); \
      write_##type##_to_file(expr, id, inst, test_file);            \
      return expr;                                                  \
    } else {                                                        \
      return expr;                                                  \
    }                                                               \
  }

DUMP_OUTPUT_PROTO(int)
DUMP_OUTPUT_PROTO(bool)
DUMP_OUTPUT_PROTO(char)
DUMP_OUTPUT_PROTO(str)

#undef DUMP_OUTPUT_PROTO


#define SUSPICIOUS_PROTO(type, typestr)                                 \
  int af_suspicious_##type(int expr, int id, char** env_ids, int* env_vals, int env_size) { \
    sprintf(message, "suspicous %d", id);                               \
    log(message);                                                       \
    if (!inputs) init_tables();                                         \
    char str_id[15];                                                    \
    sprintf(str_id, "%d", id);                                          \
    int prev = ht_get(suspicious, str_id);                              \
    int inst;                                                           \
    if (prev == -1) {                                                   \
      inst = 0;                                                         \
    } else {                                                            \
      inst = prev + 1;                                                  \
    }                                                                   \
    ht_set(suspicious, str_id, inst);                                   \
    int i;                                                              \
    for (i = 0; i < env_size; i++) {                                    \
      char name[MAX_NAME_LENGTH];                                       \
      sprintf(name, "int!suspicious!%d!%d!env!%s", id, inst, env_ids[i]); \
      int sv;                                                           \
      klee_make_symbolic(&sv, sizeof(sv), name);                        \
      klee_assume(sv == env_vals[i]);                                   \
    }                                                                   \
                                                                        \
    char name_original[MAX_NAME_LENGTH];                                \
    sprintf(name_original, "%s!suspicious!%d!%d!original", typestr, id, inst); \
    int so;                                                             \
    klee_make_symbolic(&so, sizeof(so), name_original);                 \
    klee_assume(so == expr);                                            \
                                                                        \
    char name[MAX_NAME_LENGTH];                                         \
    sprintf(name, "%s!suspicious!%d!%d!angelic", typestr, id, inst);    \
    int s;                                                              \
    klee_make_symbolic(&s, sizeof(s), name);                            \
                                                                        \
    return s;                                                           \
  }

SUSPICIOUS_PROTO(int, "int")
SUSPICIOUS_PROTO(bool, "bool")

#undef SUSPICIOUS_PROTO


void af_loc_printf(const char* loc_msg, const char* file_name) {
  FILE *fp = fopen(file_name, "a");
  if (fp == NULL) {
    fprintf(stderr, "[runtime] Failed to open %s\n", file_name);
    exit(1);
  }
  fprintf(fp, "%s\n", loc_msg);
  fclose(fp);
}
