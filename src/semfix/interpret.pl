#!/usr/bin/perl -w

use lib "lib";
use AllOutConstr;
use FuncDecl;
use Z3Result;
use Getopt::Long;
use Spec;

use Data::Dumper;

use Log::Log4perl qw(:easy);

Log::Log4perl::init_once("$ENV{'SEMFIX_ROOT'}/log4perl.conf");

my $logger = Log::Log4perl->get_logger('semfix');

my $spec_file;
my $input_file;
my $loc_prefix;
my $c_code;

my @MYASCII = ( "\\0", "\\0x01", "\\0x02", "\\0x03", "\\0x04",
                                "\\0x05", "\\0x06", "\\0x07", "\\0x08", "\\n",
                                "\\t",    "\\0x0b", "\\0x0c", "\\r",    "\\0x0e", );

GetOptions(
        'spec-file=s'    => \$spec_file,
        'synfunc-file=s' => \$synfunc_file,
        'input-file=s'   => \$input_file,
        'patch-file=s'    => \$patch_file,
        'loc-prefix=s'   => \$loc_prefix,
        'c-code'         => \$c_code,
        'help!'          => sub { print_usage() }
) or $logger->logdie("Incorrect usage!");

$logger->debug("interpret.pl started");

my $prefix = $loc_prefix;

# parse the intial specification
my $spec = new Spec();
$spec->parse_lib($spec_file);

my @all_funcdecl = @{ $spec->get_all_func() };

$logger->debug("synfunc_file: $synfunc_file");

my $sig_function = new SynFunc();
$sig_function->parse($synfunc_file);
#$sig_function->print_myself();

my $tmp_funcdecl;
if ($c_code) {
        &interpret_ccode();
}
else {
        &interpret_readable();
}

sub interpret_readable {
        my $z3_res          = new Z3Result();
        my $z3_file_content = `cat $input_file`;
        $z3_res->parse($z3_file_content);

        #loc_to_func
        my %loc2func = ();

        foreach ( (@all_funcdecl) ) {
                my $func = $_;
                my @vars = @{ $_->{_param} };
                foreach (@vars) {
                        my $param_var = $_->{_name};
                        $param_var = "p_$param_var";
                        if ( $prefix eq "ll" ) {
                                $param_var = "p$param_var";
                        }
                        my $value = $z3_res->get_val($param_var);
                        $value =~ s/^bv([0-9]+)\[[0-9]+\]/$1/g;
                        #print "$param_var =  $value;\n\n";
                }
        }

        foreach ( (@all_funcdecl) ) {
                my $func = $_;
                my @vars = @{ $_->get_all_output() };
                foreach (@vars) {
                        my $loc_var = "$prefix\_$_";
                        my $loc     = $z3_res->get_val($loc_var);
                        if ( defined $loc ) {
                                $loc2func{$loc} = $func;
                                my $name = $func->{_func_name};
                        }
                }
        }

        my @sig_in = @{ $sig_function->get_all_input() };
        for ( $i = 0 ; $i < scalar @sig_in ; $i++ ) {
                #print "$i: o$i = input($i) \n";
        }

        my @sorted = sort { $a <=> $b } ( keys %loc2func );
        foreach (@sorted) {
                my $line_num = $_;
                #print "$line_num: o$line_num = ";
                my $func     = $loc2func{$line_num};
                my $funcname = $func->{_func_name};
                #print "$funcname(";
                my @inputs = @{ $func->get_all_input() };
                my @tmp    = ();
                foreach (@inputs) {
                        my $loc_var = "$prefix\_$_";
                        my $loc     = $z3_res->get_val($loc_var);
                        push( @tmp, "o$loc" );
                }
                #print join( ",", @tmp );
                #print ")\n";
        }

        #print "return ";
        my @sig_out = @{ $sig_function->get_all_output() };
        foreach (@sig_out) {
                my $loc_var = "$prefix\_$_";
                my $loc     = $z3_res->get_val($loc_var);
                if ( defined $loc ) {
                        #print "o$loc ";
                }
        }
        #print "\n";

}

sub interpret_ccode {
        my $z3_res          = new Z3Result();
        my $z3_file_content = `cat $input_file`;
        $z3_res->parse($z3_file_content);

        #loc_to_func
        my %loc2func  = ();
        my %func2loc  = ();
        my %loc2name  = ();
        my %param2value = ();

        # $logger->debug("all_funcdecl: " . Dumper(@all_funcdecl));
        foreach ( (@all_funcdecl) ) {
                my $func = $_;
                my @vars = @{ $_->get_param() };
                foreach (@vars) {
                        my $param_var = "p_$_";
                        my $value     = $z3_res->get_val($param_var);

                        if ( defined $value) {
                            $logger->debug("value: $value");
                            DEBUG_PRINT("int $param_var =  $value;\n");
                        }

                        $param_var =~ s/p\_(\S+)\_y0/$1/g;
                        $param2value{$param_var} = $value;
                }
        }
        DEBUG_PRINT("\n");

        foreach ( (@all_funcdecl) ) {
                my $func = $_;
                my @vars = @{ $_->get_all_output() };
                foreach (@vars) {
                        my $loc_var = "$prefix\_$_";
                        my $loc     = $z3_res->get_val($loc_var);
                        if ( defined $loc ) {
                                $loc = $loc + 0;
                                $loc2func{$loc} = $func;
                                my $name = $func->{_func_name};
                        }
                }
        }

        # print the implementation of each idividual function
        foreach ( (@all_funcdecl) ) {
                my $func = $_;
                my $impl = $func->{_impl};
                my $name = $func->{_func_name};

                #print "$impl";
                #print "\n";
        }

        # signature of the synthesized function
        DEBUG_PRINT("int ");
        my $funcname = $sig_function->{_func_name};
        DEBUG_PRINT($funcname);
        DEBUG_PRINT("(");

        my @sig_in      = @{ $sig_function->get_all_input() };
        # $logger->debug("sig_in: " . Dumper(@sig_in));
        my @sig_in_type = @{ $sig_function->get_all_input_type() };
        for ( $i = 0 ; $i < scalar @sig_in ; $i++ ) {
                my $vartype = $sig_in_type[$i];
                my $name    = $sig_in[$i];
                $name =~ s/$funcname\_(\S+)\_in/$1/g;
                if ( $i > 0 ) {
                        DEBUG_PRINT(", ");
                }
                if ( not( $vartype =~ /Array/ ) ) {
                        $loc2name{$i} = $name;
                        DEBUG_PRINT("int $name");
                }
                else {
                        DEBUG_PRINT("char * o$i");
                }
        }

        my @sorted = sort ( keys %loc2func );
        @sorted    = sort { $a<=>$b } @sorted;

        my @sig_out      = @{ $sig_function->get_all_output() };
        my @sig_out_type = @{ $sig_function->get_all_output_type() };
        $logger->debug("sig_out: " . Dumper(@sig_out));

        if ( $sig_out_type[$#sig_out_type] =~ /Array/ ) {
                my $last_out = $sig_out[$#sig_out];
                my $loc_var  = "$prefix\_$last_out";
                $out_arrloc  = $z3_res->get_val($loc_var);
                if ( defined $out_arrloc ) {
                        DEBUG_PRINT(", char * o$out_arrloc");
                }
        }

        DEBUG_PRINT(") {\n");

        while ( ( scalar @sorted ) > 0 ) {
                my $line_num = shift @sorted;
                my $func     = $loc2func{$line_num};
                my $funcname = $func->{_func_name};

                my @outvars      = @{ $func->get_all_output() };
                my @outvars_type = @{ $func->get_all_output_type() };

                if ( $outvars_type[$#outvars_type] =~ /Array/ )
                {    # next line should be the same;
                        my $next_linenum = $line_num + 1;
                        if ( ( defined $out_arrloc ) and ( $out_arrloc == $next_linenum ) )
                        {
                                # do nothing
                        }
                        else {
                                DEBUG_PRINT("    char * o$next_linenum;");
                        }
                }

                DEBUG_PRINT("    int o$line_num = $funcname(");
                my @inputs = @{ $func->get_all_input() };
                my @tmp    = ();
                foreach (@inputs) {
                        my $loc_var = "$prefix\_$_";
                        my $loc     = $z3_res->get_val($loc_var);
                        my $name    = $loc2name{$loc};

                        if (defined $name) {
                                push( @tmp, $name);
                        } else {
                                push( @tmp, "o$loc" );
                        }
                }
                DEBUG_PRINT(join( ", ", @tmp ));

                if ( $outvars_type[$#outvars_type] =~ /Array/ )
                {
                        # next line should be the same;
                        my $next_linenum = $line_num + 1;
                        DEBUG_PRINT(",o$next_linenum");
                        shift @sorted;
                }

                DEBUG_PRINT(");\n");

        }

        my $first_out = $sig_out[0];
        my $prog_loc = get_prog_loc($first_out);
        $logger->debug("first_out: $first_out");

        my $loc_var   = "$prefix\_$first_out";
        $logger->debug("loc_var: $loc_var");
        my $loc       = $z3_res->get_val($loc_var);
        if ( ! defined $loc ) {
                $logger->logdie("location is not defined!");
        }
        else {
                DEBUG_PRINT("    return o$loc;\n");
        }
        DEBUG_PRINT("}\n\n");

        $logger->debug("loc: $loc");
        $logger->debug("prefix: $prefix");
        my $final_fix = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);
        $logger->debug("final_fix: $final_fix");

        $first_out =~ s/$funcname\_(\S+)\_out/$1/g;
        # $logger->info("Recommended Fix:\t" . $first_out . " = " . $final_fix . ";");
        # print "Recommended Fix:  ";
        # print "$first_out = $final_fix;";

        open(my $fh, '>', $patch_file);
        print $fh "$prog_loc\n";
        print $fh "???\n"; # FIXME: original expression
        print $fh "$final_fix\n";
        close $fh;

        print "SUCCESS";
}

sub get_prog_loc {
    my $var = shift;

    @split = split('choice!', $var);
    @split = split('!angelic', $split[1]);
    @split = split('!', $split[0]);

    # take the first one
    $result = $split[0];

    # drop the first and last one
    @split = @split[1 .. 3];
    foreach ( @split )
    {
        my $item = $_;
        $result = $result . "-" . $item
    }

    return $result;
}

sub construct_fix {
        my $z3_res       = shift;
        my $ptr_loc2func = shift;
        my %loc2func     = %{$ptr_loc2func};
        my $ptr_loc2name = shift;
        my %loc2name     = %{$ptr_loc2name};
        my $ptr_param2value = shift;
        my %param2value  = %{$ptr_param2value};
        my $loc          = shift;
        my $prefix       = shift;

        my $name = $loc2name{$loc};
        my $value;
        my $func;

        if (defined $name) {
            $logger->debug("name: $name");
            @split = split('env!', $name);
            return $split[1];
        }
        $func = $loc2func{$loc};
        if ( ! defined $func) {
            $logger->debug("loc: " . $loc);
            $logger->logdie("function is not defined in construct_fix!");
        }
        my $funcname = $func->{_func_name};

        my $final_result = "";

        $logger->debug("funcname: $funcname");
        if ($funcname =~ /boolc/ ) {
                my $value = $param2value{$funcname};
                return $value;
        }
        elsif ($funcname =~ /const/ ) {
            my $value = $param2value{$funcname};
            $logger->debug("value: $value");
            return $value;
        }
        elsif ($funcname =~ /charc/ ) {
                my $value = $param2value{$funcname};

                my $char;
                $value += 0;
                #print STDERR "my table @MYASCII\n";
                if ($value <= 14) {
                        $char = $MYASCII[$value];
                        $char = "\'$char\'";
                }
                else {
                        $char = chr($value);
                        $char = "\'$char\'";
                }
                return $char;
        }
        elsif ($funcname =~ /int2char/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 1 ) {
                        $logger->logdie("inputs of int2char function has unexpected size!");
                }
                my $input  = $input[0];

                my $f_loc_var   = "$prefix\_$input";
                my $loc = $z3_res->get_val($f_loc_var);
                my $value = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);

                return $value;
        }
        elsif ($funcname =~ /ident/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 1 ) {
                        $logger->logdie("inputs of ident function has unexpected size!");
                }
                my $input  = $input[0];

                my $f_loc_var   = "$prefix\_$input";
                my $loc = $z3_res->get_val($f_loc_var);
                my $value = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);

                return $value;
        }
        elsif ($funcname =~ /add/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var   = "$prefix\_$first_input";
                my $s_loc_var   = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                $logger->debug("first_loc: $first_loc");
                $logger->debug("second_loc: $second_loc");

                if ($first_loc == $second_loc) {
                    my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);

                    return "(2 * $left)";
                }
                else {
                    my $left = construct_fix($z3_res, \%loc2func, \%loc2name,
                                             \%param2value, $first_loc, $prefix);
                    my $right = construct_fix($z3_res, \%loc2func, \%loc2name,
                                              \%param2value, $second_loc, $prefix);

                    $logger->debug("left: $left");
                    $logger->debug("right: $right");
                    return "($left + $right)";
                }

        }
        elsif ($funcname =~ /sub/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var   = "$prefix\_$first_input";
                my $s_loc_var   = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);

                        return "0";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left - $right)";
                }

        }
        elsif ($funcname =~ /and/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var   = "$prefix\_$first_input";
                my $s_loc_var   = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);

                        return $left;
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left && $right)";
                }
        }
        elsif ($funcname =~ /or/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var  = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";
                my $first_loc  = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);

                        return $left;
                } else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left || $right)";
                }

        }
        elsif ($funcname =~ /neq/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var   = "$prefix\_$first_input";
                my $s_loc_var   = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        return "0";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left != $right)";
                }
        }
        elsif ($funcname =~ /eq/) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of add function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var   = "$prefix\_$first_input";
                my $s_loc_var   = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        return "1";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left == $right)";
                }
        }
        elsif ($funcname =~ /lt/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of lt function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";

                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        return "0";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left < $right)";
                }

        }
        elsif ($funcname =~ /le/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of le function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        return "1";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left <= $right)";
                }
        }
        elsif ($funcname =~ /ifelse/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 3 ) {
                        $logger->logdie("inputs of ifelse function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];
                my $third_input  = $input[2];

                my $f_loc_var = "$prefix\_$first_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $cond = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);

                my $s_loc_var   = "$prefix\_$second_input";
                my $t_loc_var = "$prefix\_$third_input";
                my $second_loc = $z3_res->get_val($s_loc_var);
                my $third_loc = $z3_res->get_val($t_loc_var);

                if ($second_loc == $third_loc) {
                        my $block1 = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return $block1;
                }
                else {
                        my $block1 = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);
                        my $block2 = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $third_loc, $prefix);

                        return "($cond ? $block1 : $block2)";
                }
        }
        elsif ($funcname =~ /charEq/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of charEq function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                if ($first_loc == $second_loc) {
                        return "1";
                }
                else {
                        my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                        my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                        return "($left == $right)";
                }
        }
        elsif ($funcname =~ /charAt/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of charAt function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                return "$left\[$right\]";

        }
        elsif ($funcname =~ /charIdent/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 1 ) {
                        $logger->logdie("inputs of ident function has unexpected size!");
                }
                my $input  = $input[0];

                my $f_loc_var   = "$prefix\_$input";
                my $loc = $z3_res->get_val($f_loc_var);
                my $value = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);

                return $value;
        }
        elsif ($funcname =~ /intIdent/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 1 ) {
                        $logger->logdie("inputs of ident function has unexpected size!");
                }
                my $input  = $input[0];

                my $f_loc_var   = "$prefix\_$input";
                my $loc = $z3_res->get_val($f_loc_var);
                my $value = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);

                return $value;
        }
        elsif ($funcname =~ /intAt/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 2 ) {
                        $logger->logdie("inputs of charAt function has unexpected size!");
                }

                my $first_input  = $input[0];
                my $second_input = $input[1];

                my $f_loc_var = "$prefix\_$first_input";
                my $s_loc_var  = "$prefix\_$second_input";
                my $first_loc = $z3_res->get_val($f_loc_var);
                my $second_loc = $z3_res->get_val($s_loc_var);

                my $left = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $first_loc, $prefix);
                my $right = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $second_loc, $prefix);

                return "$left\[$right\]";
        }
        elsif ($funcname =~ /stringIdent/ ) {
                my @input = @{ $func->get_all_input() };

                if( (scalar @input) != 1 ) {
                        $logger->logdie("inputs of ident function has unexpected size!");
                }
                my $input  = $input[0];

                my $f_loc_var   = "$prefix\_$input";
                my $loc = $z3_res->get_val($f_loc_var);
                my $value = construct_fix($z3_res, \%loc2func, \%loc2name, \%param2value, $loc, $prefix);

                return $value;
        }
        else {
                $logger->logdie("Function is not recognized");
        }

        return $final_result;
}

sub print_main() {
        my @sig_in = @_;

        print 'int main(int argc, char** argv) {';
        print "\n";

        for ( $i = 0 ; $i < scalar @sig_in ; $i++ ) {
                my $index = $i + 1;
                print "    int x_$i = atoi\(argv\[$index\]\);\n";
        }
        my $func = "sig(";
        my $i = 0;
        for ( $i = 0 ; $i < $#sig_in ; $i++ ) {
                $func = $func . "x_$i, ";
        }
        if ($#sig_in >= 0) {
                $func = $func . "x_$i)";
        } else {
                $func = $func . ")";
        }
        print "    int result = $func;\n";
        print '    printf("result = %d\n", result);';
        print "\n";
        print "}\n";
}

sub print_usage() {
        print STDERR (
                "usage:command
        --spec-file=s
        --synfunc-file=s
        --input-file=s
        --loc-prefix=s
        --c-code (to generate compilable C code)
        --help \n"
        );
        exit;
}

sub DEBUG_PRINT {
        if (1) {
            print STDERR "@_";
        }
}
