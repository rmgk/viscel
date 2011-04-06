#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Maintenance v1.3.0;

use 5.012;
use warnings;

use Log;
use Try::Tiny;
use Data::Dumper;

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
		Log->trace('do continuation ' , $s->{state});
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
				$s->{state} = 'done'
					unless $s->{continuation} = $s->keep_current();
			}
			#when ('fetch_info') {
			#	$s->{state} = 'done'
			#		unless $s->{continuation} = $s->fetch_info();
			#}
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
	Log->trace('create time list');
	my $c = $s->cfg($section);
	my @return;
	for my $key (@_) {
		my ($last_time,$next_time) = split(/:/,$c->{$key}//'');
		$last_time ||= 0; $next_time ||= 209600;
		push(@return, [$key,$last_time,$next_time]) if (time - $last_time > $next_time);
	}
	return $c, @return;
}

#$config, $current, $factor
#adjusts the time values according to factor
sub adjust_time {
	my ($config,$current,$factor) = @_;
	Log->trace('adjusting time of ', $current->[0], ' by factor ', $factor); 
	$config->{$current->[0]} = join ':', time, $current->[2] * $factor;
}

#$error -> $bool
#returns true if $error seems to be temporary
sub is_temporary {
	my ($error) = @_;
	if (ref $error) {
		return (($error->[0] =~ /^(get page|fetch element)$/) and ($error->[1] >= 500));
	}
	return ();
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
			Log->info('update core ', $core->[0]);
		}
		return try {
			if (my $list = $fetch->()) {
				my @oldkeys = $core->[0]->clist();
				if ((@oldkeys == keys %$list) and
					!(grep {!exists $list->{$_}} @oldkeys) )
				{ #there are no new remotes
					adjust_time($c,$core,1.2);
				}
				else {
					adjust_time($c,$core,0.7);
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
			my $error = $_;
			if (is_temporary($error)) { 
				Log->warn("updating core list $core had an error");
				return 1;
			}
			else {
				die "there was an unhandled error, please fix!\n" . Dumper $error;
			}
		};
	};
}

#checks the collections for errors
sub check_collections {
	my ($s) = @_;
	Log->trace('initialise check collections');
	my ($c,@to_update) = $s->time_list('consistency_check',Collection->list());
	return () unless @to_update;
	
	my $current;
	my %seen;
	return sub {
		unless ($current) {
			$current = shift @to_update;
			return () unless $current;
			Log->info('check collection ' , $current->[0]);
			unless (Cores::known($current->[0])) {
				Log->warn('unknown collection ', $current->[0]);
				adjust_time($c,$current,1);
				$current = undef;
				return 1;
			}
			try {
				if (!check_first($current->[0])) {
					adjust_time($c,$current,0.7);
					$current = undef;
				}
			} catch {
				my $error = $_;
				if (is_temporary($error)) {
					Log->warn("error check first of ", $current->[0]);
					push @to_update, $current unless $seen{$current->[0]};
					$seen{$current->[0]} = 1;
					adjust_time($c,$current,1);
					$current = undef;
				}
				else {
					die "there was an unhandled error, please fix!\n" . Dumper $error;
				}
			};
			return 1;
		}
		try {
			if (my $ret = check_last($current->[0])) {
				adjust_time($c,$current,$current->[3]||($ret<0 ? 0.7 : 1.2));
				$current = undef;
			}
			else {
				$current->[3] = 0.7;
			}
		} catch {
			my $error = $_;
			if (is_temporary($error)) {
				Log->warn("error check last of ", $current->[0]);
				push @to_update, $current unless $seen{$current->[0]};
				$seen{$current->[0]} = 1;
				adjust_time($c,$current,1);
				$current = undef;
			}
			else {
				die "there was an unhandled error, please fix!\n" . Dumper $error;
			}
		};

		return 1;
	}
}

#$id -> $bool
#returns true if first of $id
#matches local and remote
sub check_first {
	my ($id) = @_;
	Log->trace('check first ', $id);
	my $col = Collection->get($id);
	my $first_elem = $col->fetch(1);
	return 1 unless $first_elem;
	my $r_first = Cores::first($id);
	return 1 unless $r_first;
	return try {
		$r_first->mount();
		my $r_first_elem = $r_first->element();
		if (my $attr = $first_elem->differs($r_first_elem)) {
			Log->debug('attribute ', $attr, ' of first is inconsistent ', $id);
			$col->purge();
			return 0;
		}
		return 1;
	}
	catch {
		my $error = $_;
		die $error if (is_temporary($error)); #temporary errors are handled further up
		Log->error('page of first no longer parses ', $id);
		$col->purge();
		return 0;
	};
}

#$id -> $bool
#checks the given collection
sub check_last {
	my ($next_check) = @_;
	my $col = Collection->get($next_check);

	my $last_pos = $col->last();
	unless ($last_pos) {
		Log->error('has no elements', $next_check);
		$col->purge();
		return -1;
	}
	Log->trace("check last ($last_pos) of $next_check");
	if ($last_pos >= 1) {
		my $last_elem = $col->fetch($last_pos);
		my $r_last = $last_elem->create_spot();
		return unless try {
			$r_last->mount();
			return 1;
		} catch {
			my $error = $_;
			die $error if (is_temporary($error)); #temporary errors are handled further up
			Log->error('page of last no longer parses ', $next_check);
			$col->delete($last_pos);
			return 0;
		};
		my $r_last_elem = $r_last->element();
		if (my $attr = $last_elem->differs($r_last_elem)) {
			Log->error('attribute ', $attr, ' of last is inconsistent ', $next_check);
			$col->delete($last_pos);
			return 0;
		}
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
	my %seen;
	return sub {
		unless ($spot) {
			unless (@to_update) {
				Log->debug('selected collections kept current');
				return ();
			}
			$current = shift @to_update;
			my $id = $current->[0];
			my $remote = Cores::new($id);
			try {
				if ($remote->want_info()) {
					$remote->clist($remote->fetch_info());
					$remote->save_clist();
				}
				my $col = Collection->get($id);
				my $last = $col->last();
				if ($last) {
					$spot = $col->fetch($last)->create_spot();
				}
				else {
					$spot = Cores::first($id);
				}
				$spot->mount();
			} catch {
				my $error = $_;
				if (is_temporary($error)) {
					Log->warn("error keep current of ", $current->[0]);
					push @to_update, $current unless $seen{$current->[0]};
					$seen{$current->[0]} = 1;
					$current = undef;
					$spot = undef;
				}
				elsif (ref($error) and ($error->[0] =~ /^(get page|fetch element)$/)) {
					Log->warn("error keep current of ", $current->[0], ' code ', $error->[1]);
					$spot = undef;
				}
				elsif (ref($error) and ($error->[0] eq 'mount failed')) {
					Log->warn("error keep current of ", $current->[0]);
					$spot = undef;
				}
				else {
					die "there was an unhandled error, please fix!\n" . Dumper $error;
				}
			};
			return 1;
		}
		my $id = $spot->id;
		my $col = Collection->get($id);
		if ($col->fetch($spot->position + 1)) {
			$spot = undef;
		}
		else {
			try {
				return unless $spot = $spot->next();
				$spot->mount();
				my $blob = $spot->fetch();
				my $elem = $spot->element();
				if ($elem and $blob and $col->store($elem,$blob)) {
					$col->clean();
					$current->[3] = 0.7;
				}
				else {
					$spot = undef;
				}
			} catch {
				my $error = $_;
				if (is_temporary($error)) {
					Log->warn("error keep current of ", $current->[0]);
					push @to_update, $current unless $seen{$current->[0]};
					$seen{$current->[0]} = 1;
					$spot = undef;
				}
				elsif (ref($error) and ($error->[0] =~ /^(get page|fetch element)$/)) {
					Log->warn("error keep current of ", $current->[0], ' code ', $error->[1]);
					$spot = undef;
				}
				elsif (ref($error) and ($error->[0] eq 'mount failed')) {
					Log->warn("error keep current of ", $current->[0]);
					$spot = undef;
				}
				else {
					die "there was an unhandled error, please fix!\n" . Dumper $error;
				}
			};
		}
		unless ($spot) {
			adjust_time($c,$current,$current->[3]||1.2);
			$current = undef;
		}
		return 1;
	}
}

##fetches more info for collections
#sub fetch_info {
#	my ($s) = @_;
#	Log->trace('initialise fetch info');
#	my @fetch_list;
#	for my $core (Cores::initialised()) {
#		push(@fetch_list, $core->list_need_info());
#	}
#	return () unless @fetch_list;
#	my %seen;
#	return sub {
#		my $id = shift @fetch_list;
#		return () unless $id;
#		my $remote = Cores::new($id);
#		return try {
#			$remote->clist($remote->fetch_info()) and
#			$remote->save_clist();
#		} catch {
#			my $error = $_;
#			if (is_temporary($error)) {
#				Log->warn("fetch info of ", $id);
#				push @fetch_list, $id unless $seen{$id};
#				$seen{$id} = 1;
#				return 1;
#			}
#			else {
#				die "there was an unhandled error, please fix!\n" . Dumper $error;
#			}
#		};
#	}
#}

1;
