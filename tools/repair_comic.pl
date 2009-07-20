use 5.010;
use strict;
use warnings;
use lib "../lib";
use dbutil;
use DBI;
use Data::Dumper;

die 'cant start with locks present' if (-e '../.singlelock' or -e '../.multilock.db');

my $comics = dbutil::readINI('../comic.ini');
my $dbh = DBI->connect("dbi:SQLite:dbname=../comics.db","","",{AutoCommit => 0,PrintError => 1});

my $comic = $ARGV[0];

die "no such comic $comic" if (!$comics->{$comic} or $comics->{$comic}->{broken});


###################################################
#general cleanup
###################################################
say "starting general cleanup";

my $prev_clean = $dbh->do("UPDATE _$comic SET prev = NULL WHERE prev NOT IN (SELECT id FROM _$comic)");
my $next_clean = $dbh->do("UPDATE _$comic SET next = NULL WHERE next NOT IN (SELECT id FROM _$comic)");
my $none_clean = $dbh->do("DELETE FROM _$comic WHERE next IS NULL AND prev IS NULL");
my $solo_clean = $dbh->do("DELETE FROM _$comic WHERE id NOT IN(SELECT prev FROM _$comic) AND id NOT IN(SELECT next FROM _$comic)");

if (0<($prev_clean + $next_clean + $none_clean + $solo_clean)) {
	print "$comic: cleaned";
	print " prev: $prev_clean" if ($prev_clean != 0);
	print " next: $next_clean" if ($next_clean != 0);
	print " none: $none_clean" if ($none_clean != 0);
	print " solo: $solo_clean" if ($solo_clean != 0);
	print "\n";
}


###################################################
#check first
###################################################
my ($t_comic,$first,$last,$bookmark,$url_current) = $dbh->selectrow_array("SELECT comic,first,last,bookmark,url_current FROM comics WHERE comic = ?",undef,$comic) or die 'database access failed';
die "$comic is not $t_comic" unless ($t_comic eq $comic);

$bookmark //= ''; #no errors in strip concatenation but still looks like undefined
say "comic is $t_comic first is $first last is $last bookmark is $bookmark \nurl_current is $url_current";
$bookmark ||= 0; #no error in numeric equal


if (!$first) {
	say "first seems to be invalid try to find strip with number 1";
	my $frsts = $dbh->selectall_arrayref("SELECT id FROM _$comic WHERE number = 1",{Slice=>{}});
	if (@$frsts > 1) {
		say "it seems we have ". scalar(@$frsts)." possible first candidates";
		die "not implemented yet";
	}
	elsif(@$frsts == 1) {
		say $frsts->[0]->{id} . " is our first";
		$first = $frsts->[0]->{id};
	}
	else {
		die 'could not find first';
	}
	say "commiting first $first into database";
	$dbh->do("UPDATE _$comic SET first = ? WHERE comic = ?",undef,$first,$comic) or die "could not commit to database $!";
}

say "selecting strips from database";
my $strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id") or die 'database access failed';

say "checking first";

if (test_data($first) == 0) {
	die 'wont continue with bad first';
}

if ($strips->{$first}->{prev}) {
	my $prev = $strips->{$first}->{prev};
	say "first $first has $prev as prev";
	delete_fishy($prev);
}

say "setting first as current and starting loop";

my $number = 0;
my $curr;
my $seen;

###################################################
#main loop
###################################################

for ($curr = $first; $curr ; $curr = $strips->{$curr}->{next}) {
	$number++;
	die "circular reference $curr" if $seen->{$curr};
	$seen->{$curr} = $number // 0; 
	say "$curr number ".$strips->{$curr}->{number}." not matching $number" if ($strips->{$curr}->{number} != $number);
	if ($curr == $last) {
		say "we successfully travelled the comic! curr $curr last $last number $number";
		last;
	}
	
	### check next ######################################
	
	my $next = $strips->{$curr}->{next};
	if (!$next or ($next == $curr)) {
		say "$curr has no next try to find some candidates";
		my $mbn = $dbh->selectall_arrayref("SELECT id FROM _$comic WHERE prev = ?",{Slice=>{}},$curr) or die 'database access failed';
		if (scalar(@$mbn) == 1) {
			if (test_data($mbn->[0]->{id})) {
				say "setting next for $curr to " . $mbn->[0]->{id};
				$dbh->do("UPDATE _$comic SET next = ? WHERE id = ?",undef,$mbn->[0]->{id},$curr) or die 'database access failed';
				say "reloading database";
				$strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
			}
			else {
				say "could not find next";
				last;
			}
		}
		elsif (scalar(@$mbn) > 1) {
			die 'multiple choice not yet implemented';
		}
		else {
			say "nothing links back. try to find something with next number";
			my $mbnn = $dbh->selectall_arrayref("SELECT id FROM _$comic WHERE number = ?",{Slice=>{}},$strips->{$curr}->{number}+1) or die 'database access failed';
			if (scalar(@$mbnn) == 1) {
				if (test_data($mbnn->[0]->{id})) {
					my $mbni = $mbnn->[0]->{id};
					die 'maybe next already has prev' if $strips->{$mbni}->{prev};
					say "setting next for $curr to " . $mbni;
					$dbh->do("UPDATE _$comic SET next = ? WHERE id = ?",undef,$mbni,$curr) or die 'database access failed';
					say "setting prev for $mbni to $curr";
					$dbh->do("UPDATE _$comic SET prev = ? WHERE id = ?",undef,$curr,$mbni) or die 'database access failed';
					say "reloading database";
					$strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
				}
				else {
					say "could not find next";
					last;
				}
			}
			elsif (scalar(@$mbnn) > 1) {
				die 'multiple choice not yet implemented';
			}
			else {
				say 'could not find next';
				last;
			}
		}
		$next = $strips->{$curr}->{next};
	}
	if (test_data($next,'non_verbose')==0) {
		say "next $next does not exist! or was not accepted";
		last;
	}
	
	### check prev ######################################
	
	if (!$strips->{$next}->{prev} or $strips->{$next}->{prev} != $curr) {
		if (!$strips->{$next}->{prev} or ($strips->{$next}->{number} == ($strips->{$curr}->{number}+1))) {
			say "next $next does not link back to curr $curr but seems save to change";
			$dbh->do("UPDATE _$comic SET prev = ? WHERE id = ?",undef,$curr,$next) or die 'database access failed';
			say "reloading database";
			$strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id") or die 'database access failed';
		}
		else {
			die 'bad backlink of next (not implemented)';
		}
	}
}


###################################################
# check last
###################################################

if ($curr != $last) {
	say "curr $curr did not travel to last $last";
	say "check if url_current $url_current matches curr ${curr}'s purl:\n" .  $strips->{$curr}->{purl};
	my $prev_purl = $strips->{$strips->{$curr}->{prev}}->{purl};
	say "or the prev purl: " . $prev_purl;
	if ($strips->{$curr}->{purl} =~ m#\Q$url_current\E$#i or $prev_purl =~ m#\Q$url_current\E$#i) {
		say "we have a match it seems save to assume that $curr is the last";
		$dbh->do("UPDATE comics SET last = ?,url_current=? WHERE comic = ?",undef,$curr,$prev_purl,$comic) or die 'database access failed';
		$dbh->do("UPDATE _$comic SET next = NULL WHERE id = ?",undef,$curr) or die 'database access failed';
		say "database successfully updated";
	}
	else {
		say "no match";
		say "should i force curr $curr to be the last and update url_current? [yes|NO]";
		if (<STDIN> =~ m/yes/) {
			$dbh->do("UPDATE comics SET last = ?,url_current=? WHERE comic = ?",undef,$curr,$prev_purl,$comic) or die 'database access failed';
			$dbh->do("UPDATE _$comic SET next = NULL WHERE id = ?",undef,$curr) or die 'database access failed';
			say "try to update the comic and check back afterwards";
		}
	}

}


###################################################
#check junk
###################################################

my $seen_count = scalar(keys %$seen);
my $strip_count = scalar(keys %$strips);

if ($seen_count != $strip_count) {
	say "there have been $seen_count ids seen and $strip_count ids are in the dataase";
	if ($seen_count<$strip_count) {
		say "should i delete the ones not seen? [yes|NO]";
		if (<STDIN> =~ m/yes/) {
			my $sth = $dbh->prepare("DELETE FROM _$comic WHERE id = ?");
			my $del = $sth->execute_array(undef,[grep {!$seen->{$_}} keys %$strips]) or die 'database access failed';
			say "deleted $del entrys";
		}
	}
	else {
		die "seen count bigger than strip count .. that should not be possible";
	}
}


say "should i commit all changes i made? [yes|NO]";
if (<STDIN> =~ m/yes/) {
	$dbh->commit;
	say "commit";
}
else {
	$dbh->rollback();
	say "rollback";
}


$dbh->disconnect;


sub delete_fishy {
	my $fishy = shift;
	say "WARNING: $fishy is bookmark" if $fishy == $bookmark;
	say "$fishy was marked for deletion here is its dump:";
	say Dumper $strips->{$fishy};
	say "do you want to delete it? [y/N]";
	if (<STDIN> =~ m/y/) {
		$dbh->do("DELETE FROM _$comic WHERE id = ?",undef,$fishy) or die "could not delete $fishy";
		$dbh->do("UPDATE _$comic SET next = NULL WHERE next = ?",undef,$fishy) or die "could not update other next";
		$dbh->do("UPDATE _$comic SET prev = NULL WHERE prev = ?",undef,$fishy) or die "could not update other prev";
		say "all updated reloading database";
		$strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
		return 1;
	}
	else {
		say "not deleting $fishy";
		return 0;
	}
}

sub test_data {
	my $id = shift;
	my $less_verbose = shift;
	my $st = $strips->{$id}; 
	return 0 unless $st;
	if ($st->{file} and $st->{purl} and $st->{surl} and $st->{sha1} and $st->{number}) {
		#say "$id was tested fine";
		return 1;
	}
	elsif ($st->{file}) {
		say "$id did not contain all information";
		if (defined $st->{sha1} and ($st->{sha1} =~ m/\w{40}/)) {
			return -3 if ($less_verbose);
			say Dumper($st);
			say "accept this strip as ok? (it has file and sha1) [OK]";
			if (<STDIN> =~ m/OK|^$/i) {
				return -1;
			}
			return 0;
		}
		say Dumper($st);
		say "accept this strip as ok? (seems to be dummy) [OK]";
		if (<STDIN> =~ m/OK|^$/i) {
			return -2;
		}
		return 0;
	}
	elsif ($st->{purl} and $st->{number} and $st->{prev} and $st->{next}) {
		say "$id is a bridging dummy";
		return -4;
	}
	say "$id has missing information";
	return 0
}
