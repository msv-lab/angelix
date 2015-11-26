package IOPair;

sub new {
	my $class = shift;
	my $self  = {
		_input      => undef,
		_output     => [],
		_raw_output => undef,  # @hoang for checking result
		_index		=> undef,  # @hoang index needed for mapping
		_status		=> undef,  # @hoang pass or fail
		_included   => 0,      # @hoang for identify whether it is included in synthesizing constraint 
	};
	bless $self, $class;
	return $self;
}

sub print_myself {
	my $self = shift;
	print "\@io\n";
	print "in: ";
	my $input = $self->{_input};
	print "$input\n";
	
	my @output = @{ $self->{_output} };
	
	print "out: @output\n";
}


1;
