package Categorization;

use Log::Log4perl qw(:easy);
use Data::Dumper;

my $logger = Log::Log4perl->get_logger('semfix');

sub new {
        my $class = shift;
        my $self  = {
                _bool_const   => [],
                _char_const   => [],
                _int_const    => [],

                _char_compare => [],
                _int_compare  => [],

                _add_sub      => [],
                _logic        => [],
                _ite          => [],
                _int_array    => [],
                _char_array   => [],
                _conversion   => [],
                _multi        => [],
        };

        bless $self, $class;
        return $self;
}

sub add_func {
        my $self  = shift;
        my $func  = shift;

        my $func_name = $func->{_func_name};

        if ($func_name eq "boolc") {
                push (@{ $self->{_bool_const} }, $func);
        }
        elsif ($func_name =~ /charc/) {
                push (@{ $self->{_char_const} }, $func);
        }
        elsif ($func_name eq "charIdent") {
                push (@{ $self->{_char_const} }, $func);
        }
        elsif ($func_name =~ /const/) {
                push (@{ $self->{_int_const} }, $func);
        }
        elsif ($func_name eq "intIdent") {
                push (@{ $self->{_int_const} }, $func);
        }
        elsif ($func_name eq "intAt") {
                push (@{ $self->{_int_array} }, $func);
        }
        elsif ($func_name eq "neq"
                        || $func_name eq "eq"
                        || $func_name eq "gt"
                        || $func_name eq "ge"
                        || $func_name eq "lt"
                        || $func_name eq "le") {
                push (@{$self->{_int_compare}}, $func);
        }
        elsif ($func_name eq "add"
                        || $func_name eq "sub") {
                push (@{ $self->{_add_sub} }, $func);
        }
        elsif ($func_name eq "and"
                        || $func_name eq "or") {
                push (@{ $self->{_logic} }, $func);
        }
        elsif ($func_name eq "charAt" ) {
                push (@{ $self->{_char_array} }, $func);
        }
        elsif ($func_name eq "charEq" ) {
                push (@{ $self->{_char_compare} }, $func);
        }
        elsif ($func_name eq "ifelse" ) {
                push (@{ $self->{_ite} }, $func);
        }
        elsif ($func_name eq "mul" ) {
                push (@{ $self->{_multi} }, $func);
        }
        elsif ($func_name eq "int2char" ) {
                push (@{ $self->{_conversion} }, $func);
        }
        elsif ($func_name eq "stringIdent") {
                push (@{ $self->{_char_array} }, $func)
        }
        else {
                print STDERR "FUNCTION $func_name HAS NOT BE CATEGORIZED!\n";
        }
        return;
}

sub get_all_func {
        my $self = shift;

        my @all_func = ();

        push (@all_func, @{ $self->{_bool_const} });
        push (@all_func, @{ $self->{_char_const} });
        push (@all_func, @{ $self->{_int_const} });
        push (@all_func, @{ $self->{_int_compare} });
        push (@all_func, @{ $self->{_char_compare} });
        push (@all_func, @{ $self->{_add_sub} });
        push (@all_func, @{ $self->{_logic} });
        push (@all_func, @{ $self->{_ite} });
        push (@all_func, @{ $self->{_int_array} });
        push (@all_func, @{ $self->{_conversion} });
        push (@all_func, @{ $self->{_multi} });
        push (@all_func, @{ $self->{_char_array} });
        return \@all_func;
}
=comment
sub select_funcs {  # @hoang hack here
        my $self            = shift;
        my $stmt_kind       = shift; # cond, assign, return
        my $energy_level    = shift; # 1, 2, 3, 4, 5, 6
        my $secondary_level = shift;
        my $char_stmt       = shift; # TODO: used later
        my @select_func = ();

        #push (@select_func, @ { $self->{_bool_const} });
        #push (@select_func, @ { $self->{_char_const} });
        #push (@select_func, @ { $self->{_char_compare}});
        push (@select_func, @ { $self->{_char_array} });
        #push (@select_func, @ { $self->{_int_const} });
        #push (@select_func, @ { $self->{_int_array} });
        #push (@select_func, @ { $self->{_add_sub} });
        #push (@select_func, @ { $self->{_int_compare} });
        return \@select_func;
}
=cut

sub select_funcs {
    my $self            = shift;
    my $stmt_kind       = shift; # cond, non-cond
    my $energy_level    = shift; # 1, 2, 3, 4, 5, 6
    my $secondary_level = shift;
    my $out_type        = shift; # TODO: used later

    my @select_funcs = ();

    if ( (defined $out_type) && ($out_type eq 'AIC') )	#@hoang handle string
    {
        push (@select_funcs, @{ $self->{_char_array} });
        return \@select_funcs;
    }
    if ( $stmt_kind eq "cond" ) {
        if ( $energy_level == 1 ) {
            push (@select_funcs, @{ $self->{_bool_const} });
        }
        elsif ( $energy_level == 2 ) {
            my @int_const = @{ $self->{_int_const} };
            push (@select_funcs, $int_const[0] );

            if ($secondary_level == 0) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "lt"
                        || $_->{_func_name} eq "le" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "eq"
                        || $_->{_func_name} eq "ne" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_int_compare} });
            }
        }
        elsif ( $energy_level == 3 ) {
            push (@select_funcs, @{ $self->{_int_const} });

            if ($secondary_level == 0) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "lt"
                        || $_->{_func_name} eq "le" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "eq"
                        || $_->{_func_name} eq "ne" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_int_compare} });
            }

            push (@select_funcs, @{ $self->{_logic} });
        }
        elsif ( $energy_level == 4) {
            push (@select_funcs, @{ $self->{_int_const} });
            if ($secondary_level == 0) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "lt"
                        || $_->{_func_name} eq "le" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "eq"
                        || $_->{_func_name} eq "ne" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_int_compare} });
            }

            push (@select_funcs, @{ $self->{_logic} });
            if ($secondary_level == 0) {
                foreach ( @{ $self->{_add_sub} } ) {
                    if ( $_->{_func_name} eq "add" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_add_sub} } ) {
                    if ( $_->{_func_name} eq "sub" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_add_sub} });
            }
        }
        elsif ( $energy_level == 5) {
            push (@select_funcs, @{ $self->{_int_const} });
            if ($secondary_level == 0) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "lt"
                        || $_->{_func_name} eq "le" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_int_compare} } ) {
                    if ($_->{_func_name} eq "eq"
                        || $_->{_func_name} eq "ne" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_int_compare} });
            }

            push (@select_funcs, @{ $self->{_logic} });
            if ($secondary_level == 0) {
                foreach ( @{ $self->{_add_sub} } ) {
                    if ( $_->{_func_name} eq "add" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            elsif ($secondary_level == 1) {
                foreach ( @{ $self->{_add_sub} } ) {
                    if ( $_->{_func_name} eq "sub" ) {
                        push (@select_funcs, $_);
                    }
                }
            }
            else {
                push (@select_funcs, @{ $self->{_add_sub} });
            }

            push (@select_funcs, @{ $self->{_ite} });
        }
        elsif ( $energy_level == 6 ) {
            push (@select_funcs, @{ $self->{_int_const} });
            push (@select_funcs, @{ $self->{_int_compare} });
            push (@select_funcs, @{ $self->{_logic} });
            push (@select_funcs, @{ $self->{_add_sub} });
            push (@select_funcs, @{ $self->{_ite} });
            push (@select_funcs, @{ $self->{_int_array} });
            push (@select_funcs, @{ $self->{_multi} });
        }
    }
    elsif ($stmt_kind eq "non-cond")
    {
        if ( $energy_level == 1 ) {
            my @int_const = @{ $self->{_int_const} };
            push ( @select_funcs, $int_const[0] );
        }
        elsif ( $energy_level == 2 ) {
            my @int_const = @{ $self->{_int_const} };
            push ( @select_funcs, $int_const[0] );

            if ( $secondary_level == 0 ) {
                my @add_sub = @{ $self->select_add_sub($secondary_level) };
                push (@select_funcs, @add_sub);
            }
            else {
                push (@select_funcs, @{ $self->{_add_sub} });
            }
        }
        elsif ( $energy_level == 3 ) {
            push (@select_funcs, @{ $self->{_int_const} });

            if ( $secondary_level == 0 ) {
                my @add_sub = @{ $self->select_add_sub($secondary_level) };
                push (@select_funcs, @add_sub);
            }
            else {
                push (@select_funcs, @{ $self->{_add_sub} });
            }

            if ( $secondary_level == 0 ) {
                my @int_compare = @{ $self->select_int_compare($secondary_level) };
                push (@select_funcs, @int_compare);
            }
            else {
                push (@select_funcs, @{ $self->{_int_compare} });
            }
            push (@select_funcs, @{ $self->{_ite} });
        }
        elsif ( $energy_level == 4) {
            push (@select_funcs, @{ $self->{_int_const} });
            push (@select_funcs, @{ $self->{_add_sub} });
            push (@select_funcs, @{ $self->{_int_compare} });
            push (@select_funcs, @{ $self->{_ite} });
            push (@select_funcs, @{ $self->{_logic} });
        }
        elsif ( $energy_level == 5) {
            push (@select_funcs, @{ $self->{_int_const} });
            push (@select_funcs, @{ $self->{_add_sub} });
            push (@select_funcs, @{ $self->{_int_compare} });
            push (@select_funcs, @{ $self->{_ite} });
            push (@select_funcs, @{ $self->{_logic} });
            push (@select_funcs, @{ $self->{_int_array} });
        }
        elsif ( $energy_level == 6 ) {
            push (@select_funcs, @{ $self->{_int_const} });
            push (@select_funcs, @{ $self->{_add_sub} });
            push (@select_funcs, @{ $self->{_int_compare} });
            push (@select_funcs, @{ $self->{_ite} });
            push (@select_funcs, @{ $self->{_logic} });
            push (@select_funcs, @{ $self->{_int_array} });
            push (@select_funcs, @{ $self->{_multi} });
        }
    }
    else {
        die "stmt_kind is not handle in select_funcs!\n";
    }

    # $logger->debug("select_funcs: " . Dumper(@select_funcs));
    return \@select_funcs;
}

sub select_add_sub {
        my $self = shift;
        my $secondary_level = shift;

        my @select_funcs = ();
        if ($secondary_level == 0) {
                foreach ( @{ $self->{_add_sub} } ) {
                        if ( $_->{_func_name} eq "add" ) {
                                push (@select_funcs, $_);
                        }
                }
        }
        else {
                foreach ( @{ $self->{_add_sub} } ) {
                        if ( $_->{_func_name} eq "sub" ) {
                                push (@select_funcs, $_);
                        }
                }
        }

        return \@select_funcs;
}

sub select_int_compare {
        my $self            = shift;
        my $secondary_level = shift;

        my @select_funcs = ();
        if ($secondary_level == 0) {
                foreach ( @{ $self->{_int_compare} } ) {
                        if ($_->{_func_name} eq "lt"
                                        || $_->{_func_name} eq "le" ) {
                                push (@select_funcs, $_);
                        }
                }
        }
        else {
                foreach ( @{ $self->{_int_compare} } ) {
                        if ($_->{_func_name} eq "eq"
                                        || $_->{_func_name} eq "ne" ) {
                                push (@select_funcs, $_);
                        }
                }
        }

        return \@select_funcs;
}

sub print_myself {
        my $self = shift;

        print "BOOL_CONST\n";
        foreach(@{ $self->{_bool_const} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "CHAR_CONST\n";
        foreach(@{ $self->{_char_const} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "INT_CONST\n";
        foreach(@{ $self->{_int_const} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "INT_COMPARE\n";
        foreach(@{ $self->{_int_compare} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "CHAR_COMPARE\n";
        foreach(@{ $self->{_char_compare} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "ADD_SUB\n";
        foreach(@{ $self->{_add_sub} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "LOGIC\n";
        foreach(@{ $self->{_logic} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "ITE\n";
        foreach(@{ $self->{_ite} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "INT_ARRAY\n";
        foreach(@{ $self->{_int_array} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "CHAR_ARRAY\n";
        foreach(@{ $self->{_char_array} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "CONVERSION\n";
        foreach(@{ $self->{_conversion} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        print "MULTI\n";
        foreach(@{ $self->{_multi} }) {
                my $func_name = $_->{_func_name};
                print "FUNC_NAME: $func_name\n";
        }
        return;
}

1;
