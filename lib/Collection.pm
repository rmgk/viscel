#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Collection v1.3.0;

use 5.012;
use warnings;

use Log;
use DBI;
use Element;
use Cache;

my $DBH;
my $cache;
my $cache_id = '';

#initialises the database
sub init {
	my $db_dir = shift // Globals::datadir() . 'collections.db';
	Log->trace('initialise database');
	Log->warn('already initialised, reinitialise') if $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=$db_dir","","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return 1 if $DBH;
	Log->error('could not connect to database');
	return;
}

#disconnects the database
sub deinit {
	$DBH->disconnect();
}

#-> @collections
#returns a list of all stored collections
sub list {
	return @{$DBH->selectcol_arrayref("SELECT name FROM sqlite_master WHERE type='table'")};
}

#$class,$id->$self
sub get {
	my ($class,$id) = @_;
	Log->trace("get collection of $id");
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
		Log->error('could not create new collection: id not specified');
		return;
	}
	Log->trace('new ordered collection: ' . $self->{id});
	unless ($DBH->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,$self->{id})) {
		unless($DBH->do('CREATE TABLE ' . $self->{id} . ' (' .Element::create_table_column_string(). ')')) {
			Log->error('could not create table '. $self->{id});
			return;
		}
		$DBH->commit();
	}
	bless $self, $class;
	return $self;
}

#removes the collection
sub purge {
	my ($s) = @_;
	Log->warn('drop table ', $s->{id});
	$DBH->do("DROP TABLE ".$s->{id});
	$DBH->commit();
	$cache = undef;
	$cache_id = '';
}

#$position
sub delete {
	my ($s,$pos) = @_;
	Log->warn("delete $pos from " , $s->{id});
	$DBH->do("DELETE FROM ". $s->{id} ." WHERE position = ?",undef,$pos);
	$DBH->commit();
}

#\%element -> $bool
#stores the given element returns false if storing has failed
sub store {
	my ($s, $ent, $blob) = @_;
	Log->trace('store '. $ent->cid);
	if ($s->{id} ne $ent->cid) {
		Log->error('can not store element with mismatching id');
		return;
	}
	my @values = $ent->attribute_values_array();
	unless($DBH->do('INSERT OR FAIL INTO '. $s->{id} . ' ('.Element::attribute_list_string().') VALUES ('.(join ',',map {'?'} @values).')',undef,@values)) {
		Log->error('could not insert into table: ' . $DBH->errstr);
		return;
	}
	if (defined $blob) {
		return Cache::put($ent->sha1,$blob);
	}
	return 1;
}

#$pos -> \%element
#retrieves the element at position pos
sub fetch {
	my ($s, $pos) = @_;
	Log->trace('fetch '. $s->{id} .' '. $pos);
	my $ret;
	unless (defined ($ret = $DBH->selectrow_hashref('SELECT '.Element::attribute_list_string().' FROM ' . $s->{id} . ' WHERE position = ?',undef, $pos))) {
		Log->warn('could not retrieve element '.$DBH->errstr) if $DBH->err;
		return;
	}
	$ret->{cid} = $s->{id};
	$ret = Element->new($ret);
	return $ret;
}

#->$pos
#returns the last position
sub last {
	my ($s) = @_;
	Log->trace('last '. $s->{id});
	if (my $pos = $DBH->selectrow_array('SELECT MAX(position) FROM ' . $s->{id})) {
		return $pos;
	}
	Log->warn('could not retrieve position ' , $DBH->errstr);
	return;
}

#commits the database changes
sub clean {
	my ($s) = @_;
	$DBH->commit;
}

1;
