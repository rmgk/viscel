#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Log;

use 5.012;
use warnings;

our $VERSION = v1;
our ($TRACE, $DEBUG, $INFO, $WARN, $ERROR, $FATAL, $SILENT) = 0..6;
my $DEFAULT = $TRACE;

#$class, \%settings -> \%self
#constructor
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
	my @call = caller 1;
	my $line = $call[2];
	my $module = $call[0];
	$module =~ s/\w+::/:/g;
	#getting the call depth
	my $d = 1;
	while(caller(++$d)){}; 
	say $line,
		' ' x (3-length($line)), #number of spaces depends on line numer length
		'-' x ($d-2) , '>', #add call depth indicator
		$module, 
		"\t" x ((6/length($module))+1.5), #uhm well it works for now
		join '', @_; #the message itself
} 

#$msg
sub trace { 
	my ($s) = shift;
	return if ($TRACE < $s->{level});
	print 'TRACE ';
	$s->log(@_);
}
#$msg
sub debug { 
	my ($s) = shift;
	return if ($DEBUG < $s->{level});
	print 'DEBUG ';
	$s->log(@_);
}
#$msg
sub info { 
	my ($s) = shift;
	return if ($INFO < $s->{level});
	print 'INFO  ';
	$s->log(@_);
}
#$msg
sub warn { 
	my ($s) = shift;
	return if ($WARN < $s->{level});
	print 'WARN  ';
	$s->log(@_);
}
#$msg
sub error { 
	my ($s) = shift;
	return if ($ERROR < $s->{level});
	print 'ERROR ';
	$s->log(@_);
}
#$msg
sub fatal { 
	my ($s) = shift;
	return if ($FATAL < $s->{level});
	print 'FATAL ';
	$s->log(@_);
}

1;
