#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Template;

use 5.012;
use warnings;
use lib "..";
our $VERSION = v1;


use Log;
use DBI;
use Entity;
use HTML::Entities;
use DlUtil;
use HTML::TreeBuilder;
use Digest::SHA;
use Data::Dumper;

my $l = Log->new();
my $SHA = Digest::SHA->new();

#the somewhat shady container for all the collections data
sub clist {
	my ($pkg,$id) = @_;
	no strict 'refs';
	if (ref $pkg) {
		$id = $pkg->{id};
		$pkg = ref $pkg;	
	} 
	if (ref $id) {
		%$pkg = %$id; 
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
	$l->trace("save clist ", $pkg);
	no strict 'refs';
	return UserPrefs::save_file($pkg,\%$pkg);
}

#initialises the database connection
sub init {
	my ($pkg) = @_;
	$l->trace('initialise ',$pkg);
	$l->warn('list already initialised, reinitialise') if $pkg->clist();
	$pkg->clist(UserPrefs::parse_file($pkg));
	if ($pkg->clist()) {
		$l->debug('loaded ' . scalar($pkg->clist()) . ' collections');
		return 1;
	}
	my $list = $pkg->_create_list();
	$pkg->clist($list);
	$l->debug('found ' .  scalar($pkg->clist()) . ' collections');
	$pkg->save_clist();
}

#->%collection_hash
#returns a hash containing all the collection ids as keys and their names as values
sub list {
	my ($p) = @_;
	return map {$_ , $p->clist($_)->{name}} $p->clist();
}

#$query,@regex -> %list
sub search {
	my ($p,@re) = @_;
	$l->debug('search ', $p );
	return map {$_,$p->clist($_)->{name}} grep {
		my $id = $_;
		my $l = $p->clist($id);
		$id =~ s/^[^_]*+_//;
		@re == grep {
			my $re = $_;
			$id ~~ $re or grep {
				(defined $l->{$_} and $l->{$_} ~~ $re);
			} $p->_searchkeys();
		} @re;
	} $p->clist();
}

#pkg, \%config -> \%config
#given a current config returns the configuration hash
sub config {
	my ($pkg,$cfg) = @_;
	return {};
}

#$class,$id -> $self
#creates a new core instance for a given collection
sub new {
	my ($class,$id) = @_;
	$l->trace("create new core $id");
	my $self = bless {id => $id}, $class;
	unless (keys %{$self->clist()}) {
		$l->error("unknown id ", $id);
		return undef;
	}
	return $self;
}

#-> @info
#returns a list of infos
sub about {
	my ($s) = @_;
	return map {"$_: " . $s->clist()->{$_}} keys %{$s->clist()};
}

#noop
sub fetch_info {}

#$self -> $name
sub name {
	return $_[0]->clist()->{name};
}

#-> \%spot
#returns the first spot
sub first {
	my ($s) = @_;
	$l->trace('creat first ',$s->{id});
	return $s->create(1,$s->clist()->{url_start});
}

#$class, $id, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($s,$pos,$state) = @_;
	my $class = ref($s) . '::Spot';
	my $spot = {id => $s->{id}, position => $pos, state => $state};
	$l->debug('creat new core ' , $class, ' id: ', $s->{id}, ,' position: ', $pos);
	$class->new($spot);
	return $spot;
}

1;
