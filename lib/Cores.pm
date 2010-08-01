#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
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