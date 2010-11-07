#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Stats v1.0.0;

use 5.012;
use warnings;

use Time::HiRes;

my $DBH;
my $STH_put;

#initialises the cache
sub init {
	$DBH = DBI->connect("dbi:SQLite:dbname=".$main::DIRDATA.'stats.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return undef unless $DBH;
	unless ($DBH->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,"statistics")) {
		unless($DBH->do('CREATE TABLE statistics (time REAL, event CHAR, value)')) {
			#$l->error('could not create table statistics');
			return undef;
		}
		$DBH->commit();
	}
	$STH_put = $DBH->prepare('INSERT OR REPLACE INTO statistics (time,event,value) VALUES (?,?,?)');
	return 1;
}

#$event, $value
#saves statistics for $event with $value
sub add {
	my ($event,$value) = @_;
	my $time = Time::HiRes::time();
	$STH_put->execute($time,$event,$value);
	$DBH->commit();
	return 1;
}

1;
