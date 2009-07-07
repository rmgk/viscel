use 5.010;
use strict;
use warnings;
use DBI;
use lib "../lib";
use dbutil;

my $comics = dbutil::readINI('../comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});

foreach my $comic (keys %$comics) {
	next if $comics->{$comic}->{broken};
	say $comic;
	my $entry = $dbh->selectall_arrayref("SELECT * FROM _$comic",{ Slice => {} });
	foreach my $ent (@$entry) {
		my $next = $dbh->selectrow_array("SELECT sha1 FROM _$comic WHERE id = ?",undef,$ent->{next}) if ($ent->{next} and ($ent->{next} =~ /^\d+$/));
		my $prev = $dbh->selectrow_array("SELECT sha1 FROM _$comic WHERE id = ?",undef,$ent->{prev}) if ($ent->{prev} and ($ent->{prev} =~ /^\d+$/));
		$dbh->do("UPDATE _$comic SET next=?,prev=? WHERE id = ?",undef,$next,$prev,$ent->{id}) if ($prev or $next);
	}
}
$dbh->commit();

my $entry = $dbh->selectall_arrayref("SELECT * FROM comics",{ Slice => {} });
foreach my $ent (@$entry) {
	my $first = $dbh->selectrow_array("SELECT sha1 FROM _".$ent->{comic}." WHERE id = ?",undef,$ent->{first}) if ($ent->{first} and ($ent->{first} =~ /^\d+$/));
	my $last = $dbh->selectrow_array("SELECT sha1 FROM _".$ent->{comic}."  WHERE id = ?",undef,$ent->{last}) if ($ent->{last} and ($ent->{last} =~ /^\d+$/));
	my $bookmark = $dbh->selectrow_array("SELECT sha1 FROM _".$ent->{comic}."  WHERE id = ?",undef,$ent->{bookmark}) if ($ent->{bookmark} and ($ent->{bookmark} =~ /^\d+$/));
	$dbh->do("UPDATE comics SET first=?,last=?,bookmark=? WHERE comic = ?",undef,$first,$last,$bookmark,$ent->{comic});
}
$dbh->commit();