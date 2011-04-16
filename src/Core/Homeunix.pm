#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Homeunix v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core::Template);
use Spot::Homeunix;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('create list of known collections');
	my $tree = DlUtil::get_tree('http://unixmanga.com/onlinereading/manga-lists.html') or return;
	foreach my $tr ($$tree->look_down('_tag' => 'tr', 'class' => qr/^snF sn(Even|Odd)$/)) {
		my $a = $tr->look_down(_tag=>'a');
		my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
		$name =~ s/\s*complete\s*$//i;
		my $href = $a->attr('href');
		my ($id,$complete) = ($href =~ m'onlinereading/(.*?)(_Complete)?\.html$'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		$id =~ s/\W/_/g;
		$id = 'Homeunix_' . $id;
		$clist{$id} = {url_info => $href, name => $name};
		$clist{$id}->{Status} = $complete ? 'complete' : 'ongoing';
		$clist{$id}->{Date} = $tr->look_down(align => 'center')->as_trimmed_text();
	}
	#$tree->delete();
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Date);
}

#fetches comic info
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my %urls;
	while ($url) {
		if ($urls{$url}) { #abort recursion
			$cfg->{Status} = 'Down';
			return $cfg;
		}
		$urls{$url} = 1; 
		my $tree = DlUtil::get_tree($url);
		my $fs = $$tree->look_down(_tag => 'fieldset', class=>qr'td2');
		if ($fs) {
			#we are at the last page and can finally find the first page
			my $a = $fs->look_down(_tag=>'a');
			$cfg->{start} = $a->attr('href');
			$url = undef;
		}
		else {
			my @chlist = $$tree->look_down(_tag=>'tr',class => qr/^snF sn(Even|Odd)$/);
			my $ch = $chlist[-1];
			my $a = $ch->look_down(_tag=>'a');
			$url = $a->attr('href');
		}
	}
	return $cfg;
}

1;
