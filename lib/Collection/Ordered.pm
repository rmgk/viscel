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

#instanciates an ordered collection
sub new {
	my ($class,$self) = @_;
	unless($self and $self->{id}) {
		$l->error('could not create new collection: id not specified');
		return undef;
	}
	$l->trace('new ordered collection: ' . $self->{id});
	$self->{dbh} = DBI->connect("dbi:SQLite:dbname=$DB_DIR","","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	unless ($self->{dbh}->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,$self->{id})) {
		unless($self->{dbh}->do('CREATE TABLE ' . $self->{id} . ' (position INTEGER PRIMARY KEY, sha1 CHAR, filename CHAR, title CHAR, alt CHAR, src CHAR)')) {
			$l->error('could not create table '. $self->{id});
			return undef;
		}
	}
	bless $self, $class;
	return $self;
}

#\%entity -> $bool
#stores the given entity returns false if storing has failed
sub store {
	my ($s, $ent) = @_;
	$l->trace('store '. $ent->{cid});
	if ($s->{id} ne $ent->{cid}) {
		$l->error('can not store entity with different id from self');
		return undef;
	}
	unless($s->{dbh}->do('INSERT OR FAIL INTO '. $s->{id} . ' (position, sha1, filename, title, alt, src) VALUES (?,?,?,?,?,?)',undef,
														$ent->{position}, $ent->{sha1}, $ent->{filename}, $ent->{title}, $ent->{alt}, $ent->{src})) {
		$l->error('could not insert into table: ' . $s->{dbh}->errstr);
		return undef;
	}
	my $blob = \$ent->{blob}; 
	return Cache::put($ent->{sha1},$blob);
}

#commits the database changes
sub clean {
	my ($s) = @_;
	$s->{dbh}->commit;
}

1;
