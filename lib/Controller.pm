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
my $HS; #hint state, chaches the current read spot for efficency
my $MS = {}; #maintanance state

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
	my $timeout = $main::IDLE;
	while (1) {
		my $hint = Server::handle_connections($timeout);
		if ($hint) { #hint is undef if no connection was accepted
			handle_hint($hint);
			$timeout = $main::IDLE; #reactivating timeout
		}
		else {
			$l->info('doing maintanance');
			if (do_maintanance()) {
				$timeout = $main::IDLE = 1000000; #deactivating timeout
			}
			else {
				$timeout = 0; #instant timeout to get some work done
			}
		}
	}
}

#does some maintanance work
sub do_maintanance {
	if ($MS->{all_done}) {
		$l->trace('all done');
		return 1;
	}
	if (!$MS->{keep_current_done}) {
		$l->trace('keep current');
		$MS->{keep_current_done} = 1 if  keep_current();
		return;
	}
	if (!$MS->{check_collections_done}) {
		$l->trace('check collections');
		if (check_collections()) {
			$MS->{all_done} = 1;
			return 1;
		}
		return;
	}
}

sub check_collections {
	my @to_update;
	if ($MS->{to_check} and @{$MS->{to_check}}) {
		@to_update = @{$MS->{to_check}};
	}
	else {
		return 1 if $MS->{got_check_list};
		@to_update = Collection->list();
		$l->trace(@to_update);
		$MS->{got_check_list} = 1;
	}
	my $next_check = shift @to_update;
	return unless $next_check;
	$MS->{to_check} = \@to_update;
	$l->trace('check first of ' , $next_check);
	my $col = Collection->get($next_check);
	my $first_ent = $col->fetch(1);
	return unless $first_ent;
	my $r_first = Cores::first($next_check);
	return unless $r_first;
	return unless $r_first->mount();
	my $r_first_ent = $r_first->element();
	if (my $attr = $first_ent->differs($r_first_ent)) {
		$l->error('attribute ', $attr, ' of first is inconsistent ', $next_check);
		$col->purge();
		return;
	}
	return;
}

#fetching new content
sub keep_current {
	my $spot = $MS->{spot};
	my $c = UserPrefs->section('keep_current');
	unless ($spot and ($c->get($spot->id()))) {
		my @to_update;
		if ($MS->{to_update} and @{$MS->{to_update}}) {
			@to_update = @{$MS->{to_update}};
		}
		else {
			if ($MS->{got_list}) {
				return 1;
			}
			@to_update = grep {$c->get($_)} $c->list();
			$MS->{got_list} = 1;
		}
		my $next_update = shift @to_update;
		return unless $next_update;
		$MS->{to_update} = \@to_update;
		Cores::new($next_update)->fetch_info();
		my $col = Collection->get($next_update);
		my $last = $col->last();
		if ($last) {
			$spot = $col->fetch($last)->create_spot();
		}
		else {
			$spot = Cores::first($next_update);
		}
		$spot->mount();
	}
	
	$spot = $spot->next();
	unless ($spot) {
		$MS->{spot} = undef;
		return;
	}
	my $col = Collection->get($spot->id);
	if ($spot->mount()) {
		my $blob = $spot->fetch();
		my $ent = $spot->element();
		$col->store($ent,$blob) if $ent;
	}
	else {
		$MS->{spot} = undef;
		return;
	}
	$col->clean();
	$MS->{spot} = $spot;
	return;
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
	my $core = Cores::new($id);
	$core->fetch_info();
	my $col = Collection->get($id);
	return undef if $col->fetch(1);
	my $spot = Cores::first($id);
	_store($col,$spot);
	$col->clean();
	$HS = $spot;
	return 1;
}

#$id,$pos
#handles viewer hints
sub hint_view {
	my ($id,$pos) = @_;
	$l->trace("handle view hint $id $pos");
	my $col = Collection->get($id);
	#this code allows to detect missing blobs and download them
	#but as this feature seems to involve too much trouble its 
	#use should currently not be supported, so this code is 
	#commented out. (KISS)
	# if (my $elem = $col->fetch($pos)) {
		# unless ($elem->sha1) {
			# my $spot = $elem->create_spot();
			# $col->delete($elem->position()); # remove the old element to store the new
			# unless (_store($col,$spot)) {
				# $col->store($elem); #get the old element badck if the new cant be stored
			# }
		# }
		# if ($elem = $col->fetch($pos+1)) {
			# unless ($elem->sha1) {
				# my $spot = $elem->create_spot();
				# $col->delete($elem->position()); # remove the old element to store the new
				# unless (_store($col,$spot)) {
					# $col->store($elem); #get the old element badck if the new cant be stored
				# }
			# }
			# return undef;
		# }
	# }
	return undef if $col->fetch($pos+1);
	$l->debug("try to get $id $pos");
	my $spot = $HS;
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
	_store($col,$spot);
	$col->clean();
	$HS = $spot;
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
	my $spot = $col->fetch($last)->create_spot();
	return undef unless $spot;
	$spot->mount();
	while ($spot = $spot->next()) {
		_store($col,$spot);
	};
	$col->clean();
	$HS = $spot;
	return 1;
}

#$col,$spot
#stores the element of the spot into the collection
sub _store {
	my ($col,$spot) = @_;
	return undef unless $spot and $spot->mount();
	my $blob = $spot->fetch();
	my $elem = $spot->element();
	if ($elem) {
		$col->store($elem,$blob) if $elem;
		return 1;
	}
	return undef;
}


1;
