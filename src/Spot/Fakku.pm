#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Fakku v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	Log->trace('mount ' . $s->{id});
	my ($pos,$id,$max,$section,$folder) = split(':',$s->{state});
	unless ($max) {
		($max,$section,$folder) = $s->_mount_parse($id);
		unless ($max) {
			Log->warn('could not parse page');
			$s->{fail} = 'could not parse page';
			die ['mount failed', $s]
		}
	}
	if ($pos > $max) {
		$s->{fail} = 'last page';
		die ['mount failed', $s, $pos, $max]
	}
	$s->{page_url} = 'http://www.fakku.net/viewonline.php?id='.$id . '#page='.$pos;
	$s->{src} = 'http://c.fakku.net/manga/' . $section . '/' . $folder . '/images/'. $pos . '.jpg';
	$pos++;
	$pos = ('0' x (3-length($pos))) . $pos; #leading zeroes
	$s->{next} = join(':', $pos,$id,$max,$section,$folder);
	$s->{fail} = undef;
	return 1;
}


#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$id) = @_;
	
	my $url = 'http://www.fakku.net/viewonline.php?id='.$id;
	my $page = DlUtil::get($url);
	if (!$page->is_success() or !$page->header('Content-Length')) {
		Log->error("error get: ", $url);
		return;
	}
	my $content = $page->decoded_content();
	
	#say $content;

	if ($content =~ /"section":"(\w+)"
						.*?
						"folder":"(\w+)"
						.*?
						"thumbs":
						\[(.*?)\],
						"full_page"
						/mx) {
		my $section = $1;
		my $folder = $2;
		my $thumbs = $3;
		$thumbs = ($thumbs =~ tr/,//)+1;
		return $thumbs,$section,$folder;
	}
	return;
}

1;
