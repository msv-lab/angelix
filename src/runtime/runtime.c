#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <time.h>
#include <dirent.h>
#include "klee/klee.h"
#include "runtime.h"


typedef char* str;
typedef int bool;

#define true 1
#define false 0

/*
  Hashtable implementation (for positive integers)
 */

int table_miss = 1;

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
    table_miss = 1;
		return 0;
 
	} else {
    table_miss = 0;
		return pair->value;
	}
	
}
 
/*
  End of hashtable implementation
 */

#define MAX_PATH_LENGTH 1000
#define MAX_NAME_LENGTH 1000
#define INT_LENGTH 15
#define LONG_LENGTH (INT_LENGTH * 2)

hashtable_t *output_instances;
hashtable_t *choice_instances;
hashtable_t *const_choices;

void init_tables() {
  output_instances = ht_create(65536);
  choice_instances = ht_create(65536);
  const_choices = ht_create(65536);
}

/*
  Parsing and printing
*/

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
  abort();
}

char parse_char(char* str) {
  if (strlen(str) != 1) {
    fprintf(stderr, "[runtime] wrong character format: %s\n", str);
    abort();
  }
  return str[0];
}

char* print_int(int i) {
  char* str = (char*) malloc(INT_LENGTH * sizeof(char));
  sprintf(str, "%d", i);
  return str;
}

char* print_long(long i) {
  char* str = (char*) malloc(LONG_LENGTH * sizeof(char));
  sprintf(str, "%ld", i);
  return str;
}

char* print_bool(bool b) {
  if (b) {
    return "true";
  } else {
    return "false";
  }
}

char* print_char(char c) {
  char* str = (char*) malloc(2 * sizeof(char));
  str[1] = '\0';
  str[0] = c;
  return str;
}

char* print_str(char* s) {
  return s;
}

/* 
   Loading and dumping
*/

char* load_instance(char* var, int instance) {
  char *dir = getenv("ANGELIX_LOAD");
  char file[MAX_PATH_LENGTH + 1];
  sprintf(file, "%s/%s/%d", dir, var, instance);

  FILE *fp = fopen(file, "r");
  if (fp == NULL)
    return NULL;
  
  fseek(fp, 0, SEEK_END);
  long fsize = ftell(fp);
  fseek(fp, 0, SEEK_SET);
  
  char *string = malloc(fsize + 1);
  fread(string, fsize, 1, fp);
  fclose(fp);
 
  string[fsize] = 0;
  return string;
}

void dump_instance(char* var, int instance, char* data) {
  char *dir = getenv("ANGELIX_DUMP");
  char vardir[MAX_PATH_LENGTH + 1];
  sprintf(vardir, "%s/%s", dir, var, instance);

  DIR* d = opendir(vardir);
  if (d) {
    closedir(d);
  } else {
    mkdir(vardir, 0777);
  }

  char file[MAX_PATH_LENGTH + 1];
  sprintf(file, "%s/%d", vardir, instance);

  FILE *fp = fopen(file, "w");
  if (!fp)
    abort();
  fputs(data, fp);
  fclose(fp);
}

/*
  Reading dumped data
*/

#define LOOKUP_RESULT_PROTO(type) \
  struct type##_lookup_result { bool succeed; type value; };

LOOKUP_RESULT_PROTO(int)
LOOKUP_RESULT_PROTO(bool)
LOOKUP_RESULT_PROTO(char)

#undef LOOKUP_RESULT_PROTO

#define LOAD_PROTO(type)                                              \
  struct type##_lookup_result load_##type(char* var, int instance) {  \
    struct type##_lookup_result result;                               \
    result.succeed = false;                                           \
    result.value = 0;                                                 \
                                                                      \
    char* data = load_instance(var, instance);                        \
                                                                      \
    if (data != NULL) {                                               \
      result.succeed = true;                                          \
      result.value = parse_##type(data);                              \
    }                                                                 \
                                                                      \
    return result;                                                    \
  }

LOAD_PROTO(int)
LOAD_PROTO(bool)
LOAD_PROTO(char)

#undef LOAD_PROTO

#define DUMP_PROTO(type)                                  \
  void dump_##type(char* var, int instance, type value) { \
    dump_instance(var, instance, print_##type(value));    \
  }

DUMP_PROTO(int)
DUMP_PROTO(long)
DUMP_PROTO(bool)
DUMP_PROTO(char)
DUMP_PROTO(str)

#undef WRITE_TO_FILE_PROTO

#define SYMBOLIC_OUTPUT_PROTO(type, typestr)                  \
  type angelix_symbolic_output_##type(type expr, char* id) {   \
    if (!output_instances)                                    \
      init_tables();                                          \
    int previous = ht_get(output_instances, id);              \
    int instance;                                             \
    if (table_miss) {                                         \
      instance = 0;                                           \
    } else {                                                  \
      instance = previous + 1;                                \
    }                                                         \
    ht_set(output_instances, id, instance);                   \
    char name[MAX_NAME_LENGTH];                               \
    sprintf(name, "%s!output!%s!%d", typestr, id, instance);  \
    type s;                                                   \
    klee_make_symbolic(&s, sizeof(s), name);                  \
    klee_assume(s == expr);                                   \
    return s;                                                 \
  }

SYMBOLIC_OUTPUT_PROTO(int, "int")
SYMBOLIC_OUTPUT_PROTO(long, "long")
SYMBOLIC_OUTPUT_PROTO(bool, "bool")
SYMBOLIC_OUTPUT_PROTO(char, "char")

#undef SYMBOLIC_OUTPUT_PROTO


//TODO: later I need to express it through angelix_symbolic_output_str
void angelix_symbolic_reachable(char* id) {
  if (!output_instances)
    init_tables();
  int previous = ht_get(output_instances, "reachable");
  int instance;
  if (table_miss) {
    instance = 0;
  } else {
    instance = previous + 1;
  }
  ht_set(output_instances, "reachable", instance);
  char name[MAX_NAME_LENGTH];
  sprintf(name, "reachable!%s!%d", id, instance);
  int s;
  klee_make_symbolic(&s, sizeof(int), name);
  klee_assume(s);
}


#define DUMP_OUTPUT_PROTO(type)                         \
  type angelix_dump_output_##type(type expr, char* id) { \
    if (getenv("ANGELIX_DUMP")) {                       \
      if (!output_instances)                            \
        init_tables();                                  \
      int previous = ht_get(output_instances, id);      \
      int instance;                                     \
      if (table_miss) {                                 \
        instance = 0;                                   \
      } else {                                          \
        instance = previous + 1;                        \
      }                                                 \
      ht_set(output_instances, id, instance);           \
      dump_##type(id, instance, expr);                  \
      return expr;                                      \
    } else {                                            \
      return expr;                                      \
    }                                                   \
  }

DUMP_OUTPUT_PROTO(int)
DUMP_OUTPUT_PROTO(long)
DUMP_OUTPUT_PROTO(bool)
DUMP_OUTPUT_PROTO(char)

#undef DUMP_OUTPUT_PROTO


//TODO: later I need to express it through angelix_dump_output_str
void angelix_dump_reachable(char* id) {
    if (getenv("ANGELIX_DUMP")) {
      if (!output_instances)
        init_tables();
      int previous = ht_get(output_instances, "reachable");
      int instance;
      if (table_miss) {
        instance = 0;
      } else {
        instance = previous + 1;
      }
      ht_set(output_instances, "reachable", instance);
      dump_str("reachable", instance, id);
    }
    return;
}


#define CHOOSE_WITH_DEPS_PROTO(type, typestr)                           \
  int angelix_choose_##type##_with_deps(int expr,                       \
                                        int bl, int bc, int el, int ec, \
                                        char** env_ids,                 \
                                        int* env_vals,                  \
                                        int env_size) {                 \
    if (!choice_instances)                                              \
      init_tables();                                                    \
    char str_id[INT_LENGTH * 4 + 4 + 1];                                \
    sprintf(str_id, "%d-%d-%d-%d", bl, bc, el, ec);                     \
    int previous = ht_get(choice_instances, str_id);                    \
    int instance;                                                       \
    if (table_miss) {                                                   \
      instance = 0;                                                     \
    } else {                                                            \
      instance = previous + 1;                                          \
    }                                                                   \
    ht_set(choice_instances, str_id, instance);                         \
    int i;                                                              \
    for (i = 0; i < env_size; i++) {                                    \
      char name[MAX_NAME_LENGTH];                                       \
      char* env_fmt = "int!choice!%d!%d!%d!%d!%d!env!%s";               \
      sprintf(name, env_fmt, bl, bc, el, ec, instance, env_ids[i]);     \
      int sv;                                                           \
      klee_make_symbolic(&sv, sizeof(sv), name);                        \
      klee_assume(sv == env_vals[i]);                                   \
    }                                                                   \
                                                                        \
    char name_orig[MAX_NAME_LENGTH];                                    \
    char* orig_fmt = "%s!choice!%d!%d!%d!%d!%d!original";               \
    sprintf(name_orig, orig_fmt, typestr, bl, bc, el, ec, instance);    \
    int so;                                                             \
    klee_make_symbolic(&so, sizeof(so), name_orig);                     \
    klee_assume(so == expr);                                            \
                                                                        \
    char name[MAX_NAME_LENGTH];                                         \
    char* angelic_fmt = "%s!choice!%d!%d!%d!%d!%d!angelic";             \
    sprintf(name, angelic_fmt, typestr, bl, bc, el, ec, instance);      \
    int s;                                                              \
    klee_make_symbolic(&s, sizeof(s), name);                            \
                                                                        \
    return s;                                                           \
  }

CHOOSE_WITH_DEPS_PROTO(int, "int")
CHOOSE_WITH_DEPS_PROTO(bool, "bool")

#undef CHOOSE_WITH_DEPS_PROTO


#define CHOOSE_PROTO(type, typestr)                                 \
  int angelix_choose_##type(int bl, int bc, int el, int ec,         \
                            char** env_ids,                         \
                            int* env_vals,                          \
                            int env_size) {                         \
    if (!choice_instances)                                          \
      init_tables();                                                \
    char str_id[INT_LENGTH * 4 + 4 + 1];                            \
    sprintf(str_id, "%d-%d-%d-%d", bl, bc, el, ec);                 \
    int previous = ht_get(choice_instances, str_id);                \
    int instance;                                                   \
    if (table_miss) {                                               \
      instance = 0;                                                 \
    } else {                                                        \
      instance = previous + 1;                                      \
    }                                                               \
    ht_set(choice_instances, str_id, instance);                     \
    int i;                                                          \
    for (i = 0; i < env_size; i++) {                                \
      char name[MAX_NAME_LENGTH];                                   \
      char* env_fmt = "int!choice!%d!%d!%d!%d!%d!env!%s";           \
      sprintf(name, env_fmt, bl, bc, el, ec, instance, env_ids[i]); \
      int sv;                                                       \
      klee_make_symbolic(&sv, sizeof(sv), name);                    \
      klee_assume(sv == env_vals[i]);                               \
    }                                                               \
                                                                    \
    char name[MAX_NAME_LENGTH];                                     \
    char* angelic_fmt = "%s!choice!%d!%d!%d!%d!%d!angelic";         \
    sprintf(name, angelic_fmt, typestr, bl, bc, el, ec, instance);  \
    int s;                                                          \
    klee_make_symbolic(&s, sizeof(s), name);                        \
                                                                    \
    return s;                                                       \
  }

CHOOSE_PROTO(int, "int")
CHOOSE_PROTO(bool, "bool")

#undef CHOOSE_PROTO


#define CHOOSE_CONST_PROTO(type, typestr)                           \
  int angelix_choose_const_##type(int bl, int bc, int el, int ec) { \
    if (!const_choices)                                             \
      init_tables();                                                \
    char str_id[INT_LENGTH * 4 + 4 + 1];                            \
    sprintf(str_id, "%d-%d-%d-%d", bl, bc, el, ec);                 \
    int choice = ht_get(const_choices, str_id);                     \
    if (table_miss) {                                               \
      char name[MAX_NAME_LENGTH];                                   \
      char* angelic_fmt = "%s!const!%d!%d!%d!%d";                   \
      sprintf(name, angelic_fmt, typestr, bl, bc, el, ec);          \
      int s;                                                        \
      klee_make_symbolic(&s, sizeof(s), name);                      \
      ht_set(const_choices, str_id, s);                             \
      return s;                                                     \
    } else {                                                        \
      return choice;                                                \
    }                                                               \
  }

CHOOSE_CONST_PROTO(int, "int")
CHOOSE_CONST_PROTO(bool, "bool")

#undef CHOOSE_CONST_PROTO

int angelix_ignore() {
  return 0;
}

int angelix_trace_and_load(int expr, int bl, int bc, int el, int ec) {
  if (getenv("ANGELIX_TRACE")) {
    FILE *fp = fopen(getenv("ANGELIX_TRACE"), "a");
    if (fp == NULL)
      abort();
    fprintf(fp, "%d %d %d %d\n", bl, bc, el, ec);
    fclose(fp);
  }
  if (getenv("ANGELIX_LOAD")) {
    if (!choice_instances)
      init_tables();
    char str_id[INT_LENGTH * 4 + 4 + 1];
    sprintf(str_id, "%d-%d-%d-%d", bl, bc, el, ec);
    int previous = ht_get(choice_instances, str_id);
    int instance;
    if (table_miss) {
      instance = 0;
    } else {
      instance = previous + 1;
    }
    ht_set(choice_instances, str_id, instance);
    char* data = load_instance(str_id, instance);
    if (!data) {
      return expr;
    }
    int result = parse_int(data);
    return result;
  }
  return expr;
}
