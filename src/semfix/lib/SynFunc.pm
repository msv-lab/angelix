package SynFunc;

use Var;
use Utility;

use Log::Log4perl qw(:easy);
use Data::Dumper;

my $logger = Log::Log4perl->get_logger('semfix');

my $INT32   = 4294967296;
my $MAX_INT = 2147483647;

sub new {
        my $class = shift;
        my $self  = {
                _func_name  => undef,
                _input      => [],      # @hoang statemet input: array of Var
                _output     => [],      # @hoang statement output: array of Var
                _expect_var => [],      # @hoang FIXME: temporary put it here
        };

        bless $self, $class;
        return $self;
}

sub parse {                 # @hoang parse the syn_func signature

        my $self     = shift;
        my $syn_file = shift;

        open( SYN_FUNC, $syn_file ) or die "Cannot open $syn_file for reading\n";

        while ( defined( $line = <SYN_FUNC> ) ) {
                chomp($line);
                if ( $line =~ /^%/ )  {     # a comment line
                        next;
                }
                if ( $line =~ /\@func/ ) {
                        $line = <SYN_FUNC>;
                        if ( $line =~ /^name\s+(\S+)$/ ) {
                                $self->{_func_name} = $1;
                        }
                        else {
                                $logger->warn("function name syntax error!");
                        }
                        last;
                }
        }

        while ( defined( $line = <SYN_FUNC> ) ) {
                chomp($line);
                if ( $line =~ /^%/ ) {         # a comment line
                        next;
                }
                elsif ( $line =~ /\@in/ ) {    # input variable

                        my $in_var = new Var();

                        $line = <SYN_FUNC>;
                        while ($line =~ /^%/) {
                                $line = <SYN_FUNC>;
                        }
                        if ( $line =~ /^name\s+(\S+)$/ ) {
                                $in_var->{_name} = $1;
                        }
                        else {
                                $logger->warn("input var name syntax error!");
                        }

                        $line = <SYN_FUNC>;
                        chomp($line);
                        if ( $line =~ /^type\s+(\S+)$/ ) {
                                $in_var->{_type} = $1;
                        }
                        $self->add_input($in_var);
                }
                elsif ( $line =~ /\@out/ ) {        # @hoang read output variable
                        my $out_var = new Var();
                        $line = <SYN_FUNC>;
                        if ( $line =~ /^name\s+(\S+)$/ ) {
                                $out_var->{_name} = $1;
                        }
                        else {
                                $logger->warn("output var name syntax error!");
                        }

                        $line = <SYN_FUNC>;
                        chomp($line);
                        if ( $line =~ /^type\s+(\S+)$/ ) {
                                $out_var->{_type} = $1;
                        }
                        $self->add_output($out_var);
                }
        }
        close(SYN_FUNC);
}

sub add_input {
        my $self = shift;
        my $var  = shift;

        push (@{ $self->{_input} }, $var);

        return;
}

sub add_output {
        my $self = shift;
        my $var  = shift;

        push (@{ $self->{_output} }, $var);

        return;
}

sub add_expect_var {
        my $self = shift;
        my $var  = shift;

        push (@{ $self->{_expect_var} }, $var);

        return;
}

sub get_all_var {

        my $self      = shift;
        my @tmpout    = ();
        my $func_name = $self->{_func_name};

        # @hoang get input vars
        foreach ( @{ $self->{_input} } ) {
            my $var_name = $self->get_in_var_name($_->{_name});
            push( @tmpout, $var_name );
        }

        # @hoang get output vars
        foreach ( @{ Utility::get_angelic($self->{_output}) } ) {
            my $var_name = $self->get_out_var_name($_->{_name});
            push( @tmpout, $var_name );
        }

        return \@tmpout;
}

sub get_all_input {

        my $self      = shift;
        my @tmpout    = ();
        my $func_name = $self->{_func_name};
        foreach ( @{ $self->{_input} } ) {
            my $raw_name = $_->{_name};
            my $var_name = $self->get_in_var_name($raw_name);
            push( @tmpout, $var_name );
        }
        return \@tmpout;
}

sub get_all_input_type {

        my $self = shift;
        my @type_arr = ();

        foreach ( @{ $self->{_input} } ) {
                my $var_type = $_->{_type};
                push (@type_arr, $var_type);
        }

        return \@type_arr;
}

sub get_all_output_type {

        my $self = shift;
        my @type_arr = ();

        foreach ( @{ $self->{_output} } ) {
                my $var_type = $_->{_type};
                push (@type_arr, $var_type);
        }

        return \@type_arr;
}

sub get_all_output {

        my $self = shift;
        my @tmpout    = ();

        my $func_name = $self->{_func_name};
        foreach ( @{ $self->{_output} } ) {
            my $raw_name = $_->{_name};
            my $var_name = $self->get_out_var_name($raw_name);
            push( @tmpout, $var_name );
        }

        return \@tmpout;
}

sub get_all_var_type {

    my $self = shift;
    my @type_arr = ();

    foreach ( (@{ $self->{_input} }, @{ Utility::get_angelic($self->{_output}) } ) ) {
        my $var_type = $self->get_actual_type($_->{_type});
        push (@type_arr, $var_type);
    }
    return \@type_arr;
}

sub get_angelic_out {
    my $p_outputs = shift;
    my @outputs = @{$p_outputs};

    # $logger->debug("outputs: " . Dumper(@outputs));

    # my $outputs_size = scalar @outputs;
    # $logger->debug("outputs_size: $outputs_size");

    my @filtered = ();
    foreach my $output (@outputs) {
        $logger->debug("output: " . Dumper($output));
        if ($output =~ /!angelic_out$/) {
            push (@filtered, $output);
        }
    }

    return \@filtered;
}

sub get_decls {  #($ins_index, $io_index)

        my $self      = shift;
        my $ins_index = shift;
        my $io_index  = shift;

        $logger->debug("SynFunc.get_decls called");

        my $func_name = $self->{_func_name};

        my @decl      = ();

        # @hoang input declaration
        foreach ( @{ $self->{_input} } )
        {
            my $raw_var_name  = $_->{_name};
            my $var_type = $self->get_actual_type($_->{_type});

            $var_name = $self->get_in_var_name($raw_var_name) . "\_ins$ins_index\_io$io_index";
            my $var_decl = Utility::getDeclare($var_name, $var_type);
            push( @decl, $var_decl );

            if ( $var_type eq "AIC"
                 || $var_type eq "AII")
            {
                my $len_name = $self->get_in_var_name($raw_var_name) .
                    "\_len\_ins$ins_index\_io$io_index";
                my $len_decl = Utility::getDeclare($len_name, "INT");
                push ( @decl, $len_decl );
            }
        }

        # output declaration
        foreach ( @{ Utility::get_angelic($self->{_output}) } ) {
            my $raw_var_name  = $_->{_name};
            my $var_type = $self->get_actual_type($_->{_type});

            $var_name = $self->get_out_var_name($raw_var_name) . "\_ins$ins_index\_io$io_index";
            my $var_decl = Utility::getDeclare($var_name, $var_type);
            $logger->debug("var_decl: $var_decl");
            push( @decl, $var_decl );
        }

        # @hoang expect_var declaration
        if ( $ins_index == 0 ) {   # @hoang expect_var is only declared once    TODO: test this code
                foreach ( @{ $self->{_expect_var} } ) {
                        my $var_name  = $_->{_name};
                        my $var_type = $_->{_type};

                        $var_name = "$var_name\_io$io_index";
                        my $var_decl = Utility::getDeclare($var_name, $var_type);
                        push( @decl, $var_decl );
                }
        }

        return join( "", @decl );
}

sub get_actual_type {
    my $self = shift;
    my $raw_type = shift;
    my $synfunc_type;

    if ($raw_type eq "BOOL") {
        $synfunc_type = "Bool";
    } else {
        $synfunc_type = $raw_type;
    }

    return $synfunc_type;
}

sub get_expect_out_decls {
        my $self     = shift;
        my $io_index = shift;

        my @decl = ();
        foreach ( @{ $self->{_expect_var} } ) {
                my $var_name  = $_->{_name};
                my $var_type = $_->{_type};

                $var_name = "$var_name\_io$io_index";
                my $var_decl = Utility::getDeclare($var_name, $var_type);
                push( @decl, $var_decl );
        }

        return join( "", @decl);
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

        # @hoang declare input location
        foreach ( @{ $self->{_input} } ) {
            my $loc_var = "l_" . $self->get_in_var_name($_->{_name}) . $suffix;
            my $loc_decl = Utility::getDeclare($loc_var, "Int");

            push( @decl, $loc_decl );
        }

        # @hoang declare output location
        foreach ( @{ Utility::get_angelic($self->{_output}) } ) {
            my $loc_var = "l_" . $self->get_out_var_name($_->{_name}) . $suffix;
            my $loc_decl = Utility::getDeclare($loc_var, "Int");

            push( @decl, $loc_decl );
        }

        return join( "", @decl );
}

sub get_in_var_name {
    my $self = shift;
    my $raw_name = shift;

    $logger->debug("raw_name; $raw_name");
    my $var_name = $raw_name;
    $var_name =~ s/\d+!env!/env!/;
    # $logger->debug("var_name; $var_name");

    return "$self->{_func_name}\_$var_name\_in";
}

sub get_out_var_name {
    my $self = shift;
    my $raw_name = shift;

    # $logger->debug("raw_name; $raw_name");
    my $var_name = $raw_name;
    $var_name =~ s/\d+!angelic/angelic/;
    # $logger->debug("var_name; $var_name");

    return "$self->{_func_name}\_$var_name\_out";
}

sub get_all_loc_var {

        my $self = shift;
        my @tmp;
        my $func_name = $self->{_func_name};

        # @hoang input location vars
        foreach ( @{ $self->{_input} } ) {
                my $var_name = $_->{_name};
                $var_name     = "$func_name\_$var_name\_in";
                my $loc_var = "l_" . $var_name;
                push( @tmp, $loc_var );
        }

        # @hoang output location vars
        foreach ( @{ $self->{_stmt_output} } ) {
                my $var_name = $_->{_name};
                $var_name    = "$func_name\_$var_name\_out";
                my $loc_var = "l_" . $var_name;
                push( @tmp, $loc_var );
        }
        return \@tmp;
}

sub create_io_connection {       # ($io_behavior, $io_index)

        my $self         = shift;
        my $io_behavior  = shift;
        my $io_index     = shift;

        my @constr    = ();

        my @io_pairs = @{ $io_behavior->get_io_pairs() };
        my $out_constr = $io_behavior->get_out_constr();
        my @symbol_vars = @{ $out_constr->get_out_vars() };

        my $ins_num = $io_behavior->get_instance_num();

        $logger->debug("ins_num: $ins_num");
        for( $i = 0; $i < $ins_num; $i++ ) {
            my $io_pair = $io_pairs[$i];

            my $tmp_constr = $self->io_for_one_instance($io_pair, $i, $io_index, \@symbol_vars);
            unless ( defined $tmp_constr ) { #FIXME : temporary ignore this situation
                print STDERR "Warning: return a junk value in create_io_connection\n";
                my $junk;
                return $junk;
            }
            # $logger->debug("tmp_constr: $tmp_constr");
            push (@constr, $tmp_constr);
        }

        return join( "", @constr);
}

sub io_for_one_instance { # @hoang ($io_pair, $ins_index, $io_index, $symbol_vars)
    my $self      = shift;
    my $io_pair   = shift;
    my $ins_index = shift;
    my $io_index  = shift;
    my $ptr_symbol_vars = shift;
    my @symbol_vars = @{ $ptr_symbol_vars };

    my @constr   = ();
    my $func_name = $self->{_func_name};

    my @in_vars  = @{ $io_pair->get_in_vars() };
    my @out_vars = @{ Utility::get_angelic($io_pair->{_out_vars}) };
    # $logger->debug("out_vars:" . Dumper(@out_vars));

    if ( (scalar @in_vars) != (scalar @{ $self->{_input} }) ) {
        # $logger->debug("in_vars: " . Dumper(@in_vars));
        # $logger->debug("_input: " . Dumper($self->{_input}));
        #die "in_vars and syn_input_arr are mismatch!\n"; #FIXME: temporary disable this check
        $logger->debug("Warning: return a junk value in io_for_one_instance!");
        print STDERR "Warning: return a junk value in io_for_one_instance!\n";
        my $junk;
        return $junk;
    }
    # $logger->debug("_output: " . Dumper(Utility::get_angelic($self->{_output})));
    if ( (scalar @out_vars) != (scalar @{ Utility::get_angelic($self->{_output}) }) ) {
        die "out_vars and syn_output_arr are mismatch!\n";
    }

    # connection for input
    $logger->debug("all_input: " . Dumper($self->{_input}));
    foreach ( @{ $self->{_input} } ) {
        my $stmt_var = $_;
        my $in_var = shift @in_vars;
        my $in_name = $in_var->{_name};
        # $logger->debug("in_name: $in_name");

        $stmt_var_name = $self->get_in_var_name($stmt_var->{_name});
        my $syn_var_name = $stmt_var_name . "\_ins$ins_index\_io$io_index";
        $in_name = "$in_name\_io$io_index";

        my $syn_var_constr =
            $self->get_representation($self->get_actual_type($in_var->{_type}),
                                      $syn_var_name);
        my $in_value = $self->get_representation($in_var->{_type}, $in_name);

        push( @constr, "(assert (= $syn_var_constr $in_value))\n" );
    }

    # $logger->debug("all_output:" . Dumper(Utility::get_angelic_out($self->get_all_output())));
    foreach ( @{ Utility::get_angelic_out($self->get_all_output()) } ) {
        my $out_var = shift @out_vars;
        my $out_name = $out_var->{_name};
        # $logger->debug("out_name: $out_name");

        my $syn_var_name = "$_\_ins$ins_index\_io$io_index";
        $out_name = "$out_name\_io$io_index";

        my $syn_var_constr =
            $self->get_representation($self->get_actual_type($out_var->{_type}),
                                      $syn_var_name);
        my $out_value =
            $self->get_representation($out_var->{_type}, $out_name);

        # $logger->debug("assertion: (= $syn_var_constr $out_value)");
        push( @constr, "(assert (= $syn_var_constr $out_value))\n" );

        # $logger->debug("out_type: " . $out_var->{_type});
        # if ( $out_var->{_type} eq "BOOL" ) {
        #     my $rep = Utility::getINTRepresentation($out_name);
        #     push ( @constr,
        #            "(assert (=> (not (= (_ bv0 32) " . $rep . ")) "
        #            . "(= (_ bv1 32) " . $rep ."))) \n" );
        # }
    }

    return join( "", @constr);
}

sub get_representation {
    my $self = shift;
    my $var_type = shift;
    my $var_name = shift;

    my $rep;
    if ($var_type eq "Bool")
    {
        $rep = $var_name;
    }
    elsif ($var_type eq "CHAR")
    {
        $rep = Utility::getCHARRepresentation($var_name);
    }
    elsif ($var_type eq "INT")
    {
        $rep = Utility::getINTRepresentation($var_name);
    }
    elsif ($var_type eq "BOOL") {
        $rep = Utility::getINTRepresentation($var_name);
        $rep = "(not (= (_ bv0 32) $rep ))";
    }
    elsif ($var_type eq "AIC"
           || $var_type eq "AII")
    {
        $rep = $var_name;
    }
    else
    {
        $logger->logdie("Unrecognized var_type in syn_func: $var_type");
    }

    return $rep;
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
        my $func_name  = $self->{_name};
        my @output_arr = @{ $self->{_output} };
        my $out_var    = $output_arr[0];
        foreach ( @{ $self->{_input} } ) {
                my $var_name = $_->{_name};
                push( @constr, "(assert (< l_$func_name\_$var_name$suffix l_$func_name\_$out_var$suffix))\n" );
        }
        return join( "", @constr );
}

sub get_output_type
{
        my $self     = shift;
        my @out_vars = @{ $self->{_output}};

        return $out_vars[0]->{_type};
}

sub print_myself {

        my $self     = shift;
        my $syn_file = shift;

        my $out = STDOUT;
        if ( defined $syn_file ) {
                open(SYN_FUNC, ">$syn_file") or die "Cannot open $syn_file for read!\n";
                $out = SYN_FUNC;
        }

        print $out "% synthesizied function\n";

        my $func_name = $self->{_func_name};
        print $out "\@func\n";
        print $out "name $func_name\n\n";

        # @hoang print input
        foreach ( @{ $self->{_input} }) {
                my $var_name  = $_->{_name};
                my $var_type = $_->{_type};

                print $out "\@in\n";
                print $out "name $var_name\n";
                print $out "type $var_type\n\n";
        }

        # @hoang print output
        foreach ( @{ $self->{_output} }) {
                my $var_name = $_->{_name};
                my $var_type = $_->{_type};

                print $out "\@out\n";
                print $out "name $var_name\n";
                print $out "type $var_type\n\n";
        }

        # @hoang print expect var
        #foreach ( @{ $self->{_expect_var} } ) {
        #	my $var_name = $_->{_name};
        #	my $var_type = $_->{_type};
        #
        #	print $out "\@out\n";
        #	print $out "name $var_name\n";
        #	print $out "type $var_type\n\n";
        #}

        close(SYN_FUNC);
        return;
}

sub DEBUG_PRINT {
        if (0) {
                print STDERR @_;
                print "\n";
        }
}

1;
