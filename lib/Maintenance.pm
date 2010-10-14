#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Maintenance;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;

my $l = Log->new();

#constructor
sub new {
	my ($class,$self) = @_;
	$self = {state => 'update_cores'};
	bless $self, $class;
	$self->{cfg} = UserPrefs::parse_file(__PACKAGE__);
	return $self;
}

#does some maintenance work
sub tick {
	my ($s) = @_;
	given ($s->{state}) {
		when ('update_cores') { $s->update_cores_lists() or $s->{state} = 'check_collections' }
		when ('check_collections') { $s->check_collections() or $s->{state} = 'keep_current' }
		when ('keep_current') { $s->keep_current() or $s->{state} = 'done' }
		when ('done') { return undef }
		default { return undef }
	}
	UserPrefs::save_file(__PACKAGE__,$s->{cfg});
	return 1;
}

sub cfg {
	my ($s,$sect) = @_;
	$s->{cfg}->{$sect} ||= {};
	return $s->{cfg}->{$sect};
}

#resets the non global state
sub reset {
	my ($s) = @_;
	$s->{istate} = undef;
}

#updates the lists of collections of the cores
sub update_cores_lists {
	my ($s) = @_;
	my $c = $s->cfg('update_core_list');
	my @cores_to_check = grep {time - ($c->{$_}||0) > 360000} Cores::list();
	unless (@cores_to_check) {
		$l->debug('all core lists up to date');
		return undef;
	}
	my $core = shift @cores_to_check;
	$c->{$core} = time;
	$core->update_list();
	return 1;
}

#checks the collections for errors
sub check_collections {
	my ($s) = @_;
	my $c = $s->cfg('consistency_check');
	my @to_update = grep {(time - ($c->{$_}||0)) > 36000} Collection->list();
	unless (@to_update) {
		$l->debug('consistency check complete');
		return undef;
	}
	my $next_check = shift @to_update;
	$c->{$next_check} = time;
	return 1 unless $next_check;
	$l->trace('check ' , $next_check);
	my $col = Collection->get($next_check);
	unless (Cores::new($next_check)) { # unknown collections get purged
		$col->purge();
		return 1;
	}
	my $last_pos = $col->last();
	unless ($last_pos) {
		$l->error('has no elements', $next_check);
		$col->purge();
		return 1;
	}
	if ($last_pos > 1) {
		my $last_elem = $col->fetch($last_pos);
		my $r_last = $last_elem->create_spot();
		return 1 unless $r_last->mount();
		my $r_last_elem = $r_last->element();
		if (my $attr = $last_elem->differs($r_last_elem)) {
			$l->error('attribute ', $attr, ' of last is inconsistent ', $next_check);
			$col->delete($last_pos);
			return;
		}
	}
	#checking first
	my $first_ent = $col->fetch(1);
	return 1 unless $first_ent;
	my $r_first = Cores::first($next_check);
	return 1 unless $r_first;
	return 1 unless $r_first->mount();
	my $r_first_ent = $r_first->element();
	if (my $attr = $first_ent->differs($r_first_ent)) {
		$l->error('attribute ', $attr, ' of first is inconsistent ', $next_check);
		$col->purge();
		return 1;
	}
	return 1;
}

#fetching new content
sub keep_current {
	my ($s) = @_; 
	my $spot = $s->{istate};
	my $up = UserPrefs->section('keep_current');
	my $c = $s->cfg('keep_current');
	unless ($spot) {
		my @to_update = grep {$up->get($_) and (!$c->{$_} or (time - $c->{$_}) > 36000)} $up->list();
		unless (@to_update) {
			$l->debug('selected collections kept current');
			$s->{istate} = undef;
			return undef;
		}
		my $next_update = shift @to_update;
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
	my $id = $spot->id;
	$spot = $spot->next();
	my $col = Collection->get($id);
	unless (Controller::_store($col,$spot)) {
		$s->{istate} = undef;
		$c->{$id} =  time;
		return 1;
	}
	$col->clean();
	$s->{istate} = $spot;
	return 1;
}

1;
