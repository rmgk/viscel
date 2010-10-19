#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Fakku v1.0.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of comic
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $url = 'http://www.fakku.net/manga.php?select=english';
	while($url) {
		my $tree = $pkg->_get_tree($url) or return undef;
		foreach my $main ($tree->look_down('_tag' => 'div', 'class' => 'content_row')) {
			my $a = $main->look_down('_tag'=> 'div', 'class' => 'manga_row1')->look_down('_tag' => 'a');
			my $href = $a->attr('href');
			my $name = HTML::Entities::encode($a->as_trimmed_text());
			my ($id) = ($href =~ m'id=(\d+)'i);
			unless ($id) {
				$l->debug("could not parse $href");
				next;
			}
			$href = "http://www.fakku.net/viewonline.php?id=$id&page=1";
			$id =~ s/\W/_/g;
			$id = 'Fakku_' . $id;
			$clist{$id} = {url_start => $href, name => $name};
			$clist{$id}->{Series} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			my $trans_link = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'span',class => 'english')->look_down(_tag => 'a');
			$clist{$id}->{Scanlator} = $trans_link->as_trimmed_text(extra_chars => '\xA0') if $trans_link;
			$clist{$id}->{Artist} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row3')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{Stats} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_left')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{Date} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_right')->look_down(_tag=>'b')->as_trimmed_text(extra_chars => '\xA0');
			my $desc = $main->look_down('_tag'=> 'div', 'class' => 'tags')->as_trimmed_text(extra_chars => '\xA0');
			$desc =~ s/^Description://i;
			$clist{$id}->{Detail} = $desc unless ($desc =~ m/No description has been written/i);
			$clist{$_} = HTML::Entities::encode $clist{$_} for grep {$clist{$_}} qw(Series Scanlator Artist Stats Date Detail);
		}
		my $next = $tree->look_down('_tag' => 'div', 'id' => 'pagination')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^\s*>\s*$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url)->as_string : undef;
		$tree->delete();
	}
	return \%clist;
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name Series Scanlator Artist Stats Date);
}



package Core::Fakku::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'div', id=>'content')->look_down(_tag=>'img',src => qr'fakku.net');
	unless ($img) {
		$l->error('could not get image');
		$s->{fail} = 'could not get image';
		return undef;
	}
	map {$s->{$_} = $img->attr($_)} qw( src title alt width height);
	$s->{src} = URI->new_abs($s->{src},$s->{state})->as_string;
	my $a = $img->look_up(_tag=>'a');
	if ($a) {
		$s->{next} = $a->attr('href');
		$s->{next} = URI->new_abs($s->{next} ,$s->{state})->as_string;
	}
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$'i) ;
	return 1;
}

1;
