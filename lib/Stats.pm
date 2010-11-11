#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Stats v1.1.0;

use 5.012;
use warnings;

use Time::HiRes;
use Globals;

my $FH;

sub init {
	open($FH,'>>',Globals::datadir() . 'stats.txt');
	return 1;
}

#$event, $value
#saves statistics for $event with $value
sub add {
	my ($event,$value) = @_;
	my $time = Time::HiRes::time();
	print $FH "$time\t$event\t$value\n";
	return 1;
}

1;
