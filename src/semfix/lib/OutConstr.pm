package OutConstr;

use Var;
use Log::Log4perl qw(:easy);
my $logger = Log::Log4perl->get_logger('semfix.test');

sub new {
	my $class = shift;
	my $self  = {
		_out_var    => [],     # @hoang output variable name
		_expect_var => [],     # @hoang expect variable name
		_constr     => []      # @hoang constraints for output's value
	};

	bless $self, $class;
	return $self;
}

sub parse {  # @hoang parse the smt2 file
	my $self     = shift;
	my $smt_file = shift;

	open (SMT2, $smt_file) or die "Can not open $smt_file for reading\n";
	
	while ( defined ( $line = <SMT2> ) )
	{
		chomp($line);

		# @hoang variable declaration
		if ( $line =~ /^\(declare-fun\s+(\S+)\s+/ ) {
			my $temp_var = $1;
			$temp_var =~ s/(\S+)\_0x\S+$/$1/g; # @hoang remove address suffix;
			
			if ( $temp_var =~ /smt__tmpInt/ ) {
				# temporary variables don't do anything
				next;
			} 
			elsif ( $temp_var =~ /expect\_out\_\d+/ )  {
				my @expect_var = @{ $self->{_expect_var} };
				my $duplicated = 0;
				foreach (@expect_var) {
					if ($_ eq $temp_var) {
						$duplicated = 1;
					}
				}
				unless ($duplicated) {
					push ( @{ $self->{_expect_var} }, $temp_var);
				}
				
			}
			else {
				my $var = new Var();
				$var->{_name} = $temp_var;
				push(@{ $self->{_out_var} }, $var);;
			}
		}
		elsif ($line =~ /^\(assert\s+(\S+.*)\)$/ ) {
			my $constr = $1;
			
			$constr =~ s/(\S+)\_0x\S+/$1/g; # @hoang remove address suffix;
			
			if ($constr =~ /(tmp___cond\S*)/ ) {
				#$logger->debug("CONST: $constr");
				my $tmp_var = $1;
				#$logger->debug("TMP_VAR: $tmp_var");
				if ($constr =~ /^\(not/) {
					#$logger->debug("Negate:");
					$constr = "( $tmp_var )";
					#$logger->debug("CONST: $constr");
				}
				else {
					$constr = "(not $tmp_var )";
					#$logger->debug("CONST: $constr");
				}
			}
			
			$self->add_constr($constr);
		}
	}
	
	close(SMT2);
}

sub add_out_var {
	my $self     = shift;
	my $var_name = shift;
	
	push( @{ $self->{_out_var} }, $var_name);
}

sub add_constr {
	my $self   = shift;
	my $constr = shift;
	
	push ( @{ $self->{_constr} }, $constr);
}

# @hoang return a list of constraints
sub get_constr {
	my $self = shift;
	
	return @{ $self->{_constr} };
}

# @hoang combine all constraints together
sub get_combined_constr {
	my $self   = shift;
	my @constr = $self->get_constr;
	 
	my $constraint = "(and " . join (" ", @constr) . ")";
	return $constraint;
}

sub is_valid { # $expect_out_size
	my $self = shift;
	my $expect_out_size = shift;
	
	my @expect_var = @{ $self->{_expect_var} };
	my @constr = @{ $self->{_constr} };
	
	$logger->debug("LIST OF EXPECT VAR");
	foreach(@expect_var) {
		$logger->debug("EXP_VAR: $_");
	}
	
	my $found = 0;
	my $lookup_var = "expect\_out\_" . $expect_out_size;
	foreach (@constr) {
		if ( /$lookup_var\s+/ ) {
			$found = 1;
			last;
		}
	}
	if ($found == 1) {
		foreach(@expect_var) {
			my $varname = $_;
			$varname =~ /expect\_out\_(\d+)/;
			my $index = $1 + 0;
			if ($index > $expect_out_size) {
				foreach (@constr) {
					my $overflow_var = "expect\_out\_" . $index;
					if ( /$overflow_var\s+/ ) {
						return 0;
					}
				}
			}
		}
		return 1;
	}
	else {
		if ($expect_out_size == 0) {
			foreach(@expect_var) {
				my $varname = $_;
				$varname =~ /expect\_out\_(\d+)/;
				my $index = $1 + 0;
				if ($index > $expect_out_size) {
					foreach (@constr) {
						my $overflow_var = "expect\_out\_" . $index;
						if ( /$overflow_var\s+/ ) {
							return 0;
						}
					}
				}
			}
			return 1;
		}
		else {
			return 0;
		}
		
	}
}

sub print_myself {
	my $self = shift;
	
	my @out_var = @{ $self->{_out_var} };
	print "out_var\n";
	foreach (@out_var) {
		print "varname = $_\n";
	}
	
	my @expect_var = @{ $self->{_expect_var} };
	foreach (@expect_var) {
		print "expect_var = $_\n";
	}
	
	my $combined_constr = $self->get_combined_constr();
	print "$combined_constr\n";
}

1;
