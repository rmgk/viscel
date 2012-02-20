#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Mangafox v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::Mangafox;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg,$state) = @_;
	my %clist;
	my $url = $state ? $state : 'http://www.mangafox.com/manga/';
	Log->trace('fetch list of known remotes ', $url);
	my $tree = DlUtil::get_tree($url) or return;
	foreach my $a ($$tree->look_down('_tag' => 'a', class => qr'series_preview\s+manga_(open|close)')) {
		my $href = $a->attr('href');
		my $status = $a->attr('class');
		my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
		
		my ($id) = ($href =~ m'/manga/(.*)/$'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		$href = URI->new_abs($href,$url)->as_string;
		$id =~ s/\W/_/g;
		$id = 'Mangafox_' . $id;
		$clist{$id} = {url_info => $href, name => $name }; #start => $href . 'v01/c001/'};
		$clist{$id}->{Status} = ($status eq 'manga_close') ? 'complete' : 'ongoing';
		
	}
	return (\%clist,undef);
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Rating Artist Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my $tree = DlUtil::get_tree($url);
	my @chapter = $$tree->look_down(_tag=>'a',class=>'tips');
	if (@chapter) {
		my $start = $chapter[-1]->attr('href');
		$cfg->{start} = URI->new_abs($start,$url)->as_string;
	}
	else {
		Log->warn($s->{id} . ' is no longer available');
		$cfg->{start} = undef;
	}
	
	$cfg->{Chapter} = scalar @chapter;
	
	my $info = $$tree->look_down(id => 'title');
	my $detail = $info->look_down(class => qr'summary');
	$cfg->{Detail} = HTML::Entities::encode($detail->as_trimmed_text()) if ($detail);
	my $alias = $info->look_down(_tag => 'h3');
	$cfg->{Alias} = HTML::Entities::encode($alias->as_trimmed_text()) if $alias;
	
	my @tds = ($info->look_down(_tag => 'tr'))[1]->look_down(_tag => 'td');
	my $author = $tds[1];
	my $artist = $tds[2];
	$cfg->{Artist} = join ', ' , map {HTML::Entities::encode($_->as_text)} ($author->look_down(_tag => 'a'),$artist->look_down(_tag => 'a'));
	
	my $tags = $tds[3];
	$cfg->{Tags} = join ', ' , map {HTML::Entities::encode($_->as_text)} $tags->look_down(_tag => 'a');
	
	my @data = $$tree->look_down(class => 'data');
	my $status = $data[0]->look_down(_tag => 'span');
	$status = HTML::Entities::encode(($status->content_list())[0]);
	$status =~ s/,//;
	$cfg->{Status} = @chapter ? $status : 'down';
	my $rating = $data[2]->look_down(_tag => 'span');
	$cfg->{Rating} = HTML::Entities::encode($rating->as_trimmed_text());

	
	return $cfg;
}

1;
