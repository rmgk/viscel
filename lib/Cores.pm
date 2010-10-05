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
use Log;

my $l = Log->new();
my %cores = (	'Core::AnyManga' => 0,
				'Core::Comcol' => 0,
				'Core::ComicGenesis' => 0,
				'Core::Fakku' => 0,
				);

#initialises all used cores
sub init {
	my @cores = @_;
	@cores = keys %cores unless @cores;
	$l->trace('initialising cores: ' . join(', ',@cores));
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
	return get_from_id($id)->about($id);
}

#$id -> $name;
#returns the name of the given id
sub name {
	my ($id) = @_;
	$l->trace("request name of $id");
	return get_from_id($id)->name($id);
}

#$id -> $core
#returns the core for the given id
sub get_from_id {
	my  ($id) = @_;
	$id =~ s/_.*$//;
	my $core = "Core::$id";
	unless ( $cores{$core} ) {
		$l->error("$core is not initialised");
		return undef;
	}
	return $core;
}

#$query -> %collections 
sub search {
	$l->debug('searching for ',join ' ',@_);
	my @re = map {qr/$_/i} @_;
	return map {$_->search(@re)} initialised(); 
}

#pkg -> \%config
#given a package returns the configuration
sub get_config {
	return UserPrefs::parse_file('Cores')->{$_[0] || caller} || {};
}

#pkg -> \%configuration
#given a package returns the configuration hash
sub config {
	my $pkg = shift;
	unless (list($pkg)) {
		$l->warn("cant config unknown core ", $pkg)
	}
	my $cfg = get_config($pkg); 
	$cfg->{$pkg} ||= {};
	while (my ($k,$v) = splice(@_,0,2)) {
		$cfg->{$pkg}->{$k} = $v;
	}
	UserPrefs::save_file('Cores',$cfg);
	return $pkg->config($cfg->{$pkg});
}

1;
