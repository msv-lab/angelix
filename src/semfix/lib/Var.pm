package Var;

# @hoang value constraint for one variable
sub new {
	my $class = shift;
	my $self  = {
		_name     => undef,  # @hoang input variable name
		_is_init  => undef,  # @hoang whether it is initialized or not
		_type     => undef,  # @hoang type of the variable
		_arr_size => undef,  # @hoang only applicable for array
		_value    => undef,  # @hoang concrete value or symbolic value
	};
	bless $self, $class;
	return $self;
}

sub to_string {
	my $self = shift;

	my $result = "";
	
	my $var_name = $self->{_name};
	$result .= "name = $var_name\n";
	
	my $is_init = $self->{_is_init};
	$result .= "is_init = $is_init\n";
	
	my $type = $self->{_type};
	$result .= "type = $type\n";
	
	if ($type eq "AIC" || $type eq "AII") {
		my $size = $self->{_arr_size};
		$result .= "size = $size\n";
	}
	
	my $value = $self->{_value};
	$result .= "value = $value\n";
	
	return $result;
}

1;
