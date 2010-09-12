#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;
use lib "./lib";

our $VERSION = v4.0.0;

use Log;
use Server;
use Core::Comcol;
use Cache;

# ------ initialisation --------------------

my $l = Log->new();

#Server::init();
Cache::init();

my $i = 1;

Core::Comcol::init();
for my $comic (qw(Inverloch)) {
	my $spot = Core::Comcol->create('Comcol_'.$comic,1);
	next unless $spot;
	while ($spot->mount()) {
		my $ent = $spot->fetch();
		unless ($ent) {
			$spot = $spot->next();
			next;
		}
		my $blob = \$ent->{blob}; 
		Cache::put($ent->{sha1},$blob);
		$spot = $spot->next();
	}
}