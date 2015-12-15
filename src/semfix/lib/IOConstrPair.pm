use Var;
use Utility;

use Log::Log4perl qw(:easy);

package IOConstrPair;

use Data::Dumper;

my $logger = Log::Log4perl->get_logger('semfix');

my $INT32   = 4294967296;
my $MAX_INT = 2147483647;

sub new {
        my $class = shift;
        my $self  = {
                _in_vars  => [],    # Input vector of Var
                _out_vars => [],    # Output vector of Var
        };
        bless $self, $class;
        return $self;
}

sub parseInput {
    my $self = shift;
    my $p_input_lines = shift;

    my @input_lines = @{$p_input_lines};
    my $in_var = new Var();

    foreach ( @input_lines ) {
        $line = $_;
        chomp($line);
        if ( $line =~ /^name\s+(\S+)/ ) {
            my $var_name = $1;
            $in_var->{_name} = $var_name;
        } elsif ( $line =~ /^init\s+([0-1])$/ ) {
            $in_var->{_is_init} = $1;
            $is_init = $1;
        } elsif ( $line =~ /^type\s+(\S+)$/ ) {
            my $var_type = $1;
            if ($var_type eq "char" || $var_type eq "int") { # CHARACTER/INTEGER TYPE
                if ($var_type eq "char") {
                    $in_var->{_type} = "CHAR";
                }
                else {
                    $in_var->{_type} = "INT";
                }

            }
            elsif ($var_type eq "charpointer"
                   || $var_type eq "intpointer"
                   || $var_type eq "chararray"			# TODO check this code
                   || $var_type eq "intarray")			# TODO check this code
            {
                next;	#FIXME: temporary ignore array and pointer
                $line = <INPUT>;
                chomp($line);
                $logger->debug($line);
                if ($var_type eq "charpointer"
                    || $var_type eq "intpointer")
                {
                    if ($line !~ /^size\s+unknown/) {
                        $logger->logdie("There is a confict in pointer size!");
                    }
                    if ($var_type eq "charpointer") {
                        $in_var->{_type} = "AIC";
                    } else {
                        $in_var->{_type} = "AII";
                    }
                    $in_var->{_arr_size} = -1;
                }
                else {
                    if ($var_type eq "chararray") {
                        $in_var->{_type} = "AIC";
                    } else {
                        $in_var->{_type} = "AII";
                    }

                    if ($line =~ /^size\s+(\d+)/) {
                        $in_var->{_arr_size} = $1;
                    }
                    else {
                        $logger->logdie("There is a confiction in pointer size!");
                    }

                }
                $line = <INPUT>;
                chomp($line);

                if ($line =~ /^value\s+symbolic/)
                {
                    my $cvc_string = "";
                    my $constr_string;
                    while ( ( $line = <INPUT> ) !~ /end symbolic/ ) {
                        $cvc_string = $cvc_string . $line;
                    }
                    $constr_string = Utility::convert2smt($cvc_string);

                    my $arr_name;
                    my $arr_value;
                    my @all_line  = split("\n", $constr_string);
                    foreach (@all_line) {
                        chomp($_);
                        if (/\(declare-fun const\_arr/ ) {
                            $_ =~ /\(declare-fun\s+(\S+)/;
                            $arr_name = $1;
                            $logger->debug("arr_name: $arr_name");
                        }
                        elsif (/\(assert / ) {
                            my %myhash = ();
                            $logger->debug("ASSERT: $_");
                            $arr_value = Utility::parse_arr($_, \%myhash);
                            $arr_value =~ s/$arr_name/$var_name/g;

                            if ($in_var->{_arr_size} == -1)
                            {
                                my $array_size;
                                $array_size = Utility::getPointerArraySize($arr_value, $in_var->{_type});
                                $logger->debug("ARR_SIZE: $array_size");

                                $in_var->{_arr_size} = $array_size;
                            }
                            last;
                        }
                    }
                    $logger->debug("array_value: $arr_value");
                    $in_var->{_value} = $arr_value;
                }
                elsif ( $line =~ /^value\s+undefined/ ) {	# variable is uninitialized
                    $in_var->{_value} = "undefined";
                    if ($is_init) {
                        $logger->logdie("There is an confliction in init value in charpointer/intpointer!");
                    }
                }
                else {
                    $logger->logdie("Unrecognized value charpointer/intpointer");
                }
            }
            else {
                $logger->logdie("Unhandled variable type!");
            }
        }
    }
    $self->add_in_var($in_var);
}

sub parseOutput {
    my $self = shift;
    my $p_output_lines = shift;

    my @output_lines = @{$p_output_lines};
    $logger->debug("output lines: " . Dumper(@output_lines));
    my $out_var = new Var();

    foreach ( @output_lines ) {
        $line = $_;
        chomp($line);
        if ( $line =~ /^name\s+(\S+)/ ) {
            my $var_name = $1;
            $out_var->{_name} = $var_name;
        } elsif ( $line =~ /^type\s+(\S+)$/ ) {
            my $out_type = $1;
            if ($out_type eq "bool") {
                $out_var->{_type} = "BOOL";
            }
            elsif ($out_type eq "char") {
                $out_var->{_type} = "CHAR";
            }
            elsif ($out_type eq "int") {
                $out_var->{_type} = "INT";
            }
            elsif ($out_type eq "charpointer"
                   || $out_type eq "chararray")
            {
                $out_var->{_type} = "AIC";
            }
            elsif ($out_type eq "intpointer"
                   || $out_type eq "intarray")
            {
                $out_var->{_type} = "AII";
            }
            elsif ($out_type eq "unhandled") {
                $logger->logdie("unsupported output type!");
            }
            else {
                $logger->logdie("Unrecognized output variable type!");
            }
        } else {
            $logger->logdie("Unrecognized output format: $line");
        }
    }

    $out_var->{_arr_size} = -1;
    $out_var->{_is_init} = 0;
    $out_var->{_value} = "undefined"; # @hoang add some used values

    $self->add_out_var($out_var);
}

# Parsing stmt.IO file. This file stores information
# of accessible variables.
# Return $Define::PARSE_UNSUPPORTED_OUTPUT if output type is not supported
#        $Define::PARSE_SUCCESS otherwise.

sub parse {                 # parse the input file
    my $self            = shift;
    my $input_file	= shift;
    my $is_init		= 0;

    open( INPUT, $input_file ) or $logger->logdie("Can not open $input_file for reading");

    while ( defined( $line = <INPUT> ) ) {
         chomp($line);
         if ( $line =~ /^%/ ) {    # a comment line
             next;
         }
         if ( $line =~ /\@input/ ) {
             @input_lines = ();
             while ( $line = <INPUT> ) {
                 chomp($line);
                 if ($line eq '') {
                     last;
                 }
                 else {
                     push(@input_lines, $line);
                 }
             }
             $self->parseInput(\@input_lines);
         } elsif ( $line =~ /\@output/ ) {
             @output_lines = ();
             while ( $line = <INPUT> ) {
                 chomp($line);
                 if ($line eq '') {
                     last;
                 }
                 else {
                     push(@output_lines, $line);
                 }
             }
             $self->parseOutput(\@output_lines);
         }
    }

    # @hoang sort in and out vars;
    $self->sort_in_vars();
    $self->sort_out_vars();

    close(INPUT);

    return $Define::PARSE_SUCCESS;
}

sub get_in_vars {
        my $self = shift;
        return $self->{_in_vars};
}

sub get_init_in_vars {
        my $self = shift;

        my @init_var = ();

        foreach ( @{ $self->{_in_vars} } ) {
                if ( $_->{_is_init} == 1) {
                        push (@init_var, $_);
                }
        }

        return \@init_var;
}

sub set_in_vars {
        my $self    = shift;
        my $in_vars = shift;

        $self->{_in_vars} = $in_vars;
        return;
}

sub add_in_var {
        my $self   = shift;
        my $in_var = shift;

        push( @{ $self->get_in_vars() }, $in_var );
        return;
}

sub get_out_vars {
        my $self = shift;
        return $self->{_out_vars};
}

sub set_out_vars {
        my $self     = shift;
        my $out_vars = shift;

        $self->{_out_vars} = $out_vars;
        return;
}

sub add_out_var {
        my $self    = shift;
        my $out_var = shift;

        push( @{ $self->get_out_vars() }, $out_var );
        return;
}

sub sort_in_vars {
        my $self = shift;

        my @in_sorted = sort { $a->{_name} cmp $b->{_name} } @{ $self->get_in_vars() };

        $self->set_in_vars( \@in_sorted );
        return;
}

sub sort_out_vars {
        my $self = shift;

        my @out_sorted = sort { $a->{_name} cmp $b->{_name} } @{ $self->get_out_vars() };

        $self->set_out_vars( \@out_sorted );
        return;
}

sub get_input_size {
        my $self   = shift;
        my @in_var = @{ $self->get_in_vars() };

        return scalar @in_var;
}

sub synchronize_uninit_vars {        # @hoang synchronize_uninit_vars($init_arr)
        my $self         = shift;
        my $ptr_init_arr = shift;
        my @init_arr     = @$ptr_init_arr;

        my @in_vars = @{ $self->get_in_vars() };

        # TODO probably here we should do some more proper error handling
        if ( (scalar @init_arr) != (scalar @in_vars) ) {
                        return;
        }

        for ($i = 0; $i < scalar @in_vars; $i++) {
                if ( $init_arr[$i] == 0 ) {
                        $in_vars[$i]->{_is_init} = 0;
                }
        }

        return;
}

sub get_decl {
        my $self     = shift;
        my $io_index = shift;

        my @decl     = ();
        my @in_var   = @{ $self->get_in_vars() };
        my @out_var  = @{ $self->get_out_vars() };
        my @all_var = (@in_var, @out_var);

        foreach( @all_var ) {
                my $var  = $_->{_name};
                my $var_type = $_->{_type};

                my $var_name = "$var\_io$io_index";
                my $var_decl = Utility::getDeclare($var_name, $var_type);

                push( @decl, $var_decl );
        }

        return join("", @decl);
}

# @hoang get_out_decl($io_index)
# return a string of out declare
sub get_out_decl {
        my $self     = shift;
        my $io_index = shift;

        my @decl     = ();
        my @out_var  = @{ $self->get_out_vars() };

        foreach( @out_var ) {
                my $var  = $_->{_name};
                my $var_type = $_->{_type};

                my $var_name = "$var\_io$io_index";
                my $var_decl = Utility::getDeclare($var_name, $var_type);

                push( @decl, $var_decl );
        }

        return join("", @decl);
}

sub to_string {
        my $self = shift;

my $result = "";

        my @in_vars = @{ $self->get_in_vars() };
        my @out_vars = @{ $self->get_out_vars() };

        my $input_num = scalar @in_vars;
        $result .= "input_num = $input_num\n";
        foreach (@in_vars) {
                $result .= $_->to_string();
        }

        my $output_num = scalar @out_vars;
        $result .= "output_num = $output_num\n";
        foreach (@out_vars) {
                $result .= $_->to_string();
        }

        return $result;
}

1;
