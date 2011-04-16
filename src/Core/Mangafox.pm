#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Mangafox v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core::Template);
use Spot::Mangafox;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg,$state) = @_;
	my %clist;
	my $url = $state ? $state : 'http://www.mangafox.com/directory/all/1.htm';
	Log->trace('fetch list of known remotes ', $url);
	my $tree = DlUtil::get_tree($url) or return;
	my $table = $$tree->look_down(_tag => 'table', id => 'listing');
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
			Log->debug("could not parse $href");
			next;
		}
		$href = URI->new_abs($href,$url)->as_string;
		$id =~ s/\W/_/g;
		$id = 'Mangafox_' . $id;
		$clist{$id} = {url_info => $href, name => $name }; #start => $href . 'v01/c001/'};
		$clist{$id}->{Status} = ($status eq 'manga_close') ? 'complete' : 'ongoing';
		$clist{$id}->{Chapter} = $chapters;
		$clist{$id}->{Rating} = $rating;
		
	}
	my $next = $$tree->look_down('_tag' => 'span', class => 'next')->parent();
	if ($next and $next->attr('_tag') eq 'a') {
		$url = URI->new_abs($next->attr('href'),$url)->as_string;
	}
	else {
		$url = undef;
	}
	return (\%clist,$url);
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Rating Artist Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	$url .= '?no_warning=1';
	my $tree = DlUtil::get_tree($url);
	my @chapter = $$tree->look_down(_tag=>'a',class=>'chico');
	if (@chapter) {
		my $start = $chapter[-1]->attr('href');
		$cfg->{start} = URI->new_abs($start,$url)->as_string;
	}
	else {
		Log->warn($s->{id} . ' is no longer available');
		$cfg->{start} = undef;
		$cfg->{Status} = 'down';
	}
	
	my $info = $$tree->look_down(id => 'information');
	my $detail = $info->look_down(_tag => 'p');
	$cfg->{Detail} = HTML::Entities::encode($detail->as_trimmed_text()) if ($detail);
	my $alias = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Alternative Name'} )->parent()->look_down(_tag => 'td');
	$cfg->{Alias} = HTML::Entities::encode($alias->as_text());
	if (@chapter) {
		my $status = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Status'} )->parent()->look_down(_tag => 'td');
		$cfg->{Status} = HTML::Entities::encode($status->as_text());
	}
	
	my $author = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Author(s)'} )->parent()->look_down(_tag => 'td');
	my $artist = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Artist(s)'} )->parent()->look_down(_tag => 'td');
	$cfg->{Artist} = join ', ' , map {HTML::Entities::encode($_->as_text)} ($author->look_down(_tag => 'a'),$artist->look_down(_tag => 'a'));
	
	my $tags = $info->look_down(_tag => 'th' , sub { $_[0]->as_text() eq 'Genre(s)'} )->parent()->look_down(_tag => 'td');
	$cfg->{Tags} = join ', ' , map {HTML::Entities::encode($_->as_text)} $tags->look_down(_tag => 'a');
	
	return $cfg;
}

1;
