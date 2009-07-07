use 5.010;
use strict;
use warnings;
use lib "../lib";
use dbutil;
use DBI;
use Digest::SHA;

my $SHA = Digest::SHA->new();

my $comics = dbutil::readINI('../comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});

foreach my $comic (keys %$comics) {
	next if $comics->{$comic}->{broken};
	say $comic;
	my $entry = $dbh->selectall_arrayref("SELECT * FROM _$comic WHERE sha1 IS NULL",{ Slice => {} });
	foreach my $ent (@$entry) {
		my $file = $ent->{file};
		if ($file) {
			$file = "./strips/$comic/$file";
			if (-e $file) { 
				#say $file;
				my $sha = $SHA->addfile($file,"b")->hexdigest;
				if ($sha) {
					$dbh->do("UPDATE _$comic SET sha1 = ? WHERE id = ?",undef,$sha,$ent->{id});
				}
				else {
					say "no sha for $file";
				}
			}
			else {
				$dbh->do("UPDATE _$comic SET sha1 = ? WHERE id = ?",undef,$ent->{id},$ent->{id});
				say "$file not found";
			}
		}
		else {
			say "no file " . $ent->{id};
		}
	}
	$dbh->commit();
}
