use 5.010;
use strict;
use warnings;
use DBI;
use lib '../lib/';
use dbutil;


say "this will now try to convert any database pre version 3.5. be sure to have back ups!";
say "you will have to replace the database manually ";
say "it will work best if the database is fully updated";
say "be warned this will take a while";
say "type 'start' to start the conversion everything else aborts";
die unless (<> =~ /start/);
 
my $ini = dbutil::readINI("../comic.ini");

my $dbo = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});
my $dbn = DBI->connect("dbi:SQLite:dbname=comics_new.db","","",{AutoCommit => 0,PrintError => 1});
if (1) {
	say "USERS => comics";
	my $user = $dbo->selectall_hashref('SELECT * FROM USER', 'comic'); 

	my @com_rows = qw(url_current archive_current first last bookmark strip_count last_update last_save flags tags);

	$dbn->do('CREATE TABLE comics ( comic PRIMARY KEY, '.  join(',',@com_rows).')');



	foreach my $comic (keys %$ini) {
		next if ($ini->{$comic}->{broken});
		
		my $stn = $dbn->prepare('INSERT INTO comics (comic,'.join(',',@com_rows).') VALUES ( ?,?,?,?,?,?,?,?,?,?,?)' );
		
		my $r = $user->{$comic};
		my @values;
		push (@values,$r->{$_}) for qw(comic url_current archive_current first last bookmark strip_count last_update last_save flags tags);
		$stn->execute(@values);
	}

	say "committing";
	$dbn->commit();
}

if (1) {
	say "_comic => _comic";
	my @com_rows = qw(file prev next number surl purl title time sha1); 

	foreach my $comic (keys %$ini) {
		next if ($ini->{$comic}->{broken});
		say $comic;
		my $cmcdb = $dbo->selectall_hashref('SELECT rowid,* FROM _'.$comic,'rowid'); 
		$dbn->do("CREATE TABLE _" .  $comic . " ( id INTEGER PRIMARY KEY ASC, " . join(",",@com_rows) . ")");
		my $stn = $dbn->prepare('INSERT INTO _' .  $comic . ' ('.join(',',@com_rows).') VALUES ( ?,?,?,?,?,?,?,?,?)' );
		foreach my $rid (sort {$a <=> $b} keys %$cmcdb) {
			my $s = $cmcdb->{$rid};
			my @values;
			push (@values,$s->{$_}) for qw(strip prev next number surl url title time sha1);
			$stn->execute(@values);
		}
		
		my $files = $dbn->selectall_hashref('SELECT file,id FROM _'.$comic, 'file');
		my $nh = $dbn->prepare('UPDATE _'.$comic.' SET next = ? WHERE next = ?');
		my $ph = $dbn->prepare('UPDATE _'.$comic.' SET prev = ? WHERE prev = ?');
		foreach (keys %$files) {
			my ($file,$id) = ($files->{$_}->{file}, $files->{$_}->{id});
			$nh->execute($id,$file);
			$ph->execute($id,$file);
		}
		
	}
	say "committing";
	$dbn->commit();
}

if (1) {
	say "updating pointers in comics";
	
	foreach my $comic (keys %$ini) {
		next if ($ini->{$comic}->{broken});
		
		my @point = qw(first last bookmark);
		
		foreach my $p (@point) {
			my ($fname) = $dbn->selectrow_array("SELECT $p FROM comics WHERE comic = '$comic'");
			next unless ($fname);
			next if ($fname =~ /^\d+$/);
			my $sth = $dbn->prepare("SELECT id FROM _$comic WHERE file = ?");
			$sth->execute($fname);
			my ($id) = $sth->fetchrow_array();
			$id //= 'NULL';
			$dbn->prepare("UPDATE comics SET $p = $id WHERE $p = ?")->execute($fname);
				
		}

	}
	$dbn->commit();
}



