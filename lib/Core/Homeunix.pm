#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Homeunix v1.0.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $tree = $pkg->_get_tree('http://unixmanga.com/onlinereading/manga-lists.html') or return undef;
	foreach my $tr ($tree->look_down('_tag' => 'tr', 'class' => qr/^snF sn(Even|Odd)$/)) {
		my $a = $tr->look_down(_tag=>'a');
		my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
		$name =~ s/\s*complete\s*$//i;
		my $href = $a->attr('href');
		my ($id,$complete) = ($href =~ m'onlinereading/(.*?)(_Complete)?\.html$'i);
		unless ($id) {
			$l->debug("could not parse $href");
			next;
		}
		$id =~ s/\W/_/g;
		$id = 'Homeunix_' . $id;
		$clist{$id} = {url_info => $href, name => $name};
		$clist{$id}->{Status} = $complete ? 'complete' : 'ongoing';
		$clist{$id}->{Date} = $tr->look_down(align => 'center')->as_trimmed_text();
	}
	$tree->delete();
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Date);
}

#fetches comic info
sub _fetch_info {
	my ($s) = @_;
	my $url = $s->clist()->{url_info};
	while ($url) {
		my $tree = $s->_get_tree($url) or return undef;
		my $fs = $tree->look_down(_tag => 'fieldset', class=>qr'td2');
		if ($fs) {
			#we are at the last page and can finally find the first page
			my $a = $fs->look_down(_tag=>'a');
			$s->clist()->{url_start} = $a->attr('href');
			$url = undef;
		}
		else {
			my @chlist = $tree->look_down(_tag=>'tr',class => qr/^snF sn(Even|Odd)$/);
			my $ch = $chlist[-1];
			my $a = $ch->look_down(_tag=>'a');
			$url = $a->attr('href');
			
		}
		$tree->delete();
	}
}


package Core::Homeunix::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	#we need to use a custom regex because the page 
	#prints itself with javascript
	my $page = $tree->as_HTML();
	$page =~ m'<a (?:class="ne" )?href ="([^"]+)"><b>\[NEXT(?: CHAPTER)?\]</b></a>';
	my $href = HTML::Entities::encode($1);
	$href = URI->new_abs($href,$s->{page_url})->as_string();
	$page =~ m'<IMG ALT=" " STYLE="border: solid 1px #262626" SRC="([^"]+)" >';
	my $src = HTML::Entities::encode($1);
	unless ($src) {
		$l->error('could not parse page');
		return undef;
	}
	$s->{src} = $src;
	$s->{next} = $href;
	return 1;
}

1;
