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
my @comics = scalar(@ARGV) ? @ARGV : sort keys %$comics;
comic: foreach my $comic (@comics) {
	next if !$comics->{$comic} or $comics->{$comic}->{broken};
	#say $comic;
	$all++;
	my ($first,$last) = $dbh->selectrow_array("SELECT first,last FROM comics WHERE comic = ?",undef,$comic);
	if (!$first or !$last) {
		say "$comic: first or last missing";
		next comic;
	}
	my $strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
	my $curr = $first;
	my %seen;
	do {
		if ($seen{$curr}) {
			say "$comic: $curr was already seen (circular reference)";
			next comic;
		}
		$seen{$curr} = 1;
		if (!$strips->{$curr}) {
			say "$comic: $curr does not exist";
			next comic;
		}
		if ($curr == $last) {
			$ok ++;
			next comic;
		}
		if (!$strips->{$curr}->{next}) {
			say "$comic: $curr has no next";
			next comic;
		}
		else {
			my $next = $strips->{$curr}->{next};
			if ($next == $curr) {
				say "$comic: $curr has itself ($next) as next";
				next comic;
			}
			if (!$strips->{$next}->{prev}) {
				say "$comic: next $next has no prev";
				next comic;
			}
			if ($strips->{$next}->{prev} != $curr) {
				say "$comic: next $next does not link back to $curr";
				next comic;
			}
		}
	} while ($curr = $strips->{$curr}->{next});
}

say "all: $all ok: $ok nok: " . ($all - $ok); 
