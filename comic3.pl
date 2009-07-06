#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009

use 5.010;
use strict;
use warnings;
use lib "./lib";
use DBI;
use Comic;
use dbutil;

my $build = 89 + $Comic::VERSION + $Page::VERSION + $Strip::VERSION + $dbutil::VERSION + $dlutil::VERSION;
our $VERSION = 3.050 . '.'. $build;



our $TERM = 0;
$SIG{'INT'} = sub { 
		print "\nTerminating (wait for downloads to finish)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "press ctrl+c to abort (or data corruption might occur)\n";
print "comic3.pl version $VERSION\n";

if (-e 'log.txt.' && (-s _ > 10 * 2**20)) {
	unlink("log.txt");
}



my $comics = dbutil::readINI('comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout');
dbutil::check_table($dbh,'comics');
# dbutil::check_table($dbh,'CONFIG'); TODO
my @comics;
@comics = grep {!$comics->{$_}->{'broken'}} keys %{$comics};

my $update_intervall = 45000; #$dbh->selectrow_array(qq(SELECT update_intervall FROM config)); #TODO
if (!defined $update_intervall or $update_intervall eq '') {
	$update_intervall = 45000;
	print "no update interval specified using default = $update_intervall seconds\n";
}

my $skip = 1;

if ($ARGV[0]) {
	if ($ARGV[0] and ($ARGV[0] =~ m/^-\w+/)) {
		die "no such comic: $ARGV[1]" unless defined $comics->{$ARGV[1]};
		if ($ARGV[0] eq '-r') {
			$dbh->do('UPDATE comics SET url_current = NULL,server_update = NULL,archive_current = NULL where comic=?',undef,$ARGV[1]);
			#$dbh->commit;
		}
		elsif ($ARGV[0] eq '-rd') {
			#unless ($dbh->selectrow_array('SELECT processing FROM CONFIG WHERE processing IS NOT NULL')) { # TODO
				$dbh->do('DELETE FROM comics WHERE comic=?',undef,$ARGV[1]);
				($comics->{$ARGV[1]}->{tags},$comics->{$ARGV[1]}->{flags}) = 
						$dbh->selectrow_array('SELECT tags,flags FROM comics WHERE comic = ?',undef,$ARGV[1]);
				$dbh->do("DROP TABLE _". $ARGV[1]);
			#}
			#else {
			#	say "\nplease stop current progress before dropping tables\n";
			#}
		}
		@comics = ();
		$comics[0] = $ARGV[1];
		$skip = 0;
		
	}
	elsif ($ARGV[0] =~ m#^(\w+)$#) {
		die "no such comic: $ARGV[0]" unless defined $comics->{$ARGV[0]};
		@comics = ();
		$comics[0] = $ARGV[0];
		$skip = 0;
	}
	elsif ($ARGV[0] =~ m#^/(.*)/$#) {
		@comics = grep { $_ ~~ /$1/i} @comics;
	}
}

my %order;
my $cdb = $dbh->selectall_hashref('SELECT comic,last_update,last_save FROM comics','comic');
foreach my $comic (@comics) {
	if (!$cdb->{$comic}->{last_update} or !$cdb->{$comic}->{last_save} ) {
		$order{$comic} = 1;
		next;
	}
	my $up = (time - $cdb->{$comic}->{last_update} ) || 1;
	my $sa = (time - $cdb->{$comic}->{last_save} ) || 1;
	$order{$comic} =  $up/$sa;
}

dbutil::check_table($dbh,\@comics);

@comics = sort { $order{$b} <=> $order{$a} } @comics; 
	
	
comic:foreach my $comic (@comics) {	
	if ($skip) {
		my $lu = $cdb->{$comic}->{last_update};
		next comic if ((time - $update_intervall) < ($lu||0));
	}
	
	last if $TERM;
	
	my ($domain) = $comics->{$comic}->{url_start} =~ m#(?:http://)?([^.]+\.[^.]+?)/#; #use domain name to exclude multi downloading
	{	#if we run multiple instances of the programm we dont want two to process  the same comic
		my $time = 0; #$dbh->selectrow_array(qq(SELECT time FROM CONFIG WHERE processing == "$domain")) // 0; # todo
		if ($time and ((time - $time)  < 60*60*2 )) { 
			say "\nskipped $comic: already downloading from '$domain'";
			next comic;
		}
		elsif ($time){ #is downloading for more than two ours
			say "\ndownloading from '$domain' for more than two hours, likely crashed. overwriting.";
			$dbh->do("UPDATE config SET time = " .time . qq( WHERE processing == "$domain")); # TODO
		}
		else {
			#$dbh->do(qq!INSERT INTO CONFIG (processing,time) values ("$domain",! .time . ")" ); # TODO
		}
	}
	Comic::get_comic({	"name" => $comic , "dbh" => $dbh, "autocommit" => 1, 
						'flags' => $comics->{$comic}->{flags}, 
						'tags' => $comics->{$comic}->{tags}
						});

	#$dbh->do(qq(DELETE FROM CONFIG WHERE processing = "$domain")); TODO
	last if $TERM;
}
$dbh->disconnect;
