#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Cache v1.3.0;

use 5.012;
use warnings;
use utf8;
use autodie;

use Log;

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

#$sha1 -> $bool
#removes $sha1 returns success
sub remove {
	my ($sha1) = @_;
	Log->warn('remove ', $sha1);
	substr($sha1,2,0) = '/';
	return unlink $cachedir.$sha1;
}

#->@all_shas
#returns a list of all sha1 hashes 
sub list {
	my @hashes;
	for my $a ('0'..'9','a'..'f') { for my $b ('0'..'9','a'..'f') { 
		my $dir = $cachedir.$a.$b.'/';
		opendir(my $dh, $dir);
		push @hashes, map { $a.$b.$_ } grep /^[\da-f]{38}$/, readdir $dh;
		closedir $dh;
	}}
	return @hashes;
}

#$sha1 -> %stats
sub stat {
	my ($sha1,$type) = @_;
	Log->trace('stats ' , $sha1);
	substr($sha1,2,0) = '/';
	my($size,$mtime) = (stat $cachedir.$sha1)[7,9];
	return ('modified'=>$mtime,'size'=>$size);
}

1;
