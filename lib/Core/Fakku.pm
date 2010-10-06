#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Fakku;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of comic
sub _create_list {
	my %clist,
	$l->trace('create list of known collections');
	my $url = 'http://www.fakku.net/manga.php?select=english';
	while($url) {
		my $page = DlUtil::get($url);
		if ($page->is_error()) {
			$l->error($url);
			return undef;
		}
		$l->trace('parse HTML');
		my $tree = HTML::TreeBuilder->new();
		$tree->parse_content($page->decoded_content());
		foreach my $main ($tree->look_down('_tag' => 'div', 'class' => 'content_row')) {
			my $name = HTML::Entities::encode($main->attr('title'));
			my $href = $main->look_down('_tag'=> 'div', 'class' => 'manga_row1')->look_down('_tag' => 'a')->attr('href');
			my ($id) = ($href =~ m'id=(\d+)'i);
			unless ($id) {
				$l->debug("could not parse $href");
				next;
			}
			$href = "http://www.fakku.net/viewonline.php?id=$id&page=1";
			$id =~ s/\W/_/g;
			$id = 'Fakku_' . $id;
			$clist{$id} = {url_start => $href, name => $name};
			$clist{$id}->{series} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			my $trans_link = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'span',class => 'english')->look_down(_tag => 'a');
			$clist{$id}->{translator} = $trans_link->as_trimmed_text(extra_chars => '\xA0') if $trans_link;
			$clist{$id}->{artist} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row3')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{stats} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_left')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{date} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_right')->look_down(_tag=>'b')->as_trimmed_text(extra_chars => '\xA0');
			my $desc = $main->look_down('_tag'=> 'div', 'class' => 'tags')->as_trimmed_text(extra_chars => '\xA0');
			$desc =~ s/^Description://i;
			$clist{$id}->{description} = $desc unless ($desc =~ m/No description has been written/i);
			
		}
		my $next = $tree->look_down('_tag' => 'div', 'id' => 'pagination')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^\s*>\s*$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url) : undef;
		$tree->delete();
	}
	return \%clist;
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name series translator artist stats date description);
}



package Core::Fakku::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'div', id=>'content')->look_down(_tag=>'img');
	unless ($img) {
		$l->error('could not get image');
		$s->{fail} = 'could not get image';
		return undef;
	}
	map {$s->{$_} = $img->attr($_)} qw( src title alt width height);
	$s->{src} = URI->new_abs($s->{src},$s->{state});
	my $a = $tree->look_down(_tag => 'div', class=>'next_right_nav')->look_down(_tag=>'a');
	if ($a) {
		$s->{next} = $a->attr('href');
		$s->{next} = URI->new_abs($s->{next} ,$s->{state});
	}
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$'i) ;
	return 1;
}

1;
