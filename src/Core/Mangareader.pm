#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Mangareader v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::Mangareader;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('fetch list of known remotes');
	my $url = 'http://www.mangareader.net/alphabetical';
	my $tree = DlUtil::get_tree($url);
	foreach my $list ($$tree->look_down(_tag => 'ul', class => 'series_alpha')) {
		foreach my $item ($list->look_down(_tag => 'li')) {
			my $a = $item->look_down(_tag => 'a');
			my $href = $a->attr('href');
			$href = URI->new_abs($href,$url)->as_string;
			my ($id) = ($href =~ m'/([\w-]*)(?:\.html)?$');
			$id =~ s/\W/_/g;
			$id = 'Mangareader_' . $id;
			my $name = $a->as_trimmed_text();
			$clist{$id} = { name => $name, url_info => $href};
			$clist{$id}->{Status} = $item->look_down(class => 'mangacompleted') ? 'complete' : 'ongoing';
		}
	}
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Artist Tags Chapter Alias);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my $tree = DlUtil::get_tree($url);
	my $properties = $$tree->look_down(_tag => 'div', id => 'mangaproperties');
	my @td = $properties->look_down(_tag => 'td');
	$cfg->{Alias} = HTML::Entities::encode($td[3]->as_trimmed_text());
	$cfg->{Status} = HTML::Entities::encode($td[7]->as_trimmed_text());
	$cfg->{Artist} = HTML::Entities::encode($td[9]->as_trimmed_text()) . ', '.
		HTML::Entities::encode($td[11]->as_trimmed_text());
	my @tags = $td[15]->look_down(class => 'genretags');
	$cfg->{Tags} = join ', ', map
		{ HTML::Entities::encode($_->as_trimmed_text()) }
			@tags;
	my $summary = $$tree->look_down(_tag => 'div', id => 'readmangasum')->look_down(_tag => 'p');
	$cfg->{Detail} = HTML::Entities::encode($summary->as_trimmed_text());
	my $list = $$tree->look_down(_tag => 'table', id => 'listing');
	my @chapter = $list->look_down(_tag => 'a');
	$cfg->{Chapter} = 0 + @chapter;
	my $start = $chapter[0]->attr('href');
	$start = URI->new_abs($start,$url)->as_string;
	$cfg->{start} = $start;
	return $cfg;
}

1;
