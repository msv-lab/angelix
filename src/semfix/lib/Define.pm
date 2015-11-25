package Define;

our $DEFAULT_LIB_FILE	= "$ENV{'SEMFIX_ROOT'}/lib/component.lib";
our $DEFAULT_IO_FILE	= "iopair.xml";
our $DEFAULT_BUG_INFO	= "bug.info";
our $DEFAULT_SYN_FILE	= "synfunc.info";
our $DEFAULT_RESULT_FILE = "/tmp/z3.res";
our $DEFAULT_METADATA_FILE = "/tmp/metadata.xml";
our $DEFAULT_KLEE_DIR	= "$ENV{'SEMFIX_ROOT'}/$ENV{'KLEE_DIR'}";

our $TMP = 1;
our $DEFAULT_IS_BUFFERED = 1;		# 0: Testing and debugging have not been precomputed. Otherwise, 1
our $DEFAULT_RUNNING_FIX = 1;

our $MAX_INT = 2147483647;

# Repair mode
our $TEST_MODE					= 1;
our $REPAIR_WITH_CACHED_TEST	= 2;
our $TEST_AND_REPAIR			= 3;


# Parse flags

our $PARSE_SUCCESS            = 0;
our $PARSE_UNSUPPORTED_OUTPUT = 1;
1;
