#!/usr/bin/perl -w

use lib "localizer";
use lib "drivers";
use lib "lib";

use Time::HiRes qw(gettimeofday tv_interval);
use File::Basename;
use Getopt::Long;
use Cwd;

use Log::Log4perl qw(:easy);
use Log::Log4perl::Level;

# use PotentialDefects;
# use Localizer;

# use Program;

use IOConstrPair;
use AllOutConstr;
use AllIOConstr;
use Utility;
use SynFunc;
use Z3Result;
use Spec;
use Var;

use Data::Dumper;

Log::Log4perl::init_once("$ENV{'SEMFIX_ROOT'}/log4perl.conf");
my $logger = Log::Log4perl->get_logger('semfix');

my $formula_gen_only = '';
my $skip_pc = '';
my $work_dir;
my $spec_file;
my $patch_file;
my $min_syn_level = 1;
my $max_syn_level = 6;
my $init_z3_timeout = 15;
my $z3_timeout_factor = 16;
my $max_z3_trials = 2;
my $no_log = 0;

GetOptions(
    'work-dir=s'            => \$work_dir,
    'spec-file=s'           => \$spec_file,
    'patch-file=s'          => \$patch_file,
    'formula-gen-only'      => \$formula_gen_only,
    'skip-pc'               => \$skip_pc,
    'min-syn-level=s'       => \$min_syn_level,
    'max-syn-level=s'       => \$max_syn_level,
    'init-z3-timeout=s'     => \$init_z3_timeout,
    'z3-timeout-factor=s'   => \$z3_timeout_factor,
    'max-z3-trials=s'       => \$max_z3_trials,
    'no-log'                => \$no_log,
    'help!'                 => sub { print_usage() }
) or $logger->logdie("Incorrect usage, try --help !");

if ( $no_log ) {
    $logger->level($FATAL);
}

my $is_using_POSIX = 1;
my $solving_time = 0.0;

my $syn_func = generate_syn_func();
my $synfunc_file = "$work_dir/semfix-syn-input/synfunc.info";
$syn_func->print_myself($synfunc_file);

my $model_file = "$work_dir/semfix-syn-input/z3.res";

# construct @io_behavior_pool
my @io_behavior_pool = ();
my @tests = glob("$work_dir/semfix-syn-input/tests/*");
if ( (scalar @tests) == 0) {
    $logger->logdie("@tests is empty!");
}
for ( @tests ) {
    my $test_dir = $_;
    $logger->debug("test_dir: " . $test_dir);
    my $all_io_constr = new AllIOConstr();

    my @in_files = glob("$test_dir/*.IO");
    for ( @in_files ) {
        my $in_file = $_;
        $logger->debug("in_file: " . $in_file);
        $io_constr = new IOConstrPair();
        my $status = $io_constr->parse($in_file);
        if ($status != $Define::PARSE_SUCCESS) {
            $logger->logdie("Failed to parse $in_file!");
        }
        # $logger->debug("io_constr: " . Dumper($io_constr));
        $all_io_constr->add_io_pair($io_constr);
    }

    my $in_files_size = scalar @in_files;
    if ($in_files_size > 0) {
        my $out_constr = new AllOutConstr();
        $out_constr->parseAll("$test_dir/klee");
        $out_constr->construct_expect_var($work_dir, $test_dir);
        $all_io_constr->set_out_constr($out_constr);
        push (@io_behavior_pool, $all_io_constr);
    }

    #my $instance_num = $all_io_constr->get_instance_num();
    #$logger->debug("instance_num: $instance_num");
}
# $logger->debug("io_behavior_pool: " . Dumper(@io_behavior_pool));

# check whether this location is fixable
unless ( $formula_gen_only ) {
    my @empty_funcs = ();
    my $is_fixable = generate_and_solve_formula(\@empty_funcs,
                                                \@io_behavior_pool,
                                                $syn_func);
    if ( $is_fixable == 0) {
        $logger->info("The statement is NOT fixable");
        print "FAIL";
        exit 1;
    }
}

# read basic components
my $spec = new Spec();
$spec->parse_lib( $spec_file );
my $out_type = $syn_func->get_output_type();
# $logger->debug("out_type: " . $out_type);

my $kind;
if ($out_type eq "BOOL") {
    $kind = "cond";
} else {
    $kind = "non-cond";
}
$logger->debug("kind: $kind");

my @energy_levels = ($min_syn_level..$max_syn_level);
foreach ( @energy_levels ) {
    my $energy_level = $_;
    $logger->debug("energy_level: " . $energy_level);

    my @secondary_levels;
    if ($kind eq "cond") {
        @secondary_levels = (0,1,2);
    } else {
        @secondary_levels = (0,1);
    }

    foreach ( @secondary_levels )
    {
        my $sec_level = $_;
        $logger->debug("sec_level: " . $sec_level);
        my @selected_funcs = @{ $spec->select_funcs($kind,
                                                    $energy_level,
                                                    $sec_level,
                                                    $out_type) };
        my $is_repair = generate_and_solve_formula(\@selected_funcs,
                                                   \@io_behavior_pool,
                                                   $syn_func);
        $logger->debug("is_repair: $is_repair");
        if ( $is_repair ) {
            $current_repair = Utility::interpretResult($spec_file,
                                                       $synfunc_file,
                                                       $model_file,
                                                       $patch_file);
            $logger->info("Current repair:\t" . $current_repair);
            print "SUCCESS";
            exit 0;
        }
    }
}
print "FAIL";
exit 0;

sub generate_syn_func {
    my $all_io_constr = new AllIOConstr();
    my @in_files = glob("$work_dir/semfix-syn-input/tests/*/*.IO");
    if ( (scalar @in_files) == 0) {
        $logger->logdie("in_files is empty!");
    }

    my $in_file = $in_files[0];
    $io_constr = new IOConstrPair();
    my $status = $io_constr->parse($in_file);
    if ($status != $Define::PARSE_SUCCESS) {
        print "[solve.pl] Failed to parse " . $in_file;
    }
    $all_io_constr->add_io_pair($io_constr);

    # extract signature of syn_func
    my $syn_func = extract_syn_func($all_io_constr);
    return $syn_func;
}

sub extract_syn_func {
        my $io_behavior = shift;

        my $syn_func = new SynFunc();
        $syn_func->{_func_name} = "synfunc";

        # extract input
        my @io_pairs = @{ $io_behavior->get_io_pairs() };
        if ( (scalar @io_pairs) == 0) {
                $logger->logdie("IO-pairs is empty in extract_syn_func!");
        }
        my $io_pair = $io_pairs[0];
        my @in_vars = @{ $io_pair->get_in_vars() };

        foreach ( @in_vars ) {
            my $var = new Var();

            $var->{_name}	= $_->{_name};
            $var->{_type}	= $_->{_type};
            $syn_func->add_input($var);
        }

        # extrac output
        my @out_vars = @{ $io_pair->get_out_vars() };
        foreach (@out_vars) {
            my $var = new Var();
            $var->{_name}  = $_->{_name};
            $var->{_type}  = $_->{_type};
            $syn_func->add_output($var);
        }

        return $syn_func;
}

sub generate_and_solve_formula {
        my $selected_funcs   = shift;
        my $io_behavior_pool = shift;
        my $syn_func         = shift;

        $logger->debug("generate_and_solve_formula called");
        $logger->debug("selected_funcs: " . Dumper($selected_funcs));

        my @funcs_array = @{ $selected_funcs };

        my $tmp_f = "$work_dir/semfix-syn-input/f.smt2";
        open( TMPF, ">$tmp_f" ) || $logger->logdie("Cannot open $tmp_f to write ");

        if ( (scalar @funcs_array) > 0 ) {
            my $error = &generate_formula( TMPF, $selected_funcs, $io_behavior_pool, $syn_func);
            $logger->debug("error: " . $error);
            if ($error) {
                return 2;
            }
        }
        else {
            generate_check_fixable_formula( TMPF, $io_behavior_pool, $syn_func);
        }
        print_tail(TMPF);
        close(TMPF);

        if ( $formula_gen_only ) {
            $logger->logdie("We do not call z3 yet.");
        }

        my $res_file = "$work_dir/semfix-syn-input/z3.res";
        my $z3_err_filename = "$work_dir/semfix-syn-input/semfix-z3-err";
        $logger->debug("z3_err_filename: $z3_err_filename");
        my $z3_timeout = $init_z3_timeout;
        my $z3_terminated = 0;
        my $z3_trials = 0;
        while ( $z3_trials < $max_z3_trials ) {
            $start_t = [gettimeofday];
            `echo unsat > $res_file`;
            $logger->info("Solving constraints with timeout $z3_timeout");
            system("timeout $z3_timeout z3_semfix $tmp_f > $res_file 2> $z3_err_filename");
            if ( $? != 0 ) {
                $logger->debug("z3_semfix failed\n" . `cat $z3_err_filename`);
                $z3_timeout = $z3_timeout * $z3_timeout_factor;
                $z3_trials = $z3_trials + 1;
                next;
            } else {
                $z3_terminated = 1;
                $logger->debug("z3_semfix found a model.");
                $end_t = tv_interval($start_t);
                $solving_time += $end_t;
                last;
            }
        }

        my $is_fixed = $z3_terminated && has_solution($res_file);

        return $is_fixed;
}

sub generate_check_fixable_formula {

        my ($fd, $pio_pair, $syn_func) = @_;

        my @io_behavior_pool = @$pio_pair;
        my $num_iopairs      = scalar @io_behavior_pool;

        my $io_behavior;

        print $fd "(set-option enable-cores)\n";
        print $fd "(define-sorts ( (AII (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
        print $fd "(define-sorts ( (AIC (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
        print $fd "(define-sorts ( (INT (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
        print $fd "(define-sorts ( (BOOL (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
        print $fd "(define-sorts ( (CHAR (Array (_ BitVec 32) (_ BitVec 8))) ))\n";

        if ($is_using_POSIX) {  # @hoang KLEE uses model_version variable when POSIX is used.
                for ($i = 0; $i < $num_iopairs; $i++) {
                        print $fd "(declare-fun model_version_io$i () INT)\n";
                }
        }

        # generate all variable declarations
        $logger->debug("num_iopairs: $num_iopairs");
        for ( $i = 0 ; $i < $num_iopairs ; $i++ ) {
            $io_behavior = $io_behavior_pool[$i];

            print $fd "; expect_out declare \n";
            my $var_decls = $syn_func->get_expect_out_decls($i);
            print $fd $var_decls;

            print $fd "; output of each statement instance \n";
            my $out_decls = $io_behavior->get_decls($i);
            $logger->debug("out_decls: " . Dumper($out_decls));
            print $fd $out_decls;
        }

        # value constraints on input/output pairs
        print $fd "; value constraints \n";

        for ( $i = 0; $i < $num_iopairs; $i++ ) {
                $io_behavior = $io_behavior_pool[$i];

                # @hoang print print output constraints
                my $out_constr = $io_behavior->get_out_constr();
                my $out_constr_formula = $out_constr->get_final_constr($i);

                print $fd "; output constraint \n";
                print $fd "$out_constr_formula\n";
        }

        return;
}

sub generate_formula {
    my ($fd, $pfunc_decl, $pio_pair, $syn_func) = @_;

    my @func_decl        = @$pfunc_decl;
    my $func_size = scalar @func_decl;
    $logger->debug("FUNC_DECL: $func_size");
    my @io_behavior_pool = @$pio_pair;
    my $num_iopairs      = scalar @io_behavior_pool;
    $logger->debug("num_iopairs $num_iopairs");
    my $io_behavior;

    # get all_vars and all_vars_type
    my @all_vars      = ();
    my @all_vars_type = ();
    #$logger->debug("syn_func: " . Dumper($syn_func));
    #$logger->debug("func_decl: " . Dumper(@func_decl));
    foreach ( ( $syn_func, @func_decl ) ) {
        my $tmp_func   = $_;
        # $logger->debug("tmp_func: " . Dumper($tmp_func));
        my $pvars      = $tmp_func->get_all_var();
        my $pvars_type = $tmp_func->get_all_var_type();
        push( @all_vars,      @$pvars );
        push( @all_vars_type, @$pvars_type );
    }

    print $fd "(set-option enable-cores)\n";
    print $fd "(define-sorts ( (AII (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
    print $fd "(define-sorts ( (AIC (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
    print $fd "(define-sorts ( (INT (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
    print $fd "(define-sorts ( (BOOL (Array (_ BitVec 32) (_ BitVec 8))) ))\n";
    print $fd "(define-sorts ( (CHAR (Array (_ BitVec 32) (_ BitVec 8))) ))\n";

    if ($is_using_POSIX) {  # KLEE uses model_version variable when POSIX is used.
        for ($i = 0; $i < $num_iopairs; $i++) {
            print $fd "(declare-fun model_version_io$i () INT)\n";
        }
    }

    # declare all constants
    declare_param( $fd, \@func_decl );

    # generate all location variable declaration
    # $logger->debug("syn_func: " . Dumper($syn_func));
    # $logger->debug("func_decl: " . Dumper(@func_decl));
    foreach ( ( $syn_func, @func_decl ) ) {
        my $tmp_func  = $_;
        # $logger->debug("tmp_func: " . Dumper($tmp_func));
        my $var_decls = $tmp_func->get_loc_var_decl();
        $logger->debug("var_decls: $var_decls");
        print $fd $var_decls;
    }

    # generate all variable declarations
    for ( $i = 0 ; $i < $num_iopairs ; $i++ ) {
        $io_behavior = $io_behavior_pool[$i];
        my $instance_num = $io_behavior->get_instance_num();

        # $logger->debug("io_behavior: " . Dumper($io_behavior));
        $logger->debug("instance_num: $instance_num");
        for ($j = 0; $j < $instance_num; $j++ ) {
            for ( ( $syn_func, @func_decl ) ) {
                my $tmp_func = $_;
                # $logger->debug("tmp_func: " . Dumper($tmp_func));
                my $var_decls = $tmp_func->get_decls($j, $i);
                $logger->debug("var_decls: $var_decls");
                print $fd $var_decls;
            }
        }
        print $fd "; output of each statement instance \n";
        my $out_decls = $io_behavior->get_decls($i);
        print $fd $out_decls;
    }

    # GENERATE ALL CONSTRAINTS

    # generate lib constraints
    print $fd "; lib constraint \n";
    for ( $i = 0 ; $i < $num_iopairs ; $i++ ) {
        $io_behavior = $io_behavior_pool[$i];
        my $instance_num = $io_behavior->get_instance_num();

        for ($j = 0; $j < $instance_num; $j++) {
            foreach (@func_decl) {
                my $constraint = $_->get_constraint($j, $i);
                print $fd $constraint;
            }
        }
    }

    $logger->debug("all_vars: " . Dumper(@all_vars));

    # conn constraints
    #    print "size of all_var is $#all_vars\n";
    #    print "@all_vars\n";
    #    print "size of all_var type  is $#all_vars_type\n";
    #    print "@all_vars_type\n";

    for ( $j = 0 ; $j < scalar(@all_vars) ; $j++ ) {
        for ( $k = $j + 1 ; $k < scalar(@all_vars) ; $k++ ) {
            my $var1  = $all_vars[$j];
            my $var2  = $all_vars[$k];
            my $type1 = $all_vars_type[$j];
            my $type2 = $all_vars_type[$k];

            if ( $type1 eq $type2 ) {

                # conn constraints
                print $fd "; conn constraint \n";
                for ( $io_idx = 0 ; $io_idx < $num_iopairs ; $io_idx++ ) {
                    $io_behavior = $io_behavior_pool[$io_idx];
                    my $ins_num  = $io_behavior->get_instance_num();

                    for ($ins_idx = 0; $ins_idx < $ins_num; $ins_idx++) {
                        if ($type1 eq "CHAR") {
                            print $fd "(assert (=> (= l_$var1 l_$var2)" .
                                " (= (select $var1\_ins$ins_idx\_io$io_idx (_ bv0 32))".
                                " (select $var2\_ins$ins_idx\_io$io_idx (_ bv0 32)))))\n";
                        }
                        elsif ($type1 eq "AIC"
                               || $type1 eq "AII")
                        {
                            #print $fd "(assert (=> (= l_$var1 l_$var2)" .
                            #	" (and (= $var1\_ins$ins_idx\_io$io_idx $var2\_ins$ins_idx\_io$io_idx) ".
                            #	"(= $var1\_len\_ins$ins_idx\_io$io_idx $var2\_len\_ins$ins_idx\_io$io_idx))))\n";
                            print $fd "(assert (=> (= l_$var1 l_$var2)" .
                                " (= $var1\_ins$ins_idx\_io$io_idx $var2\_ins$ins_idx\_io$io_idx)))\n"; #FIXME: temporay do not create constraint on length
                        }
                        else {
                            print $fd "(assert (=> (= l_$var1 l_$var2)" .
                                " (= $var1\_ins$ins_idx\_io$io_idx $var2\_ins$ins_idx\_io$io_idx)))\n";
                        }
                    }
                }
            }
            else {
                print $fd "; type constraint \n";
                print $fd "(assert (not (= l_$var1 l_$var2)))\n";
            }
        }
    }

    my $synfunc_name = $syn_func->{_func_name};

    # value constraints on input/output pairs
    print $fd "; value constraints \n";

    for ($i = 0; $i < $num_iopairs; $i++) {
        $io_behavior = $io_behavior_pool[$i];

        my $io_connection = $syn_func->create_io_connection($io_behavior, $i);
        unless ( defined $io_connection ) {
            $logger->logdie("io connection not defined");
            return 1;
        }
        # $logger->debug("io_connection: $io_connection");
        print $fd $io_connection;

        # print output constraints
        my $out_constr = $io_behavior->get_out_constr();

        unless ( $skip_pc ) {
            print $fd "; PC \n";
            my $out_constr_formula = $out_constr->get_final_constr($i);
            # $logger->debug("OUT_CONSTR: $out_constr_formula\n");
            print $fd "$out_constr_formula\n";
        }
    }

    # generate wfp
    my @syn_in  = @{ $syn_func->get_all_input() };
    my @syn_out = @{ Utility::get_angelic_out($syn_func->get_all_output()) };

    print $fd "; wfp constraints for global input variables\n";
    for ($i = 0; $i <= $#syn_in; $i++) {
        my $var = $syn_in[$i];
        print $fd "(assert (= l_$var $i))\n";
    }

    my @in_vars    = ();
    my @out_vars   = ();
    foreach ( ( $syn_func, @func_decl ) ) {
        my $ptmp = $_->get_all_input();
        push( @in_vars, @$ptmp );
    }
    foreach (@func_decl) {
        my $ptmp = $_->get_all_output();
        push( @out_vars, @$ptmp );
    }

    $logger->debug("out_vars: " . Dumper(@out_vars));

    my $num_syn_in = scalar @syn_in;
    my $num_out    = scalar @out_vars;
    my $num_syn_out = scalar @syn_out;
    my $total_vars = $num_syn_in + $num_out;
    $logger->debug("num_syn_in: $num_syn_in");
    $logger->debug("num_out: $num_out");
    $logger->debug("total_vars: $total_vars");

    print $fd "; wfp constraints for input variables \n";
    foreach (@in_vars) {
        my $var_name = $_;
        my $loc_var  = "l_" . $var_name;
        print $fd "(assert (and (>= $loc_var 0) (< $loc_var $total_vars)))\n";
    }

    $logger->debug("syn_out: " . Dumper(@syn_out));

    print $fd "; wfp constraints for output variables \n";
    foreach ( @syn_out, @out_vars ) {
        my $var     = $_;
        my $loc_var = "l_" . $var;
        $logger->debug("assertion: (assert (and (>= $loc_var $num_syn_in) (< $loc_var $total_vars)))");
        print $fd "(assert (and (>= $loc_var $num_syn_in) (< $loc_var $total_vars)))\n";
    }

    # generate cons
    print $fd "; cons constraints \n";
    for ($i = 0; $i <= $#out_vars; $i++ )
    {
        my $first_var = $out_vars[$i];
        for ($j = $i + 1; $j <= $#out_vars; $j++)
        {
            my $second_var = $out_vars[$j];
            print $fd "(assert (not (= l_$first_var l_$second_var)))\n";
        }
    }

    # generate acyc
    print $fd "; acyc constraints \n";
    foreach (@func_decl) {
        my $constr = $_->get_acyc_constraint();
        print $fd $constr;
    }

    # generate buf constraint
    #print $fd "; buffer constraints \n";

    #foreach (@func_decl) {
    #	my $constr = $_->get_buf_constraint();
    #	print $fd $constr;
    #}
    return 0; # @hoang normal exit
}

sub declare_param {
        my ( $fd, $pfunc_decl ) = @_;
        my @func_decl           = @$pfunc_decl;
        foreach (@func_decl) {
                my $func       = $_;
                my $funcname   = $func->{_func_name};
                my @param      = @{ $func->get_param() };
                my @param_type = @{ $func->get_param_type() };
                foreach (@param) {
                        my $tmp_type = shift(@param_type);
                        my $var_name = "p\_$_";
                        my $var_decl = Utility::getDeclare($var_name, $tmp_type);

                        print $fd $var_decl;
                }
        }
}

sub print_tail {
        my ($fd) = @_;
        print $fd "(check-sat)\n";
        print $fd "(model)\n";
        print $fd "(get-unsat-core)\n";
}

sub has_solution {
        my $res_file = shift;

        $logger->debug("res_file: $res_file");
        open(RESULT, $res_file) or $logger->logdie("Cannot open $res_file for reading!");

        my $line = <RESULT>;

        my $solution = 0;
        if ($line =~ /^sat$/) {
                $solution = 1;
        }

        close (RESULT);
        return $solution;
}
