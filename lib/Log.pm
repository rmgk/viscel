#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Log v1.0.0;

use 5.012;
use warnings;

our ($TRACE, $DEBUG, $INFO, $WARN, $ERROR, $FATAL, $SILENT) = 0..6;

#$class, \%settings -> \%self
#constructor
sub new {
	my ($class,$self) = @_;
	$self //= {}; 
	#$self->{level} //= $main::LOGLVL;
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
	$module =~ s/\w+::/:/;
	#getting the call depth
	my $d = 1;
	while(caller(++$d)){}; 
	say $line,
		' ' x (3-length($line)), #number of spaces depends on line numer length
		'-' x ($d-2) , '>', #add call depth indicator
		$module, 
		' ' x ((24-$d-length($module))), #uhm well it works for now
		join '', grep {defined} @_; #the message itself
} 

#$msg
sub trace { 
	my ($s) = shift;
	return if ($TRACE < $main::LOGLVL);
	print 'TRACE ';
	$s->log(@_);
}
#$msg
sub debug { 
	my ($s) = shift;
	return if ($DEBUG < $main::LOGLVL);
	print 'DEBUG ';
	$s->log(@_);
}
#$msg
sub info { 
	my ($s) = shift;
	return if ($INFO < $main::LOGLVL);
	print 'INFO  ';
	$s->log(@_);
}
#$msg
sub warn { 
	my ($s) = shift;
	return if ($WARN < $main::LOGLVL);
	print 'WARN  ';
	$s->log(@_);
}
#$msg
sub error { 
	my ($s) = shift;
	return if ($ERROR < $main::LOGLVL);
	print 'ERROR ';
	$s->log(@_);
}
#$msg
sub fatal { 
	my ($s) = shift;
	return if ($FATAL < $main::LOGLVL);
	print 'FATAL ';
	$s->log(@_);
}

1;
