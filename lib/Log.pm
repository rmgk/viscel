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
	my ($s,$msg) = @_;
	say $msg;
} 

#$msg
sub trace { 
	my ($s,$msg) = @_;
	return if ($TRACE < $s->{level});
	$s->log($msg);
}
#$msg
sub debug { 
	my ($s,$msg) = @_;
	return if ($DEBUG < $s->{level});
	$s->log($msg);
}
#$msg
sub info { 
	my ($s,$msg) = @_;
	return if ($INFO < $s->{level});
	$s->log($msg);
}
#$msg
sub warn { 
	my ($s,$msg) = @_;
	return if ($WARN < $s->{level});
	$s->log($msg);
}
#$msg
sub error { 
	my ($s,$msg) = @_;
	return if ($ERROR < $s->{level});
	$s->log($msg);
}
#$msg
sub fatal { 
	my ($s,$msg) = @_;
	return if ($FATAL < $s->{level});
	$s->log($msg);
}




1;	