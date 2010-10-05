#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Controller;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;
use Server;
use Cores;
use Cache;
use Collection;
use RequestHandler;
use UserPrefs;

my $l = Log->new();
my $spot; #caching the spot makes linear traversation faster

#initialises the needed modules
sub init {
	$l->trace('initialise modules');
	if (
		UserPrefs::init() &&
		Cache::init() &&
		Collection::init() &&
		Server::init() &&
		RequestHandler::init() &&
		Cores::init()
	) {
		return 1;
	}
	$l->error('failed to initialise modules');
	return undef;
}


#starts the program, never returns
sub start {
	$l->trace('run main loop');
	while (1) {
		my $hint = Server::handle_connections();
		if ($hint) { #hint is undef if no connection was accepted
			handle_hint($hint);
		}
		UserPrefs::save(); 
	}
}

#\@hint
#handles hints from server
sub handle_hint {
	my @hint = @{$_[0]};
	$l->trace("handle " . @hint . " hints");
	while(@hint) {
		my $hint = pop(@hint); #more recent hints are more interesting
		if (ref $hint eq 'CODE') { #this gives handlers great flexibility for hints
			$l->trace('run code hint');
			$hint->();
		}
		if (ref $hint eq 'ARRAY') {
			given (shift @$hint) {
				when ('front') {hint_front(@$hint)}
				when ('view') {hint_view(@$hint)}
				when ('getall') {hint_getall(@$hint)}
				default {$l->warn("unknown hint $_")}
			}
		}
	}
}

#$id
#handles front hints
sub hint_front {
	my ($id) = @_;
	$l->trace('handle front hint '.$id);
	my $col = Collection->get($id);
	return undef if $col->fetch(1);
	$spot = Cores::first($id);
	if ($spot->mount()) {
		my $blob = $spot->fetch();
		my $ent = $spot->entity();
		$col->store($ent,$blob) if $ent;
	}
	$col->clean();
	return 1;
}

#$id,$pos
#handles viewer hints
sub hint_view {
	my ($id,$pos) = @_;
	$l->trace("handle view hint $id $pos");
	my $col = Collection->get($id);
	return undef if $col->fetch($pos+1);
	$l->debug("try to get $id $pos");
	unless (defined $spot and $spot->id eq $id and $spot->position == $pos) {
		my $ent = $col->fetch($pos);
		if ($ent) { 
			$spot = $ent->create_spot();
			$spot->mount();
		}
		else {
			$l->debug('could not get spot');
			return undef;
		}
	}
	$spot = $spot->next();
	return undef unless $spot;
	if ($spot->mount()) {
		my $blob = $spot->fetch();
		my $ent = $spot->entity();
		$col->store($ent,$blob) if $ent;
	}
	$col->clean();
	return 1;
}

#$id
#handles getall hints
sub hint_getall {
	my ($id) = @_;
	$l->trace("handle getall hint $id");
	my $col = Collection->get($id);
	$l->debug("get last collected");
	my $last = $col->last();
	return undef unless ($last);
	$spot = $col->fetch($last)->create_spot();
	return undef unless $spot;
	$spot->mount();
	while ($spot = $spot->next()) {
		if ($spot->mount()) {
			my $blob = $spot->fetch();
			my $ent = $spot->entity();
			$col->store($ent,$blob) if $ent;
		}
	};
	$col->clean();
	return 1;
}



1;
