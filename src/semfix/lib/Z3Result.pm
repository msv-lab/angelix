package Z3Result;

my $MAX_POSITIVE = 2147483647;
my $INT32		 = 4294967296;

sub new {
	my $class = shift;
	my $raw   = shift;
	my $self  = {
		_raw    => [],
		_varval => {},        # @hoang hash table
		_is_sat => undef,
	};

	#    my %tmphash = ();
	#    $self->{_varval} = \%tmphash;
	if ( defined($raw) ) {
		$self->parse($raw);
	}
	bless $self, $class;
	return $self;
}

sub get_val {
	my $self = shift;
	my $var  = shift;

	#my %tmp = %{$self->{_varval}};
	return ${ $self->{_varval} }{$var};
}

sub parse {
	my $self = shift;
	my $raw  = shift;
	$self->{_raw} = $raw;
	my @all_lines     = split( "\n", $raw );
	my %valhash       = %{ $self->{_varval} };
	my %array_valhash = ();
	while ( defined( $line = shift(@all_lines) ) ) {
		if ( $line =~ /unsat/ ) {
			$self->{_is_sat} = 0;
			last;
		}

		if ( $line =~ /error/ ) {
			return 1;
		}
		if ( $line =~ /define\s+(\S+)\s+(\S+)\)/ ) {

			# TODO do not include the variables starting with proxy
			# print STDERR "adding $1 $2 to value hash \n";
			my $var   = $1;
			my $value = $2;
			unless ( $var =~ /proxy!\d+/ ) {
				if ($value =~ /^bv(\d+)\[\d+\]$/) {
					$value = $1;
				}
				${ $self->{_varval} }{$var} = $value;
			}
		}
		elsif ( $line =~ /define\s+\((k!\d+)/ ) {
			my $arr_var = $1;
			my @prefix  = ();
			my @suffix  = ();
			my $real_var = 0;
			while ( defined( $line = shift(@all_lines) ) ) {
				
				if ( $line =~ /if\s+\(=\s+\S+\s+bv(\d+)\[\d+\]\)\s+bv(\d+)\[8\]/ ) {
					my $index     = $1 + 0;
					my $index_val = $2 + 0;
					
					$real_var += $index_val * (256 ** $index);
				}
				elsif ( $line =~ /^\s+(bv\d+\[8\])\)/ ) {
					if ($real_var > $MAX_POSITIVE) {
						$real_var = $real_var - $INT32;
					}
					$array_valhash{$arr_var} = $real_var;

					# print STDERR "putting $arr_var => $z3arrayval into array_valhash \n";
					last;
				}
			}
		}
	}

	if ( not defined( $self->{_is_sat} ) ) {
		$self->{_is_sat} = 1;
	}

	# replace array with real value
	while ( my ( $key, $value ) = each %{ $self->{_varval} } ) {
		if ( $value =~ /as-array\[(.*)\]/ ) {

			#print STDERR "key and value containing array is $key => $value \n";
			my $arrfun   = $1;
			my $real_val = $array_valhash{$arrfun};

			#        print STDERR "replacing $key $arrfun to $real_val \n";
			$self->{_varval}{$key} = $real_val;
		}
		elsif ( $value =~ /bv\d+\[\d+\]/ ) {
			$value =~ s/bv\d+\[(\d+)\]/$1/g;
			$self->{_varval}{$key} = $value;
		}
		elsif ( $value =~ /true/) {
			$self->{_varval}{$key} = 1;
		}
		elsif ( $value =~ /false/ ) {
			$self->{_varval}{$key} = 0;
		}
		else {
			if ( $value =~ /(.*\d+)/ ) {
				$self->{_varval}{$key} = $1;
			}
			else {
				die "Unknown format in Z3 Result!\n";
			}
			
		}
	}

	#while(my ($key, $value) = each %{$self->{_varval}}){
	#    print STDERR "$key => $value \n";
	#}

	return 0;
}

sub is_sat {
	my $self = shift;
	return $self->{_is_sat};
}

1;
