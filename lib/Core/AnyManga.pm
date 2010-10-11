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
	my ($pkg) = @_;
	my %mangalist;
	$l->trace('create list of known collections');
	my $tree = $pkg->_get_tree('http://www.anymanga.com/directory/all/') or return undef;
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
				if ($mangalist{$id}->{Alias}) {
					$mangalist{$id}->{Alias} .= ', '.$name;
				}
				else {
					$mangalist{$id}->{Alias} = $name;
				}
				next;
			}
			$mangalist{$id} = {url_start => $href, name => $name};
			$mangalist{$id}->{Status} = $item->look_down('_tag' => 'span', title => 'Manga Complete') ? 'complete' : 'ongoing' ;
			$mangalist{$id}->{Artist} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->content_list();
			$mangalist{$id}->{Artist} =~ s/^\s*by\s*//;
			$mangalist{$id}->{Tags} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/normal/)->content_list();
		}
	}
	$tree->delete();
	return \%mangalist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Alias Tags Artist Chapter Scanlator Status);
}

#fetches more information about the comic
sub fetch_info {
	my ($s) = @_;
	return undef if $s->clist()->{moreinfo};
	$l->trace('fetching more info for ', $s->{id});
	my $url = $s->clist()->{url_start};
	$url =~ s'\d+/\d+/$'';
	my $tree = $s->_get_tree($url) or return undef;
	$s->clist()->{Tags} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Categories:' })->parent()->content_list())[1];
	$s->clist()->{Chapter} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Info:' })->parent()->content_list())[1];
	$s->clist()->{Scanlator} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Manga scans by:' })->parent()->content_list())[1];
	#$s->clist()->{update} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Last Manga Update:' })->parent()->content_list())[1];
	$s->clist()->{Status} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Status:' })->parent()->content_list())[1];
	$s->clist()->{Detail} = ($tree->look_down('_tag' => 'div', style => qr/font-weight: bolder;$/)->parent()->content_list())[1];
	($s->clist()->{Seealso}) = ($tree->look_down('_tag' => 'span', style => 'font-weight: bolder;')->look_down('_tag'=> 'a')->attr('href') =~ m'^/(.*)/$');
	$s->clist()->{Seealso} =~ s/\W/_/g;
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
	#my $chap = $tree->look_down(_tag => 'title')->as_text();
	#$chap =~ m/Manga Online, Vol. (\d+.* \(.*), .*\)/;
	#$s->{chapter} = $1 .')';
	$s->{src} = 'http://www.anymanga.com' . $s->{src};
	$s->{title} =~ s/\)\s*\[.*$/)/s;
	$s->{alt} =~ s/\)\s*\[.*$/)/s;
	return 1;
}

1;
