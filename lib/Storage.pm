#!perl
#this program is free software it may be redistributed under the same terms as perl itself
package Storage;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;

my $l = Log->new();

our $DIR = 'store/';


#initialises the storage
sub init {
	$l->trace('initialising storage');
	unless (-e $DIR or mkdir $DIR) {
		$l->error('could not create storage dir ' .$DIR);
		return undef;
	}
	
	for my $a ('0'..'9','a'..'f') { for my $b ('0'..'9','a'..'f') { 
		my $dir = $DIR.$a.$b.'/';
		unless (-e $dir or mkdir $dir) {
			$l->error('could not create storage dir ' .$dir);
			return undef;
		}
	}}

	return 1;
}

#$sha1,\$blob -> $bool
#stores the blob true if successful false if not
sub put {
	my ($sha1,$blob) = @_;
	$l->trace('store ' . $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '>', $DIR.$sha1) {
		$l->error("could not open $DIR$sha1 for write");
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
	$l->trace('retrieve ' . $sha1);
	substr($sha1,2,0) = '/';
	my $fh;
	unless (open $fh, '<', $DIR.$sha1) {
		$l->error("could not open $DIR.$sha1 for write");
		return undef;
	}
	binmode $fh;
	local $/;
	my $blob = <$fh>;
	close $fh;
	return \$blob;
}

1;
