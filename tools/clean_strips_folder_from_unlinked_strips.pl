use 5.010;
use strict;
use warnings;
use lib "../lib";
use dbutil;
use DBI;
use File::Copy;

my $comics = dbutil::readINI('../comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});

mkdir("bak") unless -d "bak";

foreach my $comic (keys %$comics) {
	say $comic;
	my $files = $dbh->selectall_hashref("SELECT file FROM _$comic",'file') unless $comics->{$comic}->{broken};
	opendir(CD,"../strips/$comic") or next;
		my $file_count = 0;
		my $move_count = 0;
		while (my $file = readdir(CD)) {
			next if $file =~ m/^\.\.?$/;
			$file_count ++;
			if ($comics->{$comic}->{broken} or !$files->{$file}) {
				mkdir("bak/$comic") unless -d "bak/$comic";
				say "move ../strips/$comic/$file to bak/$comic/";
				move("../strips/$comic/$file","bak/$comic/") or die "Move failed: $!";
				$move_count++;
			}
		}
		if (($file_count - $move_count) == 0) {
			say "remove dir ../strips/$comic/";
			rmdir("../strips/$comic/");
		}
	closedir (CD);
}
