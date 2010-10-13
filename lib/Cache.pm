#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cache;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;

my $l = Log->new();
my $DBH;
my $STH_get;
my $STH_put;

#initialises the cache
sub init {
	$l->trace('initialise cache');
	unless (-e $main::DIRCACHE or mkdir $main::DIRCACHE) {
		$l->error('could not create cache dir ' , $main::DIRCACHE);
		return undef;
	}
	
	for my $a ('0'..'9','a'..'f') { for my $b ('0'..'9','a'..'f') { 
		my $dir = $main::DIRCACHE.$a.$b.'/';
		unless (-e $dir or mkdir $dir) {
			$l->error('could not create storage dir ' .$dir);
			return undef;
		}
	}}
	
	$DBH = DBI->connect("dbi:SQLite:dbname=".$main::DIRCACHE.'stats.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	return undef unless $DBH;
	unless ($DBH->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name=?",undef,"type")) {
		unless($DBH->do('CREATE TABLE type (sha1 CHAR UNIQUE, value)')) {
			$l->error('could not create table type');
			return undef;
		}
		$DBH->commit();
	}
	
	$STH_get = $DBH->prepare('SELECT value FROM type WHERE sha1 = ?');
	$STH_put = $DBH->prepare('INSERT OR REPLACE INTO type (sha1,value) VALUES (?,?)');

	return 1;
}

#$sha1,\$blob -> $bool
#stores the blob true if successful false if not
sub put {
	my ($sha1,$blob) = @_;
	$l->trace('store ' . $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '>', $main::DIRCACHE.$sha1) {
		$l->error("could not open $main::DIRCACHE$sha1 for write");
		return undef;
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
	if (!$sha1 or length($sha1) != 40) {
		$l->error('incorrect sha1 value');
		return undef;
	}
	$l->trace('retrieve ' , $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '<', $main::DIRCACHE.$sha1) {
		$l->error("could not open $main::DIRCACHE$sha1 for read");
		return undef;
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
	$l->trace('stats ' , $sha1);
	if ($type) {
		$STH_put->execute($sha1,$type);
		$DBH->commit();
	}
	else {
		$STH_get->execute($sha1);
		my $type = $STH_get->fetchrow_array();
		substr($sha1,2,0) = '/';
		my($size,$mtime) = (stat $main::DIRCACHE.$sha1)[7,9];
		return ('modified'=>$mtime,'size'=>$size,'type'=>$type);
	}
}

1;
