#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Collection::Ordered;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Entity;
use Cache;

my $l = Log->new();
our $DB_DIR = 'collections.db';
my $DBH;
my $cache;
my $cache_id = '';

#initialises the database
sub init {
	$l->trace('initialising database');
	$l->warn('already initialised, reinitialising') if $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=$DB_DIR","","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return 1 if $DBH;
	$l->error('could not connect to database');
	return undef;
}

#$class,$id->$self
sub get {
	my ($class,$id) = @_;
	$l->trace("get collection of $id");
	unless ($cache_id eq $id) {
		$cache = $class->new({id => $id});
		$cache_id = $id;
	}
	return $cache;
}

#$class,$self -> $self
#instanciates an ordered collection
sub new {
	my ($class,$self) = @_;
	unless($self and $self->{id}) {
		$l->error('could not create new collection: id not specified');
		return undef;
	}
	$l->trace('new ordered collection: ' . $self->{id});
	unless ($DBH->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,$self->{id})) {
		unless($DBH->do('CREATE TABLE ' . $self->{id} . ' (' .Entity::create_table_column_string(). ')')) {
			$l->error('could not create table '. $self->{id});
			return undef;
		}
		$DBH->commit();
	}
	bless $self, $class;
	return $self;
}

#\%entity -> $bool
#stores the given entity returns false if storing has failed
sub store {
	my ($s, $ent) = @_;
	$l->trace('store '. $ent->cid);
	if ($s->{id} ne $ent->cid) {
		$l->error('can not store entity with mismatching id');
		return undef;
	}
	my @values = $ent->attribute_values_array();
	unless($DBH->do('INSERT OR FAIL INTO '. $s->{id} . ' ('.Entity::attribute_list_string().') VALUES ('.(join ',',map {'?'} @values).')',undef,@values)) {
		$l->error('could not insert into table: ' . $s->{dbh}->errstr);
		return undef;
	}
	my $blob = \$ent->{blob}; 
	unless (defined $blob) {
		$l->error('blob not defined');
		return undef;
	}
	return Cache::put($ent->sha1,$blob);
}

#$pos -> \%entity
#retrieves the entity at position pos
sub fetch {
	my ($s, $pos) = @_;
	$l->trace('fetch '. $s->{id} .' '. $pos);
	my $ret;
	unless (defined ($ret = $DBH->selectrow_hashref('SELECT '.Entity::attribute_list_string().' FROM ' . $s->{id} . ' WHERE position = ?',undef, $pos))) {
		$l->warn('could not retrieve entity '.$DBH->errstr) if $DBH->err;
		return undef;
	}
	$ret->{cid} = $s->{id};
	$ret = Entity->new($ret);
	return $ret;
}

#->$core
#returns the core of the given id
sub core {
	my ($s) = @_;
	my $id = $s->{id};
	$id =~ s/_.*$//;
	return "Core::$id";
}

#->$pos
#returns the last position
sub last {
	my ($s) = @_;
	$l->trace('last '. $s->{id});
	if (my $pos = $DBH->selectrow_array('SELECT MAX(position) FROM ' . $s->{id})) {
		return $pos;
	}
	$l->warn('could not retrieve position ' .$DBH->errstr);
	return undef;
}

#commits the database changes
sub clean {
	my ($s) = @_;
	$DBH->commit;
}

1;
