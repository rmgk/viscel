#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cores;

use 5.012;
use warnings;

our $VERSION = v1;

use Core::AnyManga;
use Core::Comcol;
use Core::ComicGenesis;
use Core::Fakku;
use Core::Animea;
use Core::Universal;
use Core::Cartooniverse;
use Core::Homeunix;
use Log;

my $l = Log->new();
my %cores = (	'Core::AnyManga' => 0,
				'Core::Comcol' => 0,
				'Core::ComicGenesis' => 0,
				'Core::Fakku' => 0,
				'Core::Animea' => 0,
				'Core::Universal' => 0,
				'Core::Cartooniverse' => 0,
				'Core::Homeunix' => 0,
				);

#initialises all used cores
sub init {
	my @cores = @_;
	@cores = keys %cores unless @cores;
	$l->trace('initialise cores: ' . join(', ',@cores));
	$cores{$_} = $_->init() for grep { exists $cores{$_} and !$cores{$_}} @cores;
	return 1;
}

#->@cores
#returns the list of available cores
sub list {
	return exists $cores{$_[0]} if $_[0];
	$l->trace('core list requested');
	return keys %cores;
}

#$core->@cores
#returns the list of initialised cores or the state of one core
sub initialised {
	my $core = shift;
	return $cores{$core} if defined $core;
	return grep {$cores{$_} } keys %cores;
}

#$id -> @info
#returns a list (hash) of infos about the given id
sub about {
	my ($id) = @_;
	$l->trace("about $id");
	my $c = new($id);
	return undef unless $c;
	$c->about();
}

#$id -> $name;
#returns the name of the given id
sub name {
	my ($id) = @_;
	$l->trace("request name of $id");
	my $c = new($id);
	return undef unless $c;
	return $c->name();
}

#$id -> \%self
#returns the first spot of the given id
sub first {
	my ($id) = @_;
	$l->trace("request first of $id");
	my $c = new($id);
	return undef unless $c;
	$c->fetch_info();
	return $c->first();
}

#$id -> $core
#returns the core for the given id
sub new {
	my  ($id) = @_;
	my $core = $id;
	$core =~ s/_.*$//;
	$core = "Core::$core";
	unless ( $cores{$core} ) {
		$l->error("$core is not initialised");
		return undef;
	}
	return $core->new($id);
}

#$query -> %collections 
sub search {
	$l->debug('search for ',join ' ',@_);
	my @cores;
	my @filter;
	while (defined $_[0] and $_[0] =~ m/^(.*):$/) {
		my $f = $1;
		if ($f =~ m/^Core::\w+$/) {
			push(@cores,$f);
		}
		else {
			push(@filter,$f);
		}
		shift;
	}
	my @re = map {qr/$_/i} @_;
	return map {$_->search(\@filter, @re)} @cores ? grep {initialised($_)} @cores : initialised(); 
}

#pkg -> \%configuration
#given a package returns the configuration hash
sub config {
	my $pkg = shift;
	$l->trace("configure $pkg");
	unless (list($pkg)) {
		return _config_collection($pkg,@_);
	}
	my $cfg = UserPrefs::parse_file('Cores');
	$cfg->{$pkg} ||= {};
	while (my ($k,$v) = splice(@_,0,2)) {
		$cfg->{$pkg}->{$k} = $v;
	}
	UserPrefs::save_file('Cores',$cfg);
	my $ret = $pkg->config($cfg->{$pkg});
	$ret->{updatelist} ||= { name => 'update list',
							action =>'updatelist',
							description => 'updates the list of known collections'};
	return $ret;
}

#id, %config -> \%config
#given an id and hash configures the id and returns the string to create a configuration form
sub _config_collection {
	my $id = shift;
	while (my ($k,$v) = splice(@_,0,2)) {
		UserPrefs->section($k)->set($id,$v);
	}
	
	return { bookmark => {	current => UserPrefs::get('bookmark',$id),
						default => 0,
						expected => qr/^\d+$/,
						description => 'the position of the bookmarked element' 
					} ,
			 keep_current => { current => UserPrefs::get('keep_current',$id),
						default => 0,
						expected => 'bool',
						description => 'if true updates the collection when idle'
					},
			 recommend => { current => UserPrefs::get('recommend',$id),
							default => 0,
							expected => 'bool',
							description => 'recommend this collection'
						},
			 recommended => { current => UserPrefs::get('recommended',$id),
							default => 0,
							expected => 'bool',
							description => 'this collection is recommended'
						},
			 getall => { name => 'get all',
						 action => 'getall',
						 description => 'downloads until no next is found'
						}
			}
	
}

1;
