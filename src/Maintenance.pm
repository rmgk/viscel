#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Maintenance v1.3.0;

use 5.012;
use warnings;
use utf8;

use Log;
use Try::Tiny;
use Data::Dumper;

#constructor
sub new {
	my ($class,$accept) = @_;
	my $self = {accept => $accept};
	bless $self, $class;
	$self->{cfg} = ConfigINI::parse_file(Globals::datadir,$class);
	return $self;
}

sub accept {
	my ($self) = @_;
	while ($self->{accept}->()) {};
}

sub save_cfg {
	my ($self) = @_;
	ConfigINI::save_file(Globals::datadir,ref($self),$self->{cfg});
}


#does some maintenance work
sub maintain {
	my ($s) = @_;
	Log->trace('start maintenance');
	$s->accept();
	$s->update_cores_lists();
	$s->accept();
	$s->update_collections();
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
	#dont shorten the time if the update was delayed for to long
	if ( ((time - $current->[1]) > 2 * $current->[2]) and $factor < 1) {
		$factor = 1;
	}
	$config->{$current->[0]} = join ':', time, int($current->[2] * $factor);
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
	foreach my $core (@to_check) {
		Log->info('update core ', $core->[0]);
		my $fetch = $core->[0]->fetch_list();
		try {
			my $list;
			until ($list = $fetch->()) {
				$s->accept();
			}
			my @oldkeys = $core->[0]->clist();
			if ((@oldkeys == keys %$list) and
				!(grep {!exists $list->{$_}} @oldkeys) )
			{ #there are no new remotes
				adjust_time($c,$core,1.5);
			}
			else {
				adjust_time($c,$core,0.8);
			}
			#update the list either way
			$core->[0]->clist($list);
			$core->[0]->save_clist();
			$s->save_cfg();
		}
		catch {
			my $error = $_;
			if ($error =~ /^terminate/) {
				die $error;
			}
			elsif (is_temporary($error)) { 
				Log->warn("updating core list ", $core->[0], " had an error");
			}
			else {
				die "there was an unhandled error, please fix!\n" . Dumper $error;
			}
		};
	};
}

#updates collections
sub update_collections {
	my ($s) = @_;
	Log->trace('initialise check collections');
	my $up = UserPrefs->section('bookmark');
	my ($c,@to_update) = $s->time_list('update',$up->list());
	@to_update = grep { Cores::known($_->[0]) } @to_update;
	return () unless @to_update;
	
	foreach my $current (@to_update) {
		my $id = $current->[0];
		Log->info('check collection ' , $id);
		my $spot;
		next unless try {
			if (check_first($id)) {
				until ($spot = check_last($id)) {
					$s->accept();
				}
			}
			return 1;
		} catch {
			if ($_ =~ /^terminate/) {
				die $_;
			}
			Log->error('check collection failed', [$_]);
			adjust_time($c,$current,1.0);
			return 0;
		};
		
		$spot = get_last_spot($current->[0]) unless ref $spot;
		next unless $spot;
		my $col = Collection->get($id);
		my $something;
		while ($spot = fetch_next($spot,$col)) {
			$something = 1;
			$s->accept();
		}
		adjust_time($c,$current, $something ? 0.8 : 1.2);
		$s->save_cfg();
		$s->accept();
	}
}

#$spot,$collection -> $spot
#fetch the next $spot store the
#element in $collection and
#return the $spot
sub fetch_next {
	my ($spot,$col) = @_;
	if ($col->fetch($spot->position + 1)) {
		return;
	}
	return try {
		return unless $spot = $spot->next();
		fetch_store($spot,$col);
	} &catch(std_handler('update ', $spot->id));
}

sub fetch_store {
	my ($spot,$col,$nomount) = @_;
	try {
		$spot->mount();
		my $blob = $spot->fetch();
		my $elem = $spot->element();
		if ($elem and $blob and $col->store($elem,$blob)) {
			return $spot;
		}
		else {
			return ();
		}
	} &catch(std_handler('fetch ', $spot->id));
}

sub std_handler {
	my @desc = @_;
	return sub {
		my $error = $_;
		if (is_temporary($error)) {
			Log->warn("temporary error ", @desc , $error);
		}
		elsif (ref($error) and ($error->[0] =~ /^(get page|fetch element)$/)) {
			Log->warn("network error ", @desc , $error);
		}
		elsif (ref($error) and ($error->[0] eq 'mount failed')) {
			Log->error("mount error ", @desc , $error);
		}
		else {
			Log->fatal('unhandled error ', @desc , \$error);
			die "there was an unhandled error, please fix!\n" . Dumper $error;
		}
		return ();
	};
}

#$id -> $spot
#returns the last spot
sub get_last_spot {
	my ($id) = @_;
	my $col = Collection->get($id);
	my $last = $col->last();
	my $spot;
	if ($last) {
		$spot = $col->fetch($last)->create_spot();
		$spot = try {
			$spot->mount();
		} &catch(std_handler('last ', $spot->id));
	}
	else {
		$spot = fetch_store(Cores::first($id),$col);
	}
	return $spot;
}


#$element, $spot, $fail
#mounts the spot and checks if the elements match
#calls $fail on missmatch
sub check_element {
	my ($elem,$spot,$fail) = @_;
	return () unless $elem and $spot;
	Log->trace('check ', $elem->position(), ' of ', $elem->cid());
	$spot->mount();
	my $relem = $spot->element();
	if (my $attr = $elem->differs($relem)) {
		Log->debug($attr, ' missmatch ', $elem->position(), ' ', $elem->cid());
		$fail->() if $fail;
		return 0;
	}
	else {
		return 1
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
	my $r_first = Cores::first($id);
	unless ($first_elem and $r_first) {
		Log->warn("first not found");
		$col->purge();
		return 0;
	}
	check_element($first_elem,$r_first, sub { $col->purge() });
}

#$id -> $bool
#checks the given collection
sub check_last {
	my ($next_check) = @_;
	my $col = Collection->get($next_check);

	my $last_pos = $col->last();
	unless ($last_pos > 0) {
		Log->error('has no elements', $next_check);
		$col->purge();
		return -1;
	}
	Log->trace("check last ($last_pos) of $next_check");
	my $last_elem = $col->fetch($last_pos);
	my $r_last = $last_elem->create_spot();
	return try {
		check_element($last_elem,$r_last, sub { $col->delete($last_pos) })
		? $r_last : 0;
	} catch {
		my $error = $_;
		die $error if (is_temporary($error)); #temporary errors are handled further up
		Log->error('page of last no longer parses ', $next_check, $error);
		$col->delete($last_pos);
		return 0;
	};
}

1;
