#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Log v1.2.0;

use 5.012;
use warnings;

use Globals;

our ($TRACE, $DEBUG, $INFO, $WARN, $ERROR, $FATAL, $SILENT) = 0..6;

$Globals::LOGLVL //= $DEBUG; #/

#$class, \%settings -> \%self
#constructor
sub new {
	my ($class,$self) = @_;
	$self //= {}; #/ padre display bug
	bless $self, $class;
	return $self;
}

#$msg
#logs $msg 
sub log {
	my ($s) = shift;
	my @call = caller 1;
	my $line = $call[2];
	my $module = $call[0];
	#getting the call depth
	my $d = 1;
	while(caller(++$d)){}; 
	say $line,
		' ' x (3-length($line)), #number of spaces depends on line number length
		'-' x ($d-2) , '>', #add call depth indicator
		$module, 
		' ' x ((24-$d-length($module))), #uhm well it works for now
		join '', grep {defined} @_; #the message itself
} 

#$msg
sub trace { 
	my ($s) = shift;
	return if ($TRACE < $Globals::LOGLVL);
	print 'TRACE ';
	$s->log(@_);
}
#$msg
sub debug { 
	my ($s) = shift;
	return if ($DEBUG < $Globals::LOGLVL);
	print 'DEBUG ';
	$s->log(@_);
}
#$msg
sub info { 
	my ($s) = shift;
	return if ($INFO < $Globals::LOGLVL);
	print 'INFO  ';
	$s->log(@_);
}
#$msg
sub warn { 
	my ($s) = shift;
	return if ($WARN < $Globals::LOGLVL);
	print 'WARN  ';
	$s->log(@_);
}
#$msg
sub error { 
	my ($s) = shift;
	return if ($ERROR < $Globals::LOGLVL);
	print 'ERROR ';
	$s->log(@_);
}
#$msg
sub fatal { 
	my ($s) = shift;
	return if ($FATAL < $Globals::LOGLVL);
	print 'FATAL ';
	$s->log(@_);
}

1;
