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


my $build = 96 + $Comic::VERSION + $Page::VERSION + $Strip::VERSION + $dbutil::VERSION + $dlutil::VERSION;
our $VERSION = 3.052 . '.'. $build;



our $TERM = 0;
$SIG{'INT'} = sub { 
		print "\nTerminating (wait for downloads to finish)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "press ctrl+c to abort (or data corruption might occur)\n";
print "comic3.pl version $VERSION\n\n";

die 'it seems there is another instance running, delete singelelock if there is not.
start with --multi if you want to run multiple instances.' if (-e '.singlelock');

my $multi = 0;
if (-e '.multilock.db') {
	if ($ARGV[0] and ($ARGV[0] eq'--multi')) {
		$multi = 2;
		@ARGV = ();
	}
	else {
		die 'it seems we are running in multi mode, start with --multi if we are or delete ".multilock.db" if we are not.';
	}
}
if ($ARGV[0] and ($ARGV[0] eq'--multi')) {
	$multi = 1;
	say "please wait before starting another instance";
	@ARGV = ();
}
unless ($multi) {
	open(LOCK,">.singlelock");
	print LOCK "delete this file if no instances of comic3.pl is running";
	close LOCK;
}


if (-e 'log.txt.' && (-s _ > 2 * 2**20)) {
	unlink("log.txt");
}



my $comics = dbutil::readINI('comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout');
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
	if ($ARGV[0] =~ m/^-\w+/) {
		die "no such comic: $ARGV[1]" unless defined $comics->{$ARGV[1]};
		if ($ARGV[0] eq '-r') {
			$dbh->do('UPDATE comics SET url_current = NULL,server_update = NULL,archive_current = NULL,first=NULL where comic=?',undef,$ARGV[1]);
			#$dbh->commit;
		}
		elsif ($ARGV[0] eq '-rd') {
			#unless ($dbh->selectrow_array('SELECT processing FROM CONFIG WHERE processing IS NOT NULL')) { # TODO
				#$dbh->do('DELETE FROM comics WHERE comic=?',undef,$ARGV[1]);
				$dbh->do('UPDATE comics SET url_current=NULL,server_update=NULL,archive_current=NULL,first=NULL,last=NULL where comic=?',undef,$ARGV[1]);
				# ($comics->{$ARGV[1]}->{tags},$comics->{$ARGV[1]}->{flags}) = 
						# $dbh->selectrow_array('SELECT tags,flags FROM comics WHERE comic = ?',undef,$ARGV[1]);
				$dbh->do("DROP TABLE _". $ARGV[1]);
			#}
			#else {
			#	say "\nplease stop current progress before dropping tables\n";
			#}
		}
		elsif ($ARGV[0] eq '-u') {
			say "marking $ARGV[1] as recently updated";
			$dbh->do('UPDATE comics SET last_update = ? where comic=?',undef,time,$ARGV[1]);
			exit;
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


if (($multi == 0) or ($multi == 1)) {
	dbutil::check_table($dbh,'comics');
	dbutil::check_table($dbh,\@comics);
}
my $lock;
my $worker_number;
if ($multi == 1) {
	$lock = DBI->connect("dbi:SQLite:dbname=.multilock.db","","",{AutoCommit => 0,PrintError => 1});
	$lock->do('CREATE TABLE lock (comic,domain,time)');
	$lock->do('CREATE TABLE worker (worker_number INTEGER PRIMARY KEY ASC)');
	$lock->do('INSERT INTO worker DEFAULT VALUES');
	$worker_number = $lock->last_insert_id(undef,undef,'worker','worker_number');
	$lock->commit();
	say 'created lock database, start more instances with --multi';
}
elsif ($multi == 2) {
	$lock = DBI->connect("dbi:SQLite:dbname=.multilock.db","","",{AutoCommit => 0,PrintError => 1});
	$lock->do('INSERT INTO worker DEFAULT VALUES');
	$worker_number = $lock->last_insert_id(undef,undef,'worker','worker_number');
	$lock->commit();
}

my %order;
my $cdb = $dbh->selectall_hashref('SELECT comic,last_update,last_save FROM comics','comic');
foreach my $comic (@comics) {
	# if (!$cdb->{$comic}->{last_update} or !$cdb->{$comic}->{last_save} ) {
		# $order{$comic} = 1;
		# next;
	# }
	# my $up = (time - $cdb->{$comic}->{last_update} ) || 1;
	# my $sa = (time - $cdb->{$comic}->{last_save} ) || 1;
	# $order{$comic} =  $up/$sa;
	my $up = $cdb->{$comic}->{last_update} // 0;
	my $sa = $cdb->{$comic}->{last_save} // 0;
	$order{$comic} = time - $up - $up + $sa;;
}

@comics = sort { $order{$b} <=> $order{$a} } @comics; 
	
my $nl = ''; #needed for some nice newline messages :D
comic:foreach my $comic (@comics) {	
	print $nl;
	$nl = '';
	if ($skip) {
		my $lu = $dbh->selectrow_array('SELECT last_update FROM comics WHERE comic = ?',undef,$comic);
		next comic if ((time - $update_intervall) < ($lu||0));
	}
	last if $TERM;
	if ($multi) {
		my ($domain) = $comics->{$comic}->{url_start} =~ m#^(?:https?://)?(?:[^./]+\.)*?([^./]+\.[^./]+?)/#; #use domain name to exclude multi downloading
		$lock->do('INSERT INTO lock (domain,time,comic) values (?,?,?)',undef,$domain,time,$comic);
		my $count = $lock->selectrow_array('SELECT COUNT(*) FROM lock WHERE domain == ?',undef,$domain);
		die "error writing lock $comic : $domain" unless ($count);
		if ($count > 1) {
			$lock->rollback();
			#say "skipped $comic: already downloading from '$domain'";
			next comic;
		}
		$lock->commit();
	}
	$nl = "\n";
	Comic::get_comic({	"name" => $comic , "dbh" => $dbh, "autocommit" => 1, 
						'flags' => $comics->{$comic}->{flags}, 
						'tags' => $comics->{$comic}->{tags}
						});
	if ($multi) {
		$lock->do('DELETE FROM lock WHERE comic = ?',undef,$comic);
		$lock->commit();
	}
	last if $TERM;
}
END {
	$dbh->disconnect;
	if ($multi) {
		$lock->do('DELETE FROM worker WHERE worker_number = ?',undef,$worker_number);
		$lock->commit();
		my $remaining = $lock->selectrow_array('SELECT COUNT(*) FROM worker');
		$lock->disconnect;
		say "worker $worker_number shutting down $remaining workers remaining";
		if ($remaining == 0) {
			say "all workers finished deleting lock";
			unlink ('.multilock.db') or die 'error deleting lock';
		} 
	}
	else {
		unlink ('.singlelock') or die 'error deleting lock';
	}
}