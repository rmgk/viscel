#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Collection v1.0.0;

use 5.012;
use warnings;
use lib "..";

use Log;
use DBI;
use Element;
use Cache;

my $l = Log->new();
my $DBH;
my $cache;
my $cache_id = '';

#initialises the database
sub init {
	my $db_dir = $main::DIRDATA.'collections.db';
	$l->trace('initialise database');
	$l->warn('already initialised, reinitialise') if $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=$db_dir","","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return 1 if $DBH;
	$l->error('could not connect to database');
	return undef;
}

#-> @collections
#returns a list of all stored collections
sub list {
	return @{$DBH->selectcol_arrayref("SELECT name FROM sqlite_master WHERE type='table'")};
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
		unless($DBH->do('CREATE TABLE ' . $self->{id} . ' (' .Element::create_table_column_string(). ')')) {
			$l->error('could not create table '. $self->{id});
			return undef;
		}
		$DBH->commit();
	}
	bless $self, $class;
	return $self;
}

#removes the collection
sub purge {
	my ($s) = @_;
	$l->warn('drop table ', $s->{id});
	$DBH->do("DROP TABLE ".$s->{id});
	$DBH->commit();
	$cache = undef;
	$cache_id = undef;
}

#$position
sub delete {
	my ($s,$pos) = @_;
	$l->warn("delete $pos form " , $s->{id});
	$DBH->do("DELETE FROM ". $s->{id} ." WHERE position = ?",undef,$pos);
	$DBH->commit();
}

#\%element -> $bool
#stores the given element returns false if storing has failed
sub store {
	my ($s, $ent, $blob) = @_;
	$l->trace('store '. $ent->cid);
	if ($s->{id} ne $ent->cid) {
		$l->error('can not store element with mismatching id');
		return undef;
	}
	my @values = $ent->attribute_values_array();
	unless($DBH->do('INSERT OR FAIL INTO '. $s->{id} . ' ('.Element::attribute_list_string().') VALUES ('.(join ',',map {'?'} @values).')',undef,@values)) {
		$l->error('could not insert into table: ' . $DBH->errstr);
		return undef;
	}
	if (defined $blob) {
		Cache::stat($ent->sha1,$ent->type);
		return Cache::put($ent->sha1,$blob);
	}
	return 1;
}

#$pos -> \%element
#retrieves the element at position pos
sub fetch {
	my ($s, $pos) = @_;
	$l->trace('fetch '. $s->{id} .' '. $pos);
	my $ret;
	unless (defined ($ret = $DBH->selectrow_hashref('SELECT '.Element::attribute_list_string().' FROM ' . $s->{id} . ' WHERE position = ?',undef, $pos))) {
		$l->warn('could not retrieve element '.$DBH->errstr) if $DBH->err;
		return undef;
	}
	$ret->{cid} = $s->{id};
	$ret = Element->new($ret);
	return $ret;
}

#->$core
#returns the core of the given id
sub core {
	my ($s) = @_;
	return Cores::get_from_id($s->{id});
}

#->$pos
#returns the last position
sub last {
	my ($s) = @_;
	$l->trace('last '. $s->{id});
	if (my $pos = $DBH->selectrow_array('SELECT MAX(position) FROM ' . $s->{id})) {
		return $pos;
	}
	$l->warn('could not retrieve position ' , $DBH->errstr);
	return undef;
}

#commits the database changes
sub clean {
	my ($s) = @_;
	$DBH->commit;
}

1;
