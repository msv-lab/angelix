use Categorization;
use FuncDecl;
use IOPair;

package Spec;

use IO::File;
use XML::Simple;
use Data::Dumper;
use File::Basename;

use Var;

sub new {
	my $class = shift;
	
	my $categorize = new Categorization();
	my $self  = {
		_funcs_categorize => $categorize,
		_io_pairs         => [],
	};
	
	bless $self, $class;
	return $self;
}

sub add_func {
	my $self = shift;
	my $func = shift;
	
	my $categorize = $self->{_funcs_categorize};
	 
	$categorize->add_func($func);
	
	return;
}

sub get_all_func {
	my $self = shift;
	my $categorize = $self->{_funcs_categorize};
	
	return $categorize->get_all_func();
}

sub select_funcs {
	my $self            = shift;
	my $stmt_kind       = shift;
	my $energy_level    = shift;
	my $secondary_level = shift;
	my $out_type        = shift;
	my $categorize   = $self->{_funcs_categorize};
	
	return $categorize->select_funcs($stmt_kind, $energy_level, $secondary_level, $out_type);
}

sub get_io_pairs {
	my $self = shift;
	return $self->{_io_pairs};
}

sub parse_lib {
	my $self      = shift;
	my $spec_file = shift;

	open( INPUT, $spec_file ) || die "Cannot open $spec_file to read \n";

	my @all_funcdecl;
	my $tmp_funcdecl;

	# parse the function declarations
	while (<INPUT>) {
		if (/^%/) {		# comment
			next;
		}
		$line = $_;
		if ( $line =~ /^fun\s+(\w+)/ ) {	# basic components declaration 
			$tmp_funcdecl = new FuncDecl();
			$tmp_funcdecl->{_func_name} = $1;
			
			$self->add_func($tmp_funcdecl);	
		}
		elsif ( $line =~ /^in\s+(x\d+):\((.*)\)/ ) {	# input variable
			my $var = new Var();
			
			$var->{_name} = $1;
			$var->{_type} = $2;
			$tmp_funcdecl->add_input( $var );	
		}
		elsif ( $line =~ /^out\s+(x\d+):\((.*)\)/ ) {	# output variable
			my $var = new Var();
			$var->{_name} = $1;
			$var->{_type} = $2;
			$tmp_funcdecl->add_output( $var );
		}
		elsif ( $line =~ /^model\s+(.*)/ ) {			# function's constraint
			$tmp_funcdecl->{_constraint} = $1;
		}
		elsif ( $line =~ /^impl\s+(.*)/ ) {				# function's implementation
			$tmp_funcdecl->parse_impl($1);
		}
		elsif ( $line =~ /^param\s+(.*):\((.*)\)/ ) {
			my $var = new Var();
			$var->{_name} = $1;
			$var->{_type} = $2;
			$tmp_funcdecl->add_param( $var );
		}
		elsif ( $line =~ /^param\s+(.*)/ ) {
			$tmp_funcdecl->add_param( $1, "Int" );
		}
	}
	
	close(INPUT);
	
	#my $categorize = $self->{_funcs_categorize};
	#$categorize->print_myself();
	
	return;
}

sub parse_io {
	my $self    = shift;
	my $io_file = shift;
	
	my ($file_name, $directory) = fileparse($io_file);
	
	my $simple = XML::Simple->new();
	my $data   = $simple->XMLin($io_file, forcearray => 1, keeproot => 1);  
	
	my $tmp_var   = $data->{testsuite}[0];
	my @testcases = @{$tmp_var->{testcase}};
	
	my @io_pairs = ();
	my $tmp_iopair;
	foreach(@testcases) {
		my $test = $_;
		$tmp_iopair = new IOPair();
		
		$tmp_iopair->{_index}  = $test->{idx};
		$tmp_iopair->{_status} = $test->{status};
		
		my $input    = $test->{input}[0]->{content};
		$tmp_iopair->{_input} = $input;
		
		my $out_file = $test->{output}[0]->{content};
#		$out_file = "$directory/$out_file";
		
		my $raw = `cat $out_file`;
		$tmp_iopair->{_raw_output} = $raw;
		my @all_line = split("\n", $raw);
		my @out_arr = ();
		
		foreach(@all_line) {
			my $line = $_;
			my @tmp_arr = split(//, $line);
			push(@out_arr, @tmp_arr);
			#my $out_size = scalar @tmp_arr;
			#print STDERR "OUT_SIZE: $out_size\n";
		}
		$tmp_iopair->{_output} = \@out_arr;
		push(@io_pairs, $tmp_iopair);
	}
	$self->{_io_pairs} = \@io_pairs;
	
	return;
}

sub print_myself {
	my $self = shift;

	print "\@func\n";

	foreach ( @{ $self->{_funcs} } ) {
		$_->print_myself();
		print "\n";
	}

	foreach ( @{ $self->{_io_pairs} } ) {
		$_->print_myself();
		print "\n";
	}
}

sub DEBUG_PRINT {
	if (0) {
		print STDERR "@_\n";
	}
	return;
}

1;
