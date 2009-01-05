#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:16 06.10.2008

use strict;
use warnings;
use feature qw(say switch);
use lib "./lib";
use DBI;
use Comic;
use dbutil;

our $VERSION;
$VERSION = '83' . '.' . $Comic::VERSION . '.' . $Page::VERSION;


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
	$dbh->func(300000,'busy_timeout');
	dbutil::check_table($dbh,'USER');
	dbutil::check_table($dbh,'CONFIG');
	my @comics;
	@comics = keys %{$comics};
	my $opmode;
	if ($opts[0]) {
		$opmode = "std";
		if (($opts[0] eq '-r') and (@opts > 1)) {
			$opmode = 'repair';
			shift @opts;
			foreach (@opts) {
				$dbh->do(qq(update USER set url_current = NULL,server_update = NULL,archive_current = NULL where comic="$_"));
			}
			#$dbh->commit;
		}
		elsif (($opts[0] eq '-e') and (@opts > 1)) {
			shift @opts;
			$opmode = 'exact';
		}
		elsif (($opts[0] eq '-rd') and (@opts > 1)) {
			$opmode = 'repairdelete';
			unless ($dbh->selectrow_array(qq(select processing from CONFIG where processing is not null))) {
				shift @opts;
				foreach (@opts) {
					$dbh->do(qq(update USER set url_current = NULL,server_update = NULL,archive_current = NULL where comic="$_"));
					$dbh->do(qq(DROP TABLE _$_));

				}
			}
			else {
				say "please stop current progress before dropping tables";
			}
			
		}
	}
	
	my $update_intervall = $dbh->selectrow_array(qq(select update_intervall from CONFIG));
	if (!defined $update_intervall or $update_intervall eq '') {
		$update_intervall = 45000;
		print "no update interval specified using default = $update_intervall seconds\n";
	}
	
	my %order;
	
	foreach my $comic (@comics) {
		dbutil::check_table($dbh,"_$comic") unless $comics->{$comic}->{broken};
		my $lu = $dbh->selectrow_array(qq(select last_update from USER where comic="$comic"));
		my $ls = $dbh->selectrow_array(qq(select last_save from USER where comic="$comic"));
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
			my $lu = $dbh->selectrow_array(qq(select last_update from USER where comic="$comic"));
			next comic if (((time - $update_intervall) < ($lu||0)) or $broken);
		}
		last if $TERM;
		
		my ($domain) = $comics->{$comic}->{url_start} =~ m#(?:http://)?([^.]+\.[^.]+?)/#; #use domain name to exclude multi downloading
		{	#if we run multiple instances of the programm we dont want two to process  the same comic
			if ($dbh->selectrow_array(qq(select processing from CONFIG where processing == "$domain"))) {
				say "\nskipped $comic: already downloading from '$domain'";
				next comic;
			}
			$dbh->do(qq(insert into CONFIG (processing) values ("$domain")));
		}
		Comic::get_comic({"name" => $comic , "dbh"=> $dbh, "autocommit" => 1});
		{	#unset processing when done
			$dbh->do(qq(delete from CONFIG where processing = "$domain"));
		}
		last if $TERM;
	}
	$dbh->disconnect;
}