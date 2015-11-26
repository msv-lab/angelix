use OutConstr;
use SynFunc;
use Utility;

use Log::Log4perl qw(:easy);

package AllOutConstr;

my $logger = Log::Log4perl->get_logger('semfix');

my $INT32   = 4294967296;

sub new {
	my $class = shift;
	my $self  = {
		_out_var    => [],       # @hoang an array of Var
		_expect_var => [],	     # @hoang an array of Var
		_constr     => []        # @hoang constraints for output's value
	};

	bless $self, $class;
	return $self;
}

sub parseAll {                   # @hoang parse the smt2 files
	my $self       = shift;
	my $smt_folder = shift;

	my @expect_out = @{ $self->{_expect_var} };
	my $out_size = scalar @expect_out;
	
	my @files = glob("$smt_folder/*.smt2");
	
	my $valid_flag = 0;

	foreach (@files) {
		my $out_constr = new OutConstr();
		$out_constr->parse($_);
		
		my $is_valid_constr = $out_constr->is_valid($out_size);
		$logger->debug("FILE: $_");
		$logger->debug("VALID: $is_valid_constr");
		
		
		if ( $is_valid_constr ) {
			$logger->debug("file $_ is valid");
			
			$valid_flag = 1;
		
			push( @{ $self->{_out_var} }, @{ $out_constr->{_out_var} } );
			my $out_size = scalar @{ $out_constr->{_out_var} };
			$logger->debug("OUT_VAR SIZE: $out_size");
			
			$out_size = scalar @{ $self->{_out_var} };
			$logger->debug("SELF_VAR SIZE: $out_size");
			
			foreach (@{ $self->{_out_var} }) {
				my $var_name = $_->{_name};
				$logger->debug("VAR_NAME: $var_name");
			}
			push( @{ $self->{_constr} }, $out_constr->get_combined_constr() );
		}
	}
	if ($valid_flag == 0) { # @hoang FIXME: temporay disable this check
		$logger->debug("THERE IS NO VALID PATH!");
	}
	$self->unique_out_var();
	#$self->print_myself();
	return $valid_flag;
}

sub get_out_vars {
	my $self = shift;

	return $self->{_out_var};
}

sub add_out_vars {
	my $self         = shift;
	my $ptr_var_list = shift;
	my @var_list     = @{ $ptr_var_list };
	
	push (@{ $self->{_out_var} }, @var_list);
}

sub set_out_vars {
	my $self     = shift;
	my $out_vars = shift;
	
	$self->{_out_var} = $out_vars;
	return;
}

sub get_expect_var {
	my $self = shift;
	
	return $self->{_expect_var};
}

sub set_expect_var {
	my $self     = shift;
	my $var_name = shift;
	
	$self->{_expect_var} = $var_name;
}


sub get_constr {
	my $self = shift;
	
	return $self->{ _constr };
}

sub get_or_constr { # @hoang get_or_constr($space_num)
	my $self      = shift;
	my $space_num = shift;
	
	my @constr = @{ $self->{_constr} };
	
	my $or_constr;
	if ( defined $space_num ) {
		my $space_str = "";
		for ($i = 0; $i < $space_num; $i++) {
			$space_str = $space_str . " ";
		}
		if ( (scalar @constr) > 1 ) {
			$or_constr = "$space_str(or  $space_str" . join( " $space_str", @constr ) . "$space_str)";
		}
		elsif ( (scalar @constr) == 1) {
			my $first_constr = $constr[0];
			$or_constr = "$space_str$first_constr";
		}
		else {
			$or_constr = "";
		}
	}
	else {
		if ( (scalar @constr) > 1 ) {
			$or_constr = "(or " . join( "  ", @constr ) . ")";
		}
		elsif ((scalar @constr) == 1) {
			$or_constr = $constr[1];
		}
		else {
			$or_constr = "";
		}
	}
	
	return $or_constr;
}

# @hoang combine all constraints together
sub get_final_constr {    # get_final_constr( $io_index )
	my $self      = shift;
	my $io_index  = shift;
	
	my @constr = @{ $self->{_constr} };
	my $or_constr = $self->get_or_constr(4);
	
	my $out_size = scalar @{ $self->{_out_var} };
	$logger->debug("GET_OUT_SIZE: $out_size");
	
	#foreach (@{$self->{_expect_var} }) {
	foreach ( (@{ $self->{_out_var} } , @{ $self->{_expect_var} }) ) {
		my $var_name = $_->{_name};
		$logger->debug("\@hoang VAR_NAME: $var_name");
		$logger->debug("OR_CONSTR: $or_constr");
		$or_constr =~ s/(\s+)$var_name(\s+)/$1$var_name\_io$io_index$2/g;
		#$logger->debug("OR_CONSTR: $or_constr");
	}
	
	my @expect_value_constr = ();
	foreach (@{ $self->{_expect_var} }) {
		my $var_name = $_->{_name};
		my $value    = $_->{_value};
		my $type     = $_->{_type};
		
		my $expect_name = $var_name . "\_io$io_index";
		my $expect_value;
		
		if ( $type eq "CHAR" )
		{
			my $ascii_num = ord($value);
			$value = "(_ bv$ascii_num 8)";
			
			$expect_value = Utility::getCHARRepresentation($expect_name);
		}
		elsif ( $type eq "INT" )
		{
			if ( $value < 0 ) {
				$logger->debug("NEGATIVE EXPECT VAL: $value");
				$value = $INT32 + $value;
			}
			$value = "(_ bv$value 32)";
			
			$expect_value = Utility::getINTRepresentation($expect_name);
		}
		else
		{
			die "Unrecognized expect var type in AllOutConstr!\n";
		}	
		
		my $tmp_expect_constr = " (= $expect_value $value)";	
		push(@expect_value_constr, $tmp_expect_constr);
	}
	my $all_expect_constr = join(" ", @expect_value_constr);
	
	if ( (scalar @expect_value_constr) == 0) {
		$all_expect_constr = "true";
	}
	else {
		$all_expect_constr = "(and $all_expect_constr)";
	}
	$final_constr = "(assert (and $or_constr $all_expect_constr) )";

	return $final_constr;
}

sub unique_out_var {
	my $self = shift;
	my @out_var = @{ $self->{_out_var} };
	my @result = ();			#keys %{{ map { $_ => 1 } @out_var }};
	
	my $found = 0;
	foreach (@out_var) {
		$found = 0;
		my $tmp_var = $_;
		foreach(@result) {
			if ($_->{_name} eq $tmp_var->{_name}) {
				$found = 1;
				last;
			}
		}
		if (not $found) {
			push (@result, $tmp_var);
		}
	}
	$self->{_out_var} = \@result;
	return;
}

sub to_string {
	my $self = shift;

	my $result = "";

	$result .= "AllOutConstr output variables\n";
	my @out_vars = @{ $self->{_out_var} };
	foreach (@out_vars) {
		$result .= "varname = $_->{_name}\n";
	}

	my @expect_var = @{ $self->{_expect_var} };
	foreach (@expect_var) {
		$result .= "expect_var = $_->{_name}\n";
	}
	
	my $final_constr = $self->get_final_constr ("stmt", 0);
	
	$result .= "$final_constr\n";
	return $result;
}

1;
