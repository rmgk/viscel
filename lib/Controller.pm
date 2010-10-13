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
use Maintenance;

my $l = Log->new();
my $HS; #hint state, chaches the current read spot for efficency
my $maintainer;
my $default_timeout;

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
	my $timeout = $default_timeout = $main::IDLE;
	$maintainer = Maintenance->new();
	while (1) {
		my $hint = Server::handle_connections($timeout);
		if ($hint) { #hint is undef if no connection was accepted
			handle_hint($hint);
			$timeout = $default_timeout; #resetting timeout
			$maintainer->reset();
		}
		else {
			if ($maintainer->tick()) {
				$timeout = 0; #instant timeout to get some work done
			}
			else {
				$default_timeout = $timeout = 1000000; #deactivating timeout
			}
		}
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
				when ('config') {$maintainer = Maintenance->new(); $default_timeout = $main::IDLE} #config changed, maintain anew
				when ('getrec') {hint_getrec(@$hint)}
				when ('export') {hint_export(@$hint)}
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
		$col->clean();
	};
	$HS = $spot;
	return 1;
}

#$addr
#requests the recommendations list from $addr and adds the collections to recommended
sub hint_getrec {
	my ($addr) = @_;
	$l->debug('getting recommendations');
	return unless $addr;
	my $res = DlUtil::get(URI->new_abs('/rec',$addr));
	if (!$res->is_success()) {
		return undef;
	}
	my $list = $res->decoded_content();
	my @rec = grep {Cores::new($_)} split ("\n",$list);
	$l->debug('adding ' . @rec . ' collections to recommended list');
	my $r = UserPrefs->section('recommended');
	$r->set($_,1) for @rec;
}

#$collection
#export collection to file
sub hint_export {
	my ($id) = @_;
	$l->debug('exporting ', $id);
	my $col = Collection->get($id);
	my $last = $col->last();
	my $dir = $main::EXPORT . $id . '/';
	if ($last) {
		mkdir ($main::EXPORT) unless -e $main::EXPORT ;
		mkdir ($dir) unless -e $dir;
		open (my $lfh, '>:encoding(UTF-8)', $dir . 'urls.txt');
		for (1..$last) {
			my $elem = $col->fetch($_);
			next unless $elem and $elem->sha1;
			print $lfh "$_=" .$elem->src() . "\n";
			my $blob = Cache::get($elem->sha1);
			my $ft;
			given ($elem->type) {
				when (/bmp/i) {$ft = '.bmp'}
				when (/jpe?g/i) {$ft = '.jpg'}
				when (/gif/i) {$ft = '.gif'}
				when (/png/i) {$ft = '.png'}
				when (/shockwave/i) {$ft = '.swf'}
				default {$ft = $_; $ft =~ s'^.*/''}
			}
			open (my $FH, '>', $dir . $_ . $ft);
			binmode $FH;
			print $FH $$blob;
			close $FH;
		}
		close $lfh;
	}
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
