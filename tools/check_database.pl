use 5.010;
use strict;
use warnings;
use lib "../lib";
use dbutil;
use DBI;

my $comics = dbutil::readINI('../comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});

my $ok = 0;
my $all = 0;
my $full_test = 1;
my @comics = sort keys %$comics;



if (scalar(@ARGV)) {
	@comics = @ARGV;
	$full_test = 0;

}

my %results;

use Time::HiRes;
my $time = Time::HiRes::time;
comic: foreach my $comic (@comics) {
	next if !$comics->{$comic} or $comics->{$comic}->{broken};
	#say $comic;
	$all++;
	$results{$comic}->{result} = 'undef';
	$results{$comic}->{count} = 0;
	
	my ($first,$last) = $dbh->selectrow_array("SELECT first,last FROM comics WHERE comic = ?",undef,$comic);
	if (!$first or !$last) {
		say "$comic: first or last missing";
		next comic;
	}
	my $strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
	my $curr = $first;
	my %seen;
	
	do {
		$results{$comic}->{count}++;
		if ($seen{$curr}) {
			say "$comic: $curr was already seen (circular reference)";
			$results{$comic}->{result} ="seen$curr";
			next comic;
		}
		$seen{$curr} = 1;
		if (!$strips->{$curr}) {
			say "$comic: $curr does not exist";
			$results{$comic}->{result} ="notexist$curr";
			next comic;
		}
		if ($curr == $last) {
			$ok ++;
			$results{$comic}->{result} ='ok';
			next comic;
		}
		if (!$strips->{$curr}->{next}) {
			say "$comic: $curr has no next";
			$results{$comic}->{result} ="notnext$curr";
			next comic;
		}
		else {
			my $next = $strips->{$curr}->{next};
			if ($next == $curr) {
				say "$comic: $curr has itself ($next) as next";
				$results{$comic}->{result} ="selfnext$curr";
				next comic;
			}
			if (!$strips->{$next}->{prev}) {
				say "$comic: next $next has no prev";
				$results{$comic}->{result} ="notprev$next";
				next comic;
			}
			if ($strips->{$next}->{prev} != $curr) {
				say "$comic: next $next does not link back to $curr";
				$results{$comic}->{result} ="next${next}notback$curr";
				next comic;
			}
		}
	} while ($curr = $strips->{$curr}->{next});
}
say "\n";
$dbh->disconnect;
if ($full_test) {
	$dbh = DBI->connect("dbi:SQLite:dbname=check_database.db","","",{AutoCommit => 0,PrintError => 1});
	$dbh->do('CREATE TABLE IF NOT EXISTS results(id INTEGER PRIMARY KEY ASC, count, ok, nok, time)');
	my $last_test = $dbh->selectrow_array('SELECT id FROM results WHERE time = (SELECT MAX(time) from results)');
	$dbh->do('INSERT INTO results (count,ok,nok,time) VALUES (?,?,?,?)',undef,$all,$ok,$all-$ok,time);
	my $curr_test = $dbh->last_insert_id(undef,undef,'results','id');
	$dbh->do("CREATE TABLE test_$curr_test (comic,result,strips_counted)");
	my $sth = $dbh->prepare("INSERT INTO test_$curr_test (comic,result,strips_counted) VALUES (?,?,?)");
	$sth->execute_array(undef,[keys %results],[map {$results{$_}->{result}} keys %results],[map {$results{$_}->{count}} keys %results]);
	if ($last_test) {
		my $progress = 0;
		my $regress = 0;
		my $gress = 0;
		my ($lall,$lok,$lnok) = $dbh->selectrow_array('SELECT count,ok,nok FROM results WHERE id = ?',undef,$last_test);
		my %lres = %{$dbh->selectall_hashref("SELECT * FROM test_$last_test",'comic')};
		my @cmcs = (scalar keys %results)>(scalar keys %lres)?keys %results: keys %lres;
		foreach my $cmc (@cmcs) {
			if (!$results{$cmc}->{result}) {
				say "$cmc was not tested anymore count was: ". $lres{$cmc}->{strips_counted};
				next;
			}
			if (!$lres{$cmc}->{result}) {
				say "$cmc was newly tested count: " . $results{$cmc}->{count};
				next;
			}
			if ($results{$cmc}->{result} eq 'ok' and $lres{$cmc}->{result} eq 'ok') {
				if ($results{$cmc}->{count} < $lres{$cmc}->{strips_counted}) {
					say "$cmc count was reduced from ".$lres{$cmc}->{strips_counted}." to ".$results{$cmc}->{count};
				}
				next; #both ok
			}
			if ($results{$cmc}->{result} eq $lres{$cmc}->{result}) {
				if ($results{$cmc}->{count} < $lres{$cmc}->{strips_counted}) {
					say "$cmc count was reduced from ".$lres{$cmc}->{strips_counted}." to ".$results{$cmc}->{count};
				}
				next; #no change both error
			}
			say "$cmc was '" . $lres{$cmc}->{result} . "' and is now '" . $results{$cmc}->{result},
				"' count changed from " . $lres{$cmc}->{strips_counted}." to ".$results{$cmc}->{count};
			if ($lres{$cmc}->{result} ne 'ok' and $results{$cmc}->{result} eq 'ok') {
				$progress ++;
			}
			elsif ($lres{$cmc}->{result} eq 'ok' and $results{$cmc}->{result} ne 'ok') {
				$regress++;
			}
			else {
				$gress++;
			}
			
		}		
		say '';
		say "lall: $lall lok: $lok lnok: $lnok"; 
		say "progress: $progress  regression: $regress  random changes: $gress";
	}
	$dbh->commit;
	$dbh->disconnect;
}



say "all: $all ok: $ok nok: " . ($all - $ok); 

say Time::HiRes::time - $time;