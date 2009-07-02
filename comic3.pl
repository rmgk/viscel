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

my $build = 86 + $Comic::VERSION + $Page::VERSION + $Strip::VERSION + $dbutil::VERSION + $dlutil::VERSION;
our $VERSION = 3.500 . '.'. $build;



our $TERM = 0;
$SIG{'INT'} = sub { 
		print "\nTerminating (wait for downloads to finish)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "press ctrl+c to abort (or data corruption might occur)\n";
print "comic3.pl version $VERSION\n";

my @opts = @ARGV;

if (-e 'log.txt.' && (-s _ > 10 * 2**20)) {
	unlink("log.txt");
}


{
	my $comics = dbutil::readINI('comic.ini');
	my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
	#$dbh->{Profile} = "6/DBI::ProfileDumper";
	$dbh->func(300000,'busy_timeout');
	dbutil::check_table($dbh,'comics');
	# dbutil::check_table($dbh,'CONFIG'); TODO
	my @comics;
	@comics = keys %{$comics};
	my $opmode;
	if ($opts[0]) {
		$opmode = "std";
		if (($opts[0] eq '-r') and (@opts > 1)) {
			$opmode = 'repair';
			shift @opts;
			foreach (@opts) {
				$dbh->do(qq(UPDATE comics SET url_current = NULL,server_update = NULL,archive_current = NULL where comic="$_"));
			}
			#$dbh->commit;
		}
		elsif (($opts[0] eq '-e') and (@opts > 1)) {
			shift @opts;
			$opmode = 'exact';
		}
		elsif (($opts[0] eq '-rd') and (@opts > 1)) {
			$opmode = 'repairdelete';
			unless ($dbh->selectrow_array(qq(SELECT processing FROM CONFIG WHERE processing IS NOT NULL))) { # TODO
				shift @opts;
				foreach (@opts) {
					$dbh->do(qq(UPDATE comics SET url_current = NULL,server_update = NULL,archive_current = NULL where comic="$_"));
					$dbh->do(qq(DROP TABLE _$_));

				}
			}
			else {
				say "\nplease stop current progress before dropping tables\n";
			}
			
		}
	}
	
	my $update_intervall = 45000; #$dbh->selectrow_array(qq(SELECT update_intervall FROM config)); #TODO
	if (!defined $update_intervall or $update_intervall eq '') {
		$update_intervall = 45000;
		print "no update interval specified using default = $update_intervall seconds\n";
	}
	
	my %order;
	
	foreach my $comic (@comics) {
		dbutil::check_table($dbh,"_$comic") unless $comics->{$comic}->{broken};
		my $lu = $dbh->selectrow_array(qq(SELECT last_update FROM comics WHERE comic="$comic"));
		my $ls = $dbh->selectrow_array(qq(SELECT last_save FROM comics WHERE comic="$comic"));
		if (!$lu or !$ls) {
			$order{$comic} = 1;
			next;
		}
		my $up = (time - $lu) || 1;
		my $sa = (time - $ls) || 1;
		$order{$comic} =  $up/$sa;
	}
	
	@comics = sort { $order{$b} <=> $order{$a} } @comics; 
		
	comic:foreach my $comic (@comics) {	
		my $skip = 0;
		my $broken = $comics->{$comic}->{'broken'};
		if (defined $opmode) {
			if ($opmode eq 'std') {
				next comic if ($broken);
				for my $opt (@opts) {
					next comic unless ($comic =~ m/$opt/i);
				}
			}
			elsif (($opmode eq 'repair') or ($opmode eq 'exact') or ($opmode eq 'repairdelete')) {
				for my $opt (@opts) {
					next comic unless ($comic eq $opt);
				}
			}
		}
		else {
			my $lu = $dbh->selectrow_array(qq(SELECT last_update FROM comics WHERE comic="$comic"));
			next comic if (((time - $update_intervall) < ($lu||0)) or $broken);
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
		Comic::get_comic({"name" => $comic , "dbh"=> $dbh, "autocommit" => 0});
		{	#unset processing when done
			#$dbh->do(qq(DELETE FROM CONFIG WHERE processing = "$domain")); TODO
			;
		}
		last if $TERM;
	}
	$dbh->disconnect;
}