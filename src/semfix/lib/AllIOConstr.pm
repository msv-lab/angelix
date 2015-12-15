use OutConstr;
use SynFunc;

my $logger = Log::Log4perl->get_logger('semfix');

package AllIOConstr;

sub new {
        my $class = shift;
        my $self  = {
                _io_pairs    => [],       # @hoang list of io-pairs for all instance
                _out_constr  => undef,    # @hoang constraints for output's value
        };

        bless $self, $class;
        return $self;
}

sub get_io_pairs {
        my $self = shift;

        return $self->{ _io_pairs };
}

sub set_io_pairs {
        my $self		= shift;
        my $io_pairs	= shift;

        $self->{ _io_pairs } = $io_pairs;
        return;
}

sub add_io_pair {
        my $self = shift;
        my $io_pair = shift;

        push(@{ $self->{_io_pairs} }, $io_pair);
        return;
}

sub get_out_constr {
        my $self = shift;

        return $self->{_out_constr};
}

sub set_out_constr {
        my $self =  shift;
        my $out_constr = shift;

        $self->{_out_constr} = $out_constr;
        return;
}

sub get_instance_num() {
        my $self = shift;
        my @io_pairs = @{ $self->get_io_pairs() };

        return scalar @io_pairs;
}

sub get_input_size() {
        my $self = shift;

        my @io_pairs = @{ $self->{_io_pairs} };
        if ( (scalar @io_pairs) == 0) {
                die "IO-pairs is empty!\n";
        }
        my $io_pair = $io_pairs[0];

        return $io_pair->get_input_size();
}

sub get_init_arr {
        my $self = shift;

        my $input_size = $self->get_input_size();
        my @init_arr = ();

        for ($i = 0; $i < $input_size; $i++) {
                push(@init_arr, 1);
        }

        my @io_pairs = @{ $self->get_io_pairs() };
        foreach ( @io_pairs ) {
                my @in_vars = @{ $_->get_in_vars() };
                if ( (scalar @init_arr) != (scalar @in_vars) ) {
                        my $init_arr_size = scalar @init_arr;
                        my $in_vars_size  = scalar @in_vars;
                        return undef;
                        # die "There is an inconsistency in the size of init_arr and in_vars!\n" .
                        #     "init_arr_size = $init_arr_size VS. in_vars_size = $in_vars_size\n";
                }

                for ($i = 0; $i < $input_size; $i++) {
                        $init_arr[$i] = $init_arr[$i] && $in_vars[$i]->{_is_init};
                }
        }
        return \@init_arr;
}

sub synchronize_uninit_vars {   # @hoang remove_uninit_vars($init_arr)
        my $self = shift;
        my $ptr_init_arr = shift;

        my @io_pairs = @{ $self->get_io_pairs() };

        foreach( @io_pairs ) {
                $_->synchronize_uninit_vars($ptr_init_arr);
        }
}

sub get_out_decls {
        my $self     = shift;
        my $io_index = shift;

        my @decl     =  ();
        my @io_pairs = @{ $self->get_io_pairs() };

        foreach( @io_pairs ) {
                my $out_decl = $_->get_out_decl($io_index);
                push(@decl, $out_decl);
        }

        return join( "", @decl);
}

sub get_decls {
        my $self     = shift;
        my $io_index = shift;

        $logger->debug("AllIOConstr.get_decls called");

        my @decl     =  ();
        my @io_pairs = @{ $self->get_io_pairs() };

        foreach( @io_pairs ) {
                my $decl = $_->get_decl($io_index);
                push(@decl, $decl);
        }

        return join( "", @decl);
}

sub to_string {
        my $self = shift;

        my $result = "";

        my $ins_num = $self->get_instance_num();
        $result .= "ins_num = $ins_num\n";

        $result .= "io-pairs\n";
        foreach ( @{ $self->get_io_pairs() } ) {
                $result .= $_->to_string();
        }

        my $out_constr = $self->get_out_constr();
        $result .= "output constr\n";
        $result .= $out_constr->to_string();

        return $result;
}

1;
