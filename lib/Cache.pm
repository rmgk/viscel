#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cache v1.3.0;

use 5.012;
use warnings;

use Log;

my $DBH;
my $STH_get;
my $STH_put;
my $cachedir;

#initialises the cache
sub init {
	Log->trace('initialise cache');
	$cachedir = shift // Globals::cachedir();
	
	unless (-e $cachedir or mkdir $$cachedir) {
		Log->error('could not create cache dir ' , $cachedir);
		return;
	}
	
	for my $a ('0'..'9','a'..'f') { for my $b ('0'..'9','a'..'f') { 
		my $dir = $cachedir.$a.$b.'/';
		unless (-e $dir or mkdir $dir) {
			Log->error('could not create storage dir ' .$dir);
			return;
		}
	}}
	
	$DBH = DBI->connect("dbi:SQLite:dbname=".$cachedir.'stats.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return unless $DBH;
	unless ($DBH->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,"type")) {
		unless($DBH->do('CREATE TABLE type (sha1 CHAR UNIQUE, value)')) {
			Log->error('could not create table type');
			return;
		}
		$DBH->commit();
	}
	
	$STH_get = $DBH->prepare('SELECT value FROM type WHERE sha1 = ?');
	$STH_put = $DBH->prepare('INSERT OR REPLACE INTO type (sha1,value) VALUES (?,?)');

	return 1;
}

#$sha1,\$blob -> $bool
#stores the blob; true if successful false if not
sub put {
	my ($sha1,$blob) = @_;
	Log->trace('store ' . $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '>', $cachedir.$sha1) {
		Log->error("could not open $main::DIRCACHE$sha1 for write");
		return;
	}
	binmode $fh;
	print $fh $$blob;
	close $fh;
	return 1;
}

#$sha1 -> \$blob
#retrieves the $blob for $sha
sub get {
	my ($sha1) = @_;
	if (!defined $sha1 or $sha1 !~ m/^[\da-f]{40}$/) {
		Log->error('incorrect sha1 value');
		return;
	}
	Log->trace('retrieve ' , $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '<', $cachedir.$sha1) {
		Log->error("could not open $main::DIRCACHE$sha1 for read");
		return;
	}
	binmode $fh;
	local $/;
	my $blob = <$fh>;
	close $fh;
	return \$blob;
}

#$sha1 -> %stats
sub stat {
	my ($sha1,$type) = @_;
	Log->trace('stats ' , $sha1);
	if ($type) {
		$STH_put->execute($sha1,$type);
		$DBH->commit();
	}
	else {
		$STH_get->execute($sha1);
		my $type = $STH_get->fetchrow_array();
		substr($sha1,2,0) = '/';
		my($size,$mtime) = (stat $cachedir.$sha1)[7,9];
		return ('modified'=>$mtime,'size'=>$size,'type'=>$type);
	}
}

1;
