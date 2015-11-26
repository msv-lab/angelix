package Utility;

use Define;
use Log::Log4perl qw(:easy);

my $logger = Log::Log4perl->get_logger('semfix');

sub readConfigure {
	my $configure_file = shift;
	
	my $subject = "NA";
	my $version = "NA";
#	my $directory = "NA";
	my $source_file_name = "NA";
	my $start_range = -1;
	my $end_range   = -1;
	
	open( CONFIGURE, $configure_file ) or $logger->logdie("Can not open $configure_file for reading");
	
	while ( <CONFIGURE> ) {
		if(/^program\s*=\s*(.*)$/) {
			$subject = $1;
		}
		elsif (/^version\s*=\s*(.*)$/) {
			$version = $1;
		}
		elsif (/^directory\s*=\s*(.*)$/) {
		# 	$directory = $1;
		}
		elsif (/^sourcefile\s*=\s*(.*)$/) {
			$source_file_name = $1;
		}
		elsif (/^startrange\s*=\s*(.*)$/) {
			$start_range = $1;
		}
		elsif (/^endrange\s*=\s*(.*)$/) {
			$end_range = $1;
		}
		else {
			$logger->logdie("configure file format error: $_");
		}
	}
	close(CONFIGURE);
	
 	if ($subject eq "NA" || $version eq "NA" # || $directory eq "NA"
		|| $start_range == -1 || $end_range == -1) 
	{
		$logger->logdie("Some missing information in configure file");	
	}
 	
	return ($subject, $version, # $directory,
	        $source_file_name, $start_range, $end_range);
}

# @hoang convert cvc files to smt2 format
sub cvc2smt {
	my $smt_folder = shift;
	
	my @files = glob("$smt_folder/*");
	
	my %err_file = ();
	my $index = 0;
	foreach (@files) {
		if ( $_ =~ /test(\d+)\.assert\.err/ 
			|| $_ =~ /test(\d+)\.ptr\.err/)
		{
			$err_file{$1} = $index;
			$index++;
		}
	}
	
	foreach (@files) {
		my $tmp_file = $_;
		if ( $tmp_file =~ /test(\d+)\.cvc/ ) {
			if (not defined($err_file{$1} ) ) {
				my $smt_file = $smt_folder . "/test" . $1 . ".smt";
				my $smt2_file = $smt_folder . "/test" . $1 . ".smt2";
				
				$cvc3_err_filename = '/tmp/semfix-cvc3err';
				system("cvc3 -output-lang smtlib +translate $tmp_file > $smt_file 2> $cvc3_err_filename");
				if ( $? != 0 ) {
						$logger->warn("cvc3 failed\n" . `cat $cvc3err_filename`);
				}

				system("cvc3 +translate -lang smt -output-lang smt2 $smt_file > $smt2_file 2> $cvc3_err_filename");
				if ( $? != 0 ) {
						$logger->warn("cvc3 failed\n" . `cat $cvc3err_filename`);
				}

			}
		}
	}
}

sub convert2smt {
	my $cvc_string = shift;
	my $constr_string;
  
	$cvc3_err_filename = '/tmp/semfix-cvc3err';
	`echo "$cvc_string" > /tmp/tmp.cvc`;
	system("cvc3 -output-lang smtlib +translate /tmp/tmp.cvc > /tmp/tmp.smt 2> $cvc3_err_filename");
	if ( $? != 0 ) {
			$logger->warn("cvc3 failed" . `cat $cvc3err_filename`);
	}
	
	system("cvc3 +translate -lang smt -output-lang smt2 /tmp/tmp.smt > /tmp/tmp.smt2 2> $cvc3_err_filename");
	if ( $? != 0 ) {
			$logger->warn("cvc3 failed\n" . `cat $cvc3err_filename`);
	}

	$constr_string = `cat /tmp/tmp.smt2`;
						
	return $constr_string;
}

sub getINTRepresentation {
	my $var_name = shift;
	
	my $int_represent = "(concat (select $var_name (_ bv3 32)) " .
		              	"(concat (select $var_name (_ bv2 32)) " .
		             	"(concat (select $var_name (_ bv1 32)) " .
		                        "(select $var_name (_ bv0 32)))))";
		                   	
		                   	
	return $int_represent;
}

sub getCHARRepresentation {
	my $var_name = shift;
	
	my $char_represent = "(select $var_name (_ bv0 32))";
		                   	               	
	return $char_represent;
}

sub getDeclare {
	my $var_name = shift;
	my $var_type = shift;
	
	return "(declare-fun $var_name () $var_type)\n"
}

sub parse_char_symbolic {		# TODO test this code
	my $constr_string = shift;
	
	my @all_line = split("\n", $constr_string);
	my $value;
	
	foreach(@all_line) {
		if (/\(assert/) {
			$value = parse_char($_);
			last;
		}
	}
	$value =~ s/(\S+)_0x(\S+)/$1/g;
	
	return $value;
}

sub parse_char {
	$string = shift;
	
	if ($string =~ /^\(select (.*)\)$/) {
		return $string;
	}
	elsif ($string =~ /^\(assert\s+(\(.*\))\)$/) {
		return parse_char($1);
	}
	elsif ($string =~ /^\(=\s+(\(.*\))\)$/) {
		return parse_char_multi($1);
	}
	return "";
}

sub parse_char_multi {		#TODO test this code
  my $string = shift;
    
  my @groups = $string =~ m/
        (                   # start of capture buffer 1
        \(                   # match an opening bracket
            (?:               
                [^\(\)]++     # one or more non angle brackets, non back
                  |                  
                (?1)        # found \( or \), so recurse to capture buff
            )*                 
        \)                   # match a closing angle bracket
        )                   # end of capture buffer 1
        /xg;

  my $result = "";
  if ((scalar @groups) >= 1) {
    $result = parse_char($groups[0]);
  }
  return $result;
}

sub parse_int_symbolic {
	my $constr_string = shift;
	
	my @all_line = split("\n", $constr_string);
	my $value;
	
	foreach(@all_line) {
		if (/\(assert/) {
			$value = parse_int($_);
			last;
		}
	}
	$value =~ s/(\S+)_0x(\S+)/$1/g;
	
	return $value;
}

sub parse_int {		#TODO test this code
	$string = shift;
	
	if ($string =~ /^\(concat (.*)\)$/) {
		return $string;
	}
	elsif ($string =~ /^\(assert\s+(\(.*\))\)$/) {
		return parse_int($1);
	}
	elsif ($string =~ /^\(=\s+(\(.*\))\)$/) {
		return parse_int_multi($1);
	}
	return "";
}

sub parse_int_multi {		#TODO test this code
  my $string = shift;
    
  my @groups = $string =~ m/
        (                   # start of capture buffer 1
        \(                   # match an opening bracket
            (?:               
                [^\(\)]++     # one or more non angle brackets, non back
                  |                  
                (?1)        # found \( or \), so recurse to capture buff
            )*                 
        \)                   # match a closing angle bracket
        )                   # end of capture buffer 1
        /xg;

  my $result = "";
  if ((scalar @groups) >= 1) {
    $result = parse_int($groups[0]);
  }
  return $result;
}

sub parse_arr { 		# TODO test this code
	
	my $string = shift;
	my $hash_ptr = shift;

	if ($string =~ /^\(store(.*)\)$/) {
		return $string;
	}
	elsif ($string =~ /^\(assert\s+(\S.*)\)$/) {
		DEBUG_PRINT("parse assert\n");
		return parse_arr($1, $hash_ptr);
	}
	elsif ($string =~ /^\(=\s+(\S.*)\)$/) {
		DEBUG_PRINT("parse equal\n");
		return parse_arr_multi($1, $hash_ptr);
	}
	elsif ($string =~ /^\(select\s+(\S.*)\)$/) {
		DEBUG_PRINT("parse select\n");
		return parse_arr_multi($1, $hash_ptr);
	}
	elsif ($string =~ /^\(let\s+\((\S.*)\)\)$/) {
		DEBUG_PRINT("parse let\n");
		return parse_arr_multi($1, $hash_ptr);
	}
	elsif ($string =~ /^\(\?v\_(\d+)\s+(\S.*)\)$/) {
		DEBUG_PRINT("parse ?v_$1\n");
		my $index = $1;
		my $value = $2;
		my %myhash = %{$hash_ptr};
		while (($key, $val) = each (%myhash)) {
			$value =~ s/\?v\_$key/$val/g;
		}
		$myhash{$index} = $value;
		if ($index == 0) {
			return "$value";   
		}	
  	}
  	return "";
}

sub parse_arr_multi {		#TODO test this code
	DEBUG_PRINT("parse multi\n");
	my $string = shift;
	my $hash_ptr = shift;
    
    my @groups = $string =~ m/
        (                   # start of capture buffer 1
        \(                   # match an opening bracket
            (?:               
                [^\(\)]++     # one or more non angle brackets, non back
                  |                  
                (?1)        # found \( or \), so recurse to capture buff
            )*                 
        \)                   # match a closing angle bracket
        )                   # end of capture buffer 1
        /xg;

  my $result = "";
  foreach(@groups) {
    $result = $result . parse_arr($_, $hash_ptr);
  }
  return $result;
}

sub getPointerArraySize {
	my $arr_value = shift;
	my $ele_type  = shift;
	
	my $arr_size = 0;
	while ($arr_value =~ /store/g) { #FIXME: counter number of store, might not be correct
		$arr_size++;	
	}
	
	if ($ele_type eq "AII") {
		return $arr_size/4;
	}
	elsif ($ele_type eq "AIC") {
		return $arr_size;
	}
	else {
		$logger->logdie("Array element type format error $ele_type in getPointerArraySize");
	}	
}

sub getCILStartEndRange {
	my $start_range = shift;
	my $end_range	= shift;
	my $cil_file	= shift;
	my $new_start_range = $Define::MAX_INT;
	my $new_end_range   = 0;
	
	open (CIL_FILE, $cil_file) or $logger->logdie("Can not open $cil_file for reading");
	
	my $line_num = 0;

	while (<CIL_FILE>) {
		$line_num++;
		if (/\#line\s*(\d+)/) {
			if ($1 >= $start_range && $1 <= $end_range) {
				if ($line_num < $new_start_range) {
					$new_start_range = $line_num + 1;
				}
				if ($line_num > $new_end_range) {
					$new_end_range = $line_num + 1;
				}
			}
		}
	}
	close (CIL_FILE);
	
	if ($new_end_range < $new_start_range) {
		$logger->logdie("Error in identifying new start=$new_start_range, end=$new_end_range range");
	}
	
	return ($new_start_range, $new_end_range);
}

# Compiling source
sub createBitcodeFile {         # $source_file, $des_dir, $cwd
	my $source_file = shift; 
	my $des_dir     = shift;
	my $cwd         = shift;
	my $is_fixing_CoreUtil = shift;
	
	if ( (defined $is_fixing_CoreUtil ) && $is_fixing_CoreUtil == 1)
	{
		my $new_dir = $des_dir . "/../";
		chdir($new_dir);
		$logger->warn("Using klee-gcc!");
		$make_err_filename = '/tmp/semfix-make-err';
		system("make CC=$ENV{'SEMFIX_ROOT'}/$ENV{'KLEE_DIR'}/scripts/klee-gcc 2> $make_err_filename");
		if ( $? != 0 ) {
				$logger->warn("make failed\n" . `cat $make_err_filename`);
		}

	}
	else
	{
		chdir($des_dir);
		$llvm_gcc_err_filename = '/tmp/semfix-llvm-gcc-err';
		system("llvm-gcc -O0 -emit-llvm -c -g $source_file 2> $llvm_gcc_err_filename");
		if ( $? != 0 ) {
				$logger->warn("llvm-gcc failed\n" . `cat $llvm_gcc_err_filename`);
		}

	}
	chdir($cwd);
}


# Running symbolic execution

sub  Utility::runKLEE  {   # ($config_mode, $bug_info, $is_using_POSIX, $object_file, $input_string, $directory, $cwd)
	my $config_mode    = shift;
	my $bug_info       = shift;
	my $is_using_POSIX = shift;
	my $object_file    = shift;
	my $input_string   = shift;
	my $directory      = shift;
	my $cwd            = shift;
	
	
	# By defaut: 
	# Tcas $CONFIG_FLAGS = '-max-forks=100 -max-time=0.2;
	
	#my $CONFIG_FLAGS     = '-max-forks=500 -max-time=0.2';
	#my $CONFIG_FLAGS = '-max-time=0.1';		# For Tcas, Schedule, Schedule2, Replace
	#my $CONFIG_FLAGS = '-max-time=0.5';			# For Grep
	#my $CONFIG_FLAGS = '-max-time=0.5';			# For Coreutils
	my $CONFIG_FLAGS = '-max-time=1.0'; # for slow machines
	my $LIBC_FLAGS       = '--libc=uclibc --posix-runtime';
	my $DUMP_INPUT_FLAGS = '--write-inputs --write-cvcs';
	
	if ($config_mode == 2) {   # Dump globals variables
		$DUMP_INPUT_FLAGS = $DUMP_INPUT_FLAGS . ' --write-globals ';
	}

	$klee_err_filename = '/tmp/semfix-klee-err';
	$klee_out_filename = '/tmp/semfix-klee-out';

	$logger->info("Running KLEE with object file " . `basename $object_file`);

	my $klee_status = 0;
	
	chdir($directory);
	#`rm -f E F; touch E; mkfifo F`;  # @hoang FIXME: just help create environment for cp
	if ($is_using_POSIX) {
		system("timeout 15 klee -bug $bug_info $CONFIG_FLAGS $DUMP_INPUT_FLAGS $LIBC_FLAGS $object_file $input_string 2> $klee_err_filename > $klee_out_filename");
		if ( $? != 0 ) {
				$klee_status = 1;
				$logger->warn("KLEE failed (see debug message)");
				$logger->debug(`cat $klee_err_filename`);
		}
	}
	else {
		$CONFIG_FLAGS = '-max-time=2.0 -max-forks=10000';
		system("timeout 15 klee -bug $bug_info $CONFIG_FLAGS $DUMP_INPUT_FLAGS $object_file $input_string 2> $klee_err_filename > /dev/null");
		$logger->debug("timeout 15 klee -bug $bug_info $CONFIG_FLAGS $DUMP_INPUT_FLAGS $object_file $input_string 2> $klee_err_filename > $klee_out_filename");
		if ( $? != 0 ) {
				$klee_status = 1;
				$logger->warn("KLEE failed (see debug message)");
				$logger->debug(`cat $klee_err_filename`);
		}
	}

	chdir($cwd);

	return $klee_status;
} 

# Interpret SMT model

sub interpretResult {
	my $spec_file = shift;
	my $syn_file  = shift;
	my $res_file  = shift;
	
	my $result;
# TODO should not call it as a separate script
	$result = `./interpret.pl --spec-file=$spec_file --synfunc-file=$syn_file --input-file=$res_file --loc-prefix=l --c-code`;
	
	return $result;
}

sub DEBUG_PRINT {
	if (0) {
		print STDERR "@_\n";
	}
}

1;
