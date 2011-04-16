#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Log v1.3.0;

use 5.012;
use warnings;
use utf8;
use autodie;

use Data::Dumper;
use Globals;


our ($TRACE, $DEBUG, $INFO, $WARN, $ERROR, $FATAL, $SILENT) = 0..6;

$Globals::LOGLVL //= $DEBUG;
$Globals::FILELOG //= $ERROR;

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
	my ($s,$level) = (shift,shift);
	my $addinfo = '';
	$addinfo = (Dumper pop @_)."\n" if ref $_[-1];
	my @call = caller 1;
	my $line = $call[2];
	my $module = $call[0];
	#getting the call depth
	my $d = 1;
	while(caller(++$d)){};
	$line = ' ' x (3-length($line)) . $line;
	my $cdi = ('-' x ($d-2)) . '>';
	$cdi .= $module;
	$cdi .= ' ' x ((24-$d-length($module)));
	substr($cdi,23) = '';
	my $message = $line . $cdi . ' ' . 
		join '', grep {defined} @_; #the message itself
	say $message unless $level == $SILENT;
	return if ($level < $Globals::FILELOG);
	open (my $fh, '>>', 'error.txt');
	print $fh $message ,"\n",$addinfo;
	close $fh;
} 

#$msg
sub trace { 
	my ($s) = shift;
	return if ($TRACE < $Globals::LOGLVL);
	print 'TRACE ';
	$s->log($TRACE,@_);
}
#$msg
sub debug { 
	my ($s) = shift;
	return if ($DEBUG < $Globals::LOGLVL);
	print 'DEBUG ';
	$s->log($DEBUG,@_);
}
#$msg
sub info { 
	my ($s) = shift;
	return if ($INFO < $Globals::LOGLVL);
	print 'INFO  ';
	$s->log($INFO,@_);
}
#$msg
sub warn { 
	my ($s) = shift;
	return if ($WARN < $Globals::LOGLVL);
	print 'WARN  ';
	$s->log($WARN,@_);
}
#$msg
sub error { 
	my ($s) = shift;
	return if ($ERROR < $Globals::LOGLVL);
	print 'ERROR ';
	$s->log($ERROR,@_);
}
#$msg
sub fatal { 
	my ($s) = shift;
	return if ($FATAL < $Globals::LOGLVL);
	print 'FATAL ';
	$s->log($FATAL,@_);
}

1;
