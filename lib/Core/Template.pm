#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Template v1.2.0;

use 5.012;
use warnings;

use Log;
use DBI;
use Element;
use HTML::Entities;
use DlUtil;
use HTML::TreeBuilder;
use Digest::SHA;
use Data::Dumper;
use Time::HiRes qw(tv_interval gettimeofday);
use Spot::Template;

#the somewhat shady container for all the collections data
sub clist {
	my ($pkg,$id) = @_;
	no strict 'refs';
	if (ref $pkg) {
		$id = $pkg->{id};
		$pkg = ref $pkg;	
	} 
	if (ref $id) {
		$$pkg{$_} = $$id{$_} for keys %$id; 
	}
	elsif (defined $id) {
		#return the reference to the collection
		return $$pkg{$id} ||= {};
	}
	else {
		return keys %$pkg;
	}
}

#saves the list
sub save_clist {
	my ($pkg) = @_;
	$pkg = ref($pkg) || $pkg;
	Log->trace("save clist ", $pkg);
	no strict 'refs';
	return ConfigINI::save_file(Globals::datadir,$pkg,\%$pkg);
}

#initialises the core and loads collection list
sub init {
	my ($pkg) = @_;
	Log->trace('initialise ',$pkg);
	Log->warn('list already initialised, reinitialise') if $pkg->clist();
	return $pkg->_load_list();
}

#tries to load the collection list from file, creates it if it cant be found
sub _load_list {
	my ($pkg) = @_;
	$pkg->clist(ConfigINI::parse_file(Globals::datadir,$pkg));
	if ($pkg->clist()) {
		Log->debug('loaded ' . scalar($pkg->clist()) . ' collections');
		return 1;
	}
	return 1;
	#return $pkg->update_list();
}

#$state -> $next_state
#updates and saves the collection list
#as loading lists can take a long time
#this method returns a next state to split list generating
sub update_list {
	my ($pkg,$state) = @_;
	my $list;
	($list,$state) = $pkg->_create_list($state);
	$pkg->clist($list);
	Log->debug($pkg . ' now has ' .  scalar($pkg->clist()) . ' collections');
	return unless $pkg->save_clist();
	return $state;
}

#->%collection_hash
#returns a hash containing all the collection ids as keys and their names as values
sub list {
	my ($p) = @_;
	return map {$_ , $p->clist($_)->{name}} $p->clist();
}

#->@collection_id_list
#returns a list containing all the ids of collections that need updating
sub list_need_info {
	my ($p) = @_;
	return () unless $p->can('_fetch_info');
	return grep {!$p->clist($_)->{moreinfo}} $p->clist();
}

#$id -> $is_known
#returns true if the $id is a known remote
sub known {
	my ($pkg,$id) = @_;
	return 0 + keys( %{$pkg->clist($id)});
}

#$query,@regex -> %list
sub search {
	my ($p,$filter,@re) = @_;
	Log->debug('search ', $p );
	my %cap;
	my @return;
	my $time = [gettimeofday];
	col: for my $id ($p->clist()) {
		my $l = $p->clist($id);
		my $sid = $id;
		$sid =~ s/^[^_]*+_//;
		reg: for my $re (@re) { #all regexes should match
			if ($sid ~~ $re) {
				$cap{$id} = $1;
				next reg;
			}
			for my $k (@$filter ? @$filter : $p->_searchkeys()) {
				next unless defined $l->{$k};
				if ($l->{$k} ~~ $re) {
					$cap{$id} = $1;
					next reg;
				} 
			}
			#not all regexes matched, checking next collection
			next col;
		}
		#we have a matched
		push @return, [$id,$l->{name},$cap{$id}//$l->{name}]; #/ padre display bug
	}
	Log->trace('took ', tv_interval($time), ' seconds');
	return @return;
}

#$class,$id -> $remote
#creates a new remote for a given id
sub new {
	my ($class,$id) = @_;
	Log->trace("create new remote $id");
	my $self = bless {id => $id}, $class;
	unless (keys %{$self->clist()}) {
		Log->error("unknown id ", $id);
		return;
	}
	return $self;
}

#-> @info
#returns a list of infos
sub about {
	my ($s) = @_;
	#all lowercased attributes are not intended for user
	return ['Name',$s->clist->{name}], map {[$_, $s->clist()->{$_}]} grep {$_ eq ucfirst $_} keys %{$s->clist()};
}

#$force
#fetches more information about the comic, force overwrites existing info
sub fetch_info {
	my ($s,$force) = @_;
	return 1 if $s->clist()->{moreinfo} and !$force;
	if ($s->can('_fetch_info')) {
		Log->trace('fetching more info for ', $s->{id});
		local $@;
		unless (eval { $s->_fetch_info() }) {
			Log->error("crash on parse info: " . $@);
			return;
		}
	}
	$s->clist()->{moreinfo} = 1;
	return $s->save_clist();
}

#$self -> $name
sub name {
	return $_[0]->clist()->{name};
}

#-> \%spot
#returns the first spot
sub first {
	my ($s) = @_;
	Log->trace('creat first ',$s->{id});
	return $s->create(1,$s->clist()->{url_start});
}

#$class, $position, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($s,$pos,$state) = @_;
	my $class = ref($s);
	$class =~ s/Core/Spot/;
	my $spot = {id => $s->{id}, position => $pos, state => $state};
	Log->debug('create new spot ' , $class, ' id: ', $s->{id}, ,' position: ', $pos);
	$class->new($spot);
	return $spot;
}

1;
