#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;
use lib "./lib";

our $VERSION = v4.0.0;

use Log;
use Server;
use Cores;
use Cache;
use Collection::Ordered;
use RequestHandler;
use UserPrefs;

# ------ initialisation --------------------

my $l = Log->new();

Server::init();
Cache::init();
Collection::Ordered::init();
UserPrefs::init();
Cores::init();
RequestHandler::init();

UserPrefs::save(); 

my $spot;

while (1) { 
	my $status = Server::handle_connections();
	$l->trace("handling status " . @$status);
	while(@$status) {
		my $stat = pop(@$status);
		next unless ref $stat;
		my $id = $stat->[0];
		my $pos = $stat->[1];
		my $col = Collection::Ordered->get($id);
		next if $col->fetch($pos+1);
		$l->debug("try to get $id $pos");
		unless (defined $spot and $spot->{id} eq $id and $spot->{position} == $pos) {
			my $ent = $col->fetch($pos);
			if ($ent) { 
				$spot = $ent->get_spot();
				$spot->mount();
			}
			else {
				next;
			}
		}
		$spot = $spot->next();
		next unless $spot;
		if ($spot->mount()) {
			my $ent = $spot->fetch();
			$col->store($ent) if $ent;
		}
		$col->clean();
		last;
	}
	UserPrefs::save(); 
}

