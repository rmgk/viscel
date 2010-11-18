#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cores v1.1.0;

use 5.012;
use warnings;

use Core::AnyManga;
use Core::Comcol;
use Core::ComicGenesis;
use Core::Fakku;
use Core::Animea;
use Core::Universal;
use Core::Cartooniverse;
use Core::Homeunix;
use Core::KatBox;
use Core::Mangafox;
use Log;

my $l = Log->new();
my %cores = (	'Core::AnyManga' => 0,
				#'Core::Comcol' => 0,
				'Core::ComicGenesis' => 0,
				'Core::Fakku' => 0,
				'Core::Animea' => 0,
				'Core::Universal' => 0,
				'Core::Cartooniverse' => 0,
				'Core::Homeunix' => 0,
				'Core::KatBox' => 0,
				'Core::Mangafox' => 0,
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
	my $remote = new($id);
	return undef unless $remote;
	$remote->about();
}

#$id -> $name;
#returns the name of the given id
sub name {
	my ($id) = @_;
	$l->trace("request name of $id");
	my $remote = new($id);
	return undef unless $remote;
	return $remote->name();
}

#$id -> \%spot
#returns the first spot of the given id
sub first {
	my ($id) = @_;
	$l->trace("request first of $id");
	my $remote = new($id);
	return undef unless $remote;
	$remote->fetch_info() or return undef;
	return $remote->first();
}

#$id -> $remote
#returns the remote for the given id
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

1;
