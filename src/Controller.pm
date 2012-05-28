#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Controller v1.3.0;

use 5.012;
use warnings;
use utf8;

use Log;
use Server;
use Cores;
use Cache;
use Collection;
use Handler;
use Handler::Misc;
use UserPrefs;
use Maintenance;
use Data::Dumper;
use Try::Tiny;

my $HS; #hint state, caches the current read spot
my @hint;

our $TERM = 0;
our $INTS = sub {
	print "\nTerminating\n\n" ;
	$TERM = 1;
};
our $INTF = $SIG{'INT'};
$SIG{'INT'} = $INTS;


#initialises the needed modules
sub init {
	Log->trace('initialise modules');
	if (
		UserPrefs::init(Globals::userdir(),'user') &&
		Cache::init(Globals::cachedir()) &&
		Collection::init(Globals::datadir() . 'collections.db') &&
		Server::init() &&
		Handler::init() &&
		Handler::Misc::init() &&
		Cores::init()
	) {
		return 1;
	}
	Log->error('failed to initialise modules');
	return;
}


#starts the program, never returns
sub start {
	Log->trace('run main loop');
	my $timeout = 0;

	my $accept = sub {
		my ($longwait) = @_;
		die 'terminate' if $TERM;
		Core::Universal->_load_list() if Globals::updateuniversal();
		$SIG{'INT'} = $INTF;
		if (Server::accept($timeout,0.5)) { #some connection was accepted
			$SIG{'INT'} = $INTS;
			handle_hints();
			$timeout = 60; #resetting timeout
			return 1;
		}
		else {
			$SIG{'INT'} = $INTS;
			if ($longwait) {
				$timeout = 3600; #one hour timeout

			}
			else {
				$timeout = 0; #instant timeout to get some work done
			}
			return 0;
		}
	};
	my $maintainer = Maintenance->new($accept);
	try {
		$maintainer->maintain();
		while (!$TERM) {
			$accept->(1);
		}
	} catch {
		when(/^terminate /) { };
		default { die $_ };
	}
}

#@hints
#adds hints to be handled
sub add_hint {
	push(@hint,@_);
}

#\@hint
#handles hints from server
sub handle_hints {
	while(@hint) {
		my $hint = pop(@hint); #more recent hints are more interesting
		if (ref $hint eq 'CODE') { #this gives handlers great flexibility for hints
			Log->trace('run code hint');
			$hint->();
		}
		if (ref $hint eq 'ARRAY') {
			given (shift @$hint) {
				when ('front') {hint_front(@$hint)}
				when ('view') {hint_view(@$hint)}
				#when ('getall') {hint_getall(@$hint)}
				when ('config') {}
				#when ('export') {hint_export(@$hint)}
				#when ('check') { until (Maintenance::check_collection(@$hint)) {} }
				default {Log->warn("unknown hint $_")}
			}
		}
	}
}

#$id
#handles front hints
sub hint_front {
	my ($id) = @_;
	Log->trace('handle front hint '.$id);
	my $remote = Cores::new($id);
	unless ($remote) {
		Log->trace('unknown collection ', $id);
		return;
	}
	if ($remote->want_info()) {
		return unless try {
			$remote->clist($remote->fetch_info());
			$remote->save_clist();
			return 1;
		} catch {
			Log->error("error fetch info",[$_]);
			return 0 ;
		};
	}
	my $col = Collection->get($id);
	return if $col->fetch(1);
	my $spot = Cores::first($id);
	try {
		_store($col,$spot);
		$HS = $spot;
	} catch {
			Log->error("error store first",[$_]);
	};
	return 1;
}

#$id,$pos
#handles viewer hints
sub hint_view {
	my ($id,@pos) = @_;
	return unless Cores::known($id);
	Log->trace("handle view hint $id ", join(' ',@pos) );
	my $col = Collection->get($id);
	@pos = sort @pos;
	for my $pos ($pos[0]..($pos[-1] + $#pos)) {
		return next if $col->fetch($pos + 1);
		Log->debug("try to get $id $pos");
		my $spot = $HS;
		unless (defined $spot and $spot->id eq $id and $spot->position == $pos) {
			my $ent = $col->fetch($pos);
			if ($ent) {
				$spot = $ent->create_spot();
				return unless try {
					$spot->mount();
					return 1;
				} catch {
					Log->error("error mount this", [$_]);
					return 0;
				};
			}
			else {
				Log->debug('could not get spot');
				return;
			}
		}
		try {
			$spot = $spot->next();
			return unless $spot;
			_store($col,$spot);
			$HS = $spot;
		} catch {
			Log->error("error fetch and store next",[$_]);
		};
	}
	return 1;
}

##$id
##handles getall hints
#sub hint_getall {
#	my ($id) = @_;
#	Log->trace("handle getall hint $id");
#	my $col = Collection->get($id);
#	Log->debug("get last collected");
#	my $last = $col->last();
#	return unless ($last);
#	my $spot = $col->fetch($last)->create_spot();
#	return unless $spot;
#	$spot->mount();
#	while ($spot = $spot->next()) {
#		return unless _store($col,$spot);
#		$col->clean();
#	};
#	$HS = $spot;
#	return 1;
#}

##$collection
##export collection to file
#sub hint_export {
#	my ($id) = @_;
#	Log->debug('exporting ', $id);
#	my $col = Collection->get($id);
#	my $last = $col->last();
#	my $dir = $main::EXPORT . $id . '/';
#	if ($last) {
#		mkdir ($main::EXPORT) unless -e $main::EXPORT ;
#		mkdir ($dir) unless -e $dir;
#		open (my $lfh, '>:encoding(UTF-8)', $dir . 'urls.txt');
#		for (1..$last) {
#			my $elem = $col->fetch($_);
#			next unless $elem and $elem->sha1;
#			print $lfh "$_=" .$elem->page_url() . "\n";
#			my $blob = Cache::get($elem->sha1);
#			my $ft;
#			given ($elem->type) {
#				when (/bmp/i) {$ft = '.bmp'}
#				when (/jpe?g/i) {$ft = '.jpg'}
#				when (/gif/i) {$ft = '.gif'}
#				when (/png/i) {$ft = '.png'}
#				when (/shockwave/i) {$ft = '.swf'}
#				default {$ft = $_; $ft =~ s'^.*/''}
#			}
#			open (my $FH, '>', $dir . $_ . $ft);
#			binmode $FH;
#			print $FH $$blob;
#			close $FH;
#		}
#		close $lfh;
#	}
#}

#$col,$spot
#stores the element of the spot into the collection
sub _store {
	my ($col,$spot) = @_;
	return unless $spot;
	$spot->mount();
	my $blob = $spot->fetch();
	my $elem = $spot->element();
	if ($elem and $blob) {
		return $col->store($elem,$blob);
	}
	return ();
}

#trims the collections
sub trim_collections {
	my $dry = shift;
	my %bm;
	$bm{$_} = 1 for UserPrefs->section('bookmark')->list();
	Log->info('has ', 0+keys %bm , ' bookmarks');
	my @collections = Collection->list();
	Log->info('has ' . (0+@collections) . ' collections');
	my @to_purge = grep {! exists $bm{$_} } @collections;
	Log->info('purge ' , 0+@to_purge , ' collections');
	unless ($dry) {
		Collection->get($_)->purge() for @to_purge;
	}
}

#trims the cache
sub trim_cache {
	my $dry = shift;
	my @collections = Collection->list();
	Log->info('has ' . (0+@collections) . ' collections');
	my %hashes;
	foreach my $id (@collections) {
		my $col = Collection->get($id);
		my $last = $col->last();
		Log->info($id , ' has ', $last, ' elements');
		for my $i (1..$last) {
			my $elem = $col->fetch($i);
			unless ($elem) {
				Log->error($id, ' is missing position ', $i);
				next;
			}
			$hashes{$elem->sha1} = 1;
		}
	}
	Log->info('found ' , 0+keys %hashes, ' hashes');
	my @inCache = Cache::list();
	Log->info('Cache has ' , 0+@inCache, ' elemenst');
	my @toRemove = grep { ! exists $hashes{$_} } @inCache;
	Log->info('remove ' , 0+@toRemove , ' elements');
	if(!$dry) {
		Cache::remove($_) for @toRemove;
	}
}

1;
