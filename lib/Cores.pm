#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cores;

use 5.012;
use warnings;

our $VERSION = v1;

use Core::AnyManga;
use Core::Comcol;
use Core::ComicGenesis;
use Log;

my $l = Log->new();
my %cores = (	'Core::AnyManga' => 1,
				'Core::Comcol' => 1,
				'Core::ComicGenesis' => 1,
				);

#->@cores
#returns the list of used cores
sub list {
	$l->trace('core list requested');
	return grep {$cores{$_} } keys %cores;
}

#initialises all used cores
sub init {
	$l->trace('initialising cores');
	for (list()) {
		unless ($_->init()) {
			$cores{$_} = 0;
		}
	}
	return 1;
}

1;
