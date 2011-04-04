#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Maintenance v1.1.0;

use 5.012;
use warnings;

use Log;
use Try::Tiny;
use Data::Dumper;

my $l = Log->new();

#constructor
sub new {
	my ($class,$self) = @_;
	$self = {state => 'update_cores'};
	bless $self, $class;
	$self->{cfg} = ConfigINI::parse_file(Globals::datadir,$class);
	return $self;
}

#does some maintenance work
sub tick {
	my ($s) = @_;
	if ($s->{continuation}) {
		Log->trace('do continuation');
		delete $s->{continuation} unless $s->{continuation}->();
		ConfigINI::save_file(Globals::datadir,ref($s),$s->{cfg});
	}
	else {
		given ($s->{state}) {
			when ('update_cores') {
				$s->{state} = 'check_collections'
					unless $s->{continuation} = $s->update_cores_lists();
			}
			when ('check_collections') {
				$s->{state} = 'keep_current'
					unless $s->{continuation} = $s->check_collections();
			}
			when ('keep_current') {
				$s->{state} = 'fetch_info'
					unless $s->{continuation} = $s->keep_current();
			}
			when ('fetch_info') {
				$s->{state} = 'done'
					unless $s->{continuation} = $s->fetch_info();
			}
			when ('done') { return }
			default { return }
		}
		Log->trace('maintenance state ', $s->{state});
	}
	return 1;
}

sub cfg {
	my ($s,$sect) = @_;
	$s->{cfg}->{$sect} ||= {};
	return $s->{cfg}->{$sect};
}

#section,@keylist -> $config, @timelist
#takes a section and a list of keys
#and returns the config and the list of array
#refs with the id and the times
sub time_list {
	my ($s,$section) = (shift,shift);
	my $c = $s->cfg($section);
	my @return;
	for my $key (@_) {
		my ($last_time,$next_time) = split(/:/,$c->{$key}//'');
		$last_time ||= 0; $next_time ||= 209600;
		push(@return, [$key,$last_time,$next_time]) if (time - $last_time > $next_time);
	}
	return $c, @return;
}

#updates the lists of collections of the cores
sub update_cores_lists {
	my ($s) = @_;
	Log->trace('initialise update cores list');
	my ($c,@to_check) = $s->time_list('update_core_list',Cores::initialised());
	return () unless @to_check;
	Log->trace('start update cores list');
	my $core;
	my $fetch;
	return sub {
		unless ($core) {
			return () unless @to_check;
			$core = shift @to_check;
			$fetch = $core->[0]->fetch_list();
		}
		return try {
			if (my $list = $fetch->()) {
				my @oldkeys = $core->[0]->clist();
				if ((@oldkeys == keys %$list) and
					!(grep {!exists $list->{$_}} @oldkeys) )
				{ #there are no new remotes
					$c->{$core->[0]} = join ':', time, $core->[2] * 1.2;
				}
				else {
					$c->{$core->[0]} = join ':', time, $core->[2] * 0.7;
				}
				#update the list either way
				$core->[0]->clist($list);
				$core->[0]->save_clist();
				$core = undef;
				$fetch = undef;
			}
			return 1;
		}
		catch {
			die "there was an unhandled error, please fix!\n" . Dumper $_;
		};
	};
}

#checks the collections for errors
sub check_collections {
	my ($s) = @_;
	Log->trace('initialise check collections');
	my ($c,@to_update) = $s->time_list('consistency_check',Collection->list());
	return () unless @to_update;
	
	my $next_check;
	return sub {
		$next_check = shift @to_update;
		return () unless $next_check;
		$l->trace('check ' , $next_check->[0]);
		if (try { check_collection($next_check->[0]) } ) {
			$c->{$next_check->[0]} = join ':', time, $next_check->[2] * 0.7;
		}
		else {
			$c->{$next_check->[0]} = join ':', time, $next_check->[2] * 1.2;
		}
		return 1;
	}
}

#$collections
#checks the given collection
sub check_collection {
	my ($next_check) = @_;
	my $col = Collection->get($next_check);
	unless (Cores::known($next_check)) {
		#$col->purge();
		return 1;
	}
	my $last_pos = $col->last();
	unless ($last_pos) {
		$l->error('has no elements', $next_check);
		$col->purge();
		return 1;
	}
	$l->trace("checking last ($last_pos) of $next_check");
	if ($last_pos > 1) {
		my $last_elem = $col->fetch($last_pos);
		my $r_last = $last_elem->create_spot();
		return 1 unless $r_last->mount();
		my $r_last_elem = $r_last->element();
		if (my $attr = $last_elem->differs($r_last_elem)) {
			$l->error('attribute ', $attr, ' of last is inconsistent ', $next_check);
			$col->delete($last_pos);
			return 1;
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
	Log->trace('initialise keep current');
	my $up = UserPrefs->section('bookmark');
	my ($c,@to_update) = $s->time_list('keep_current',$up->list());
	return () unless @to_update;
	
	my $spot;
	my $current;
	return sub {
		unless ($spot) {
			unless (@to_update) {
				$l->debug('selected collections kept current');
				return ();
			}
			$current = shift @to_update;
			my $id = $current->[0];
			my $remote = Cores::new($id);
			try {
				$remote->clist($remote->fetch_info()) if $remote->want_info();
			} catch {
				die "there was an unhandled error, please fix!\n" . Dumper $_;
			};
			my $col = Collection->get($id);
			my $last = $col->last();
			if ($last) {
				$spot = $col->fetch($last)->create_spot();
			}
			else {
				$spot = Cores::first($id);
			}
			try {
				$spot->mount();
			} catch {
				die "there was an unhandled error, please fix!\n" . Dumper $_;
			};	
		}
		my $id = $spot->id;
		if (Collection->get($id)->fetch($spot->position + 1)) {
			$spot = undef;
		}
		else {
			try {
				$spot = $spot->next();
			}
			catch {
				die "there was an unhandled error, please fix!\n" . Dumper $_;
			};
			my $col = Collection->get($id);
			if (!Controller::_store($col,$spot)) {
				$spot = undef;
			}
			else {
				$current->[3] ||= $current->[2] * 1.2;
				$col->clean();
			}
		}
		unless ($spot) {
			$c->{$current->[0]} = join ':', time, $current->[3] || $current->[2] * 0.7;
			$current = undef;
		}
		return 1;
	}
}

#fetches more info for collections
sub fetch_info {
	my ($s) = @_;
	Log->trace('initialise fetch info');
	my @fetch_list;
	for my $core (Cores::initialised()) {
		push(@fetch_list, $core->list_need_info());
	}
	return () unless @fetch_list;
	
	return sub {
		my $id = shift @fetch_list;
		return () unless $id;
		my $remote = Cores::new($id);
		return try {
			$remote->clist($remote->fetch_info());
		} catch {
			die "there was an unhandled error, please fix!\n" . Dumper $_;
		};
	}
}

1;
