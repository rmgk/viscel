#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Mangafox v1.1.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my ($pkg,$state) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $url = ref $state ? $state->[0] : 'http://www.mangafox.com/directory/all/1.htm';
	my $tree = $pkg->_get_tree($url) or return undef;
	my $table = $tree->look_down(_tag => 'table', id => 'listing');
	foreach my $tr ($table->look_down('_tag' => 'tr')) {
		my @td = $tr->look_down(_tag => 'td');
		next unless @td;
		my $a = $td[0]->look_down(_tag => 'a');
		my $href = $a->attr('href');
		my $status = $a->attr('class');
		my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
		my $rating = $td[1]->look_down(_tag => 'img')->attr('alt');
		my $chapters = $td[3]->as_trimmed_text();
		
		my ($id) = ($href =~ m'^/manga/(.*)/$'i);
		unless ($id) {
			$l->debug("could not parse $href");
			next;
		}
		$href = URI->new_abs($href,$url)->as_string;
		$id =~ s/\W/_/g;
		$id = 'Mangafox_' . $id;
		$clist{$id} = {url_info => $href, name => $name }; #url_start => $href . 'v01/c001/'};
		$clist{$id}->{Status} = ($status eq 'manga_close') ? 'complete' : 'ongoing';
		$clist{$id}->{Chapter} = $chapters;
		$clist{$id}->{Rating} = $rating;
		
	}
	my $next = $tree->look_down('_tag' => 'span', class => 'next')->parent();
	if ($next and $next->attr('_tag') eq 'a') {
		$url = [URI->new_abs($next->attr('href'),$url)->as_string];
	}
	else {
		$url = undef;
	}
	$tree->delete();
	
	return (\%clist,$url);
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Rating Artist Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s) = @_;
	my $url = $s->clist()->{url_info};
	$url .= '?no_warning=1';
	my $tree = $s->_get_tree($url) or return undef;
	my @chapter = $tree->look_down(_tag=>'a',class=>'chico');
	if (@chapter) {
		my $url_start = $chapter[-1]->attr('href');
		$s->clist()->{url_start} = URI->new_abs($url_start,$url)->as_string;
	}
	else {
		$l->warn('mangafox no longer makes this collection available');
		$s->clist()->{url_start} = undef;
		$s->clist()->{Status} = 'down';
	}
	
	my $info = $tree->look_down(id => 'information');
	my $detail = $info->look_down(_tag => 'p');
	$s->clist()->{Detail} = HTML::Entities::encode($detail->as_trimmed_text()) if ($detail);
	my $alias = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Alternative Name'} )->parent()->look_down(_tag => 'td');
	$s->clist()->{Alias} = HTML::Entities::encode($alias->as_text());
	if (@chapter) {
		my $status = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Status'} )->parent()->look_down(_tag => 'td');
		$s->clist()->{Status} = HTML::Entities::encode($status->as_text());
	}
	
	my $author = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Author(s)'} )->parent()->look_down(_tag => 'td');
	my $artist = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Artist(s)'} )->parent()->look_down(_tag => 'td');
	$s->clist()->{Artist} = join ', ' , map {HTML::Entities::encode($_->as_text)} ($author->look_down(_tag => 'a'),$artist->look_down(_tag => 'a'));
	
	my $tags = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Genre(s)'} )->parent()->look_down(_tag => 'td');
	$s->clist()->{Tags} = join ', ' , map {HTML::Entities::encode($_->as_text)} $tags->look_down(_tag => 'a');
	
	$tree->delete();
	return 1;
}


package Core::Mangafox::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', id => 'image');
	unless ($img) {
		$l->error('could not parse page');
		return undef;
	}
	map {$s->{$_} = $img->attr($_)} qw( src alt);
	
	my $next = $img->parent()->attr('href');
	if ($next eq 'javascript:void(0);') {
		my $url_info = $s->{page_url};
		$url_info =~ s'/[^/]+/[^/]+/[^/]+$'/';
		my $tree = Core::Mangafox->_get_tree($url_info) or return undef;
		my $url = $s->{page_url};
		my @chapter = reverse $tree->look_down(_tag=>'a',class=>'chico');
		for (0..($#chapter - 1)) {
			my $href = $chapter[$_]->attr('href');
			if ($url =~ m/\Q$href\E/i) {
				$next = $chapter[$_+1]->attr('href');
				$s->{next} = URI->new_abs($next,$url)->as_string();
				return 1;
			}
		}
		return undef;
	}
	$s->{next} = URI->new_abs($next,$s->{page_url})->as_string();
	return 1;
}

1;
