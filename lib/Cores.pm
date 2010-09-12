#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cores;

use 5.012;
use strict;
use warnings;

use Log::Log4perl qw(get_logger);

my $l = get_logger();

use Core::Inverloch;

sub list {
	$l->trace('core list requested: ', caller);
	return qw(Inverloch);
}

sub get {
	my ($cid) = @_;
	$l->trace('get core ', $cid);
	return Core::Inverloch->new();
}

1;