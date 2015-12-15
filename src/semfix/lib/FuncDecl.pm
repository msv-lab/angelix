package FuncDecl;

use Var;
use Utility;

my $logger = Log::Log4perl->get_logger('semfix');

sub new {
        my $class = shift;
        my $self  = {
                _func_name   => undef,
                _input       => [],  # @hoang an array of Var
                _param       => [],  # @hoang an array of Var
                _output      => [],  # @hoang an array of Var

                _impl        => undef,
                _constraint  => undef,

        };

        bless $self, $class;
        return $self;
}

sub add_input {
        my $self = shift;
        my $var  = shift;

        push (@{$self->{_input}}, $var);

        return;
}

sub add_param {
        my $self = shift;
        my $var  = shift;

        push (@{$self->{_param}}, $var);

        return;
}

sub add_output {
        my $self = shift;
        my $var  = shift;

        push (@{$self->{_output}}, $var);

        return;
}

sub parse_impl {
        my $self = shift;
        my $line = shift;

        #TODO
        $self->{_impl} = $line;
        return;
}

sub get_impl {
        my $self = shift;
        my $impl = $self->{_impl};
        $impl =~ s/\\n/\n/g;
        $impl =~ s/\\t/\ \ \ \ /g;
        return $impl;
}

sub get_angelic {
    my $p_outputs = shift;
    my @outputs = @{$p_outputs};

    # $logger->debug("outputs: " . Dumper(@outputs));

    # my $outputs_size = scalar @outputs;
    # $logger->debug("outputs_size: $outputs_size");

    my @filtered = ();
    foreach my $output (@outputs) {
        # $logger->debug("output: " . Dumper($output));
        my $var_name  = $output->{_name};
        if ($var_name =~ /!angelic$/) {
            push (@filtered, $output);
        }
    }

    return \@filtered;
}

sub get_all_var {
        my $self      = shift;
        my @tmpout    = ();
        my $func_name = $self->{_func_name};

        foreach ( ( @{ $self->{_input} }, @{ $self->{_output} } ) ) {
                my $var_name = $_->{_name};
                push( @tmpout, "$func_name\_$var_name" );
        }

        return \@tmpout;
}

sub get_all_input {
        my $self      = shift;
        my @tmpout    = ();
        my $func_name = $self->{_func_name};

        foreach ( @{ $self->{_input} } ) {
                my $var_name = $_->{_name};
                DEBUG_PRINT("VARNAME: $var_name");
                push( @tmpout, "$func_name\_$var_name" );
        }

        return \@tmpout;
}

sub get_all_input_type {
        my $self      = shift;
        my @tmpout    = ();

        foreach ( @{ $self->{_input} } ) {
                my $var_type = $_->{_type};
                push( @tmpout, $var_type);
        }

        return \@tmpout;
}

sub get_all_output_type {
        my $self      = shift;
        my @tmpout    = ();

        foreach ( @{ $self->{_output} } ) {
                my $var_type = $_->{_type};
                push( @tmpout, $var_type);
        }

        return \@tmpout;
}

sub get_all_output {
        my $self = shift;

        my @tmpout    = ();
        my $func_name = $self->{_func_name};

        foreach ( @{ $self->{_output} } ) {
                my $var_name = $_->{_name};
                DEBUG_PRINT("VARNAME: $var_name");
                push( @tmpout, "$func_name\_$var_name" );
        }
        return \@tmpout;
}

sub get_all_var_type {
        my $self = shift;

        my @tmpout = ();
        foreach ( ( @{ $self->{_input} }, @{ $self->{_output} } ) ) {
                my $var_type = $_->{_type};
                push( @tmpout, $var_type );
        }

        return \@tmpout;
}

sub get_decls {  # ($ins_index, $io_index)

        my $self      = shift;
        my $ins_index = shift;
        my $io_index  = shift;

        $logger->debug("FuncDecl.get_decls called");

        my @decl      = ();
        my $func_name = $self->{_func_name};

        foreach ( ( @{ $self->{_input} }, @{ $self->{_output} } ) ) {
                my $var      = $_;
                my $var_name = $var->{_name};
                my $var_type = $var->{_type};
                my $var_decl;

                my $new_var_name = "$func_name\_$var_name\_ins$ins_index\_io$io_index";
                $var_decl = Utility::getDeclare($new_var_name, $var_type);
                push( @decl, $var_decl );

                if ( $var_type eq "AIC"
                        || $var_type eq "AII")			#@hoang add length declaration
                {
                        my $len_name = "$func_name\_$var_name\_len\_ins$ins_index\_io$io_index";
                        my $len_type = "INT";
                        my $len_decl = Utility::getDeclare($len_name, $len_type);
                        push (@decl, $len_decl);
                }
        }
        return join( "", @decl );
}

sub get_loc_var_decl {
        my $self   = shift;
        my $suffix = shift;		# optional parameter

        if ( not defined($suffix) ) {
                $suffix = "";
        }
        else {
                $suffix = "_" . $suffix;
        }

        my @decl      = ();
        my $func_name = $self->{_func_name};
        foreach ( ( @{ $self->{_input} }, @{ $self->{_output} } ) ) {
                my $var_name = $_->{_name};
                $var_name    = "$func_name\_$var_name";
                my $loc_var = "l_" . $var_name . $suffix;

                my $loc_type = Utility::getDeclare($loc_var, "Int");
                push( @decl, $loc_type );
        }
        return join( "", @decl );
}

sub get_all_loc_var {
        my $self = shift;
        my @tmp = ();

        my $func_name = $self->{_func_name};
        foreach ( ( @{ $self->{_input} }, @{ $self->{_output} } ) ) {
                my $var_name =  $_->{_name};
                $var_name    = "$func_name\_$var_name";
                my $loc_var = "l_" . $var_name;
                push( @tmp, $loc_var );
        }
        return \@tmp;
}

sub get_param {
        my $self = shift;
        my @tmp;

        my $func_name = $self->{_func_name};
        foreach ( @{ $self->{_param} } ) {
                my $var_name = $_->{_name};
                $var_name = "$func_name\_$var_name";
                push( @tmp, $var_name );
        }

        return \@tmp;
}

sub get_param_type {
        my $self = shift;

        my @tmpout = ();
        foreach ( @{ $self->{_param} }) {
                my $var_type = $_->{_type};
                push (@tmpout, $var_type);
        }

        return \@tmpout
}

sub get_constraint {          # @hoang get_constraint($ins_index, $io_index)

        my $self      = shift;
        my $ins_index = shift;
        my $io_index  = shift;
        my $tmp       = $self->{_constraint};
        my $func_name = $self->{_func_name};
        my @param     = @{ $self->{_param} };

        foreach (@param) {
                my $tmp_param_name = $_->{_name};
                $tmp =~ s/$tmp_param_name/p_$func_name\_$tmp_param_name/g;
        }

        $tmp =~ s/(x\d+\_len)/$func_name\_$1\_ins$ins_index\_io$io_index/g;
        $tmp =~ s/(x\d+)([^\_])/$func_name\_$1\_ins$ins_index\_io$io_index$2/g;

        return "(assert $tmp)\n";
}

sub get_acyc_constraint {
        my $self   = shift;
        my $suffix = shift;
        if ( not defined($suffix) ) {
                $suffix = "";
        }
        else {
                $suffix = "_" . $suffix;
        }

        my @constr     = ();
        my $func_name  = $self->{_func_name};
        my @output     = @{ $self->{_output} };
        my $out_var    = $output[0]->{_name};
        foreach ( @{ $self->{_input} } ) {
                my $in_name = $_->{_name};
                push( @constr, "(assert (< l_$func_name\_$in_name$suffix l_$func_name\_$out_var$suffix))\n" );
        }

        return join( "", @constr );
}

sub print_myself {
        my $self     = shift;
        my $funcname = $self->{_func_name};

        print "fun $funcname:1\n";

        foreach ( @{ $self->{_input} } ) {
                my $input_name = $_->{_name};
                my $input_type = $_->{_type};
                print "in $input_name:($input_type)\n";
        }

        foreach ( @{ $self->{_output} } ) {
                my $output_name = $_->{_name};
                my $output_type = $_->{_type};
                print "out $output_name:($output_type)\n";
        }
        if ( defined $self->{_impl} ) {
                print "impl ";
                print $self->{_impl} . "\n";
        }

        if ( defined $self->{_constraint} ) {
                print $self->{_constraint} . "\n";
        }

        foreach ( @{ $self->{_param} } ) {
                my $param_name = $_->{_name};
                print "param $param_name\n";
        }
}

sub DEBUG_PRINT {
        if (0) {
                print STDERR "@_\n";
        }
}

1;
