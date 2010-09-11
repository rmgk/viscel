#!perl
#this program is free software it may be redistributed under the same terms as perl itself
package Log;

use 5.012;
use warnings;

our $VERSION = 1;
our ($TRACE, $DEBUG, $INFO, $WARN, $ERROR, $FATAL, $SILENT) = 0..6;
my $DEFAULT = $INFO;

#$class, \%settings -> \%self
#consctructor
sub new {
	my ($class,$self) = @_;
	$self //= {}; 
	$self->{level} //= $DEFAULT;  
	bless $self, $class;
	return $self;
}

#$level, $msg
#logs $msg if $level is high enough
sub log {
	my ($s) = shift;
	say join '', @_;
} 

#$msg
sub trace { 
	my ($s) = shift;
	return if ($TRACE < $s->{level});
	$s->log(@_);
}
#$msg
sub debug { 
	my ($s) = shift;
	return if ($DEBUG < $s->{level});
	$s->log(@_);
}
#$msg
sub info { 
	my ($s) = shift;
	return if ($INFO < $s->{level});
	$s->log(@_);
}
#$msg
sub warn { 
	my ($s) = shift;
	return if ($WARN < $s->{level});
	$s->log(@_);
}
#$msg
sub error { 
	my ($s) = shift;
	return if ($ERROR < $s->{level});
	$s->log(@_);
}
#$msg
sub fatal { 
	my ($s) = shift;
	return if ($FATAL < $s->{level});
	$s->log(@_);
}

1;
