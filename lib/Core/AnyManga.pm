#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::AnyManga;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my %mangalist;
	$l->trace('create list of known collections');
	my $page = DlUtil::get('http://www.anymanga.com/directory/all/');
	if ($page->is_error()) {
		$l->error('error get http://www.anymanga.com/directory/all/');
		return undef;
	}
	$l->trace('parse HTML');
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->decoded_content());
	foreach my $list ($tree->look_down('_tag' => 'ul', 'class' => 'mainmangalist')) {
		foreach my $item ($list->look_down('_tag'=>'li')) {
			my $a = $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->look_down('_tag'=>'a');
			my $href = $a->attr('href');
			my $name = $a->as_trimmed_text(extra_chars => '\xA0');
			my ($id) = ($href =~ m'^/(.*)/$');
			$id =~ s/\W/_/g;
			$id = 'AnyManga_' . $id;
			$href = 'http://www.anymanga.com' . $href .'001/001/';
			if ($mangalist{$id}) { #its an alias
				if ($mangalist{$id}->{alias}) {
					$mangalist{$id}->{alias} .= ', '.$name;
				}
				else {
					$mangalist{$id}->{alias} = $name;
				}
				next;
			}
			$mangalist{$id} = {url_start => $href, name => $name};
			$mangalist{$id}->{status} = $item->look_down('_tag' => 'span', title => 'Manga Complete') ? 'complete' : 'ongoing' ;
			$mangalist{$id}->{author} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->content_list();
			$mangalist{$id}->{author} =~ s/^\s*by\s*//;
			$mangalist{$id}->{tags} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/normal/)->content_list();
		}
	}
	$tree->delete();
	return \%mangalist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name alias tags author info scans update status);
}

#fetches more information about the comic
sub fetch_info {
	my ($s) = @_;
	return undef if $s->clist()->{moreinfo};
	$l->trace('fetching more info for ', $s->{id});
	my $url = $s->clist()->{url_start};
	$url =~ s'\d+/\d+/$'';
	my $page = DlUtil::get($url);
	if ($page->is_error()) {
		$l->error($url);
		return undef;
	}
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->decoded_content());
	$s->clist()->{tags} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Categories:' })->parent()->content_list())[1];
	$s->clist()->{info} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Info:' })->parent()->content_list())[1];
	$s->clist()->{scans} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Manga scans by:' })->parent()->content_list())[1];
	#$s->clist()->{update} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Last Manga Update:' })->parent()->content_list())[1];
	$s->clist()->{status} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Status:' })->parent()->content_list())[1];
	$s->clist()->{review} = ($tree->look_down('_tag' => 'div', style => qr/font-weight: bolder;$/)->parent()->content_list())[1];
	($s->clist()->{seealso}) = ($tree->look_down('_tag' => 'span', style => 'font-weight: bolder;')->look_down('_tag'=> 'a')->attr('href') =~ m'^/(.*)/$');
	$s->clist()->{seealso} =~ s/\W/_/g;
	$s->clist()->{moreinfo} = 1;
	return $s->save_clist();
}



package Core::AnyManga::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', title => qr'Click to view next page or press next or back buttons'i);
	map {$s->{$_} = $img->attr($_)} qw( src title alt );
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = 'http://www.anymanga.com' . $a_next->attr('href');
	}
	my $chap = $tree->look_down(_tag => 'title')->as_text();
	$chap =~ m/Manga Online, Vol. (\d+) \((.*), .*\)/;
	$s->{chapter} = $1 .' '. $2;
	$s->{src} = 'http://www.anymanga.com' . $s->{src};
	$s->{title} =~ s/\)\s*\[.*$/)/s;
	$s->{alt} =~ s/\)\s*\[.*$/)/s;
	$s->{page_url} = $s->{state};
	($s->{filename}) = ($s->{src} =~ m'/manga/[^/]+/(.*+)'i) ;
	$s->{filename} =~ s'/''g;
	return 1;
}

1;
