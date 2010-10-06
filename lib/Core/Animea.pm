#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Animea;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $url = 'http://manga.animea.net/browse.html?page=0';
	while($url) {
		my $tree = $pkg->_get_tree($url) or return undef;
		foreach my $td ($tree->look_down('_tag' => 'td', 'class' => 'c')) {
			my $tr = $td->parent();
			my $a = $tr->look_down(_tag=>'a',class=>qr/manga$/);
			my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
			my $href = $a->attr('href');
			my ($id) = ($href =~ m'^/(.*)\.html$'i);
			unless ($id) {
				$l->debug("could not parse $href");
				next;
			}
			$href = URI->new_abs("/$id-chapter-1-page-1.html",$url);
			$id =~ s/\W/_/g;
			$id = 'Animea_' . $id;
			$clist{$id} = {urlstart => $href, name => $name};
			$clist{$id}->{Status} = ($a->{class} eq 'complete_manga') ? 'complete' : 'ongoing';
			$clist{$id}->{Chapter} = $td->as_trimmed_text();
			
		}
		my $next = $tree->look_down('_tag' => 'ul', 'class' => 'paging')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^Next$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url) : undef;
		$tree->delete();
	}
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Tags);
}

#fetches more information about the comic
sub fetch_info {
	my ($s) = @_;
	return undef if $s->clist()->{moreinfo};
	$l->trace('fetching more info for ', $s->{id});
	my $url = $s->clist()->{urlstart};
	$url =~ s/-chapter-.*-page-1//;
	$url .= '?skip=1';
	my $tree = $s->_get_tree($url) or return undef;;
	$s->clist()->{Tags} = join ', ' , map {$_->as_trimmed_text} $tree->look_down(_tag=>'a', href=>qr'/genre/'i);
	my $p = $tree->look_down('_tag' => 'p', style => 'width:570px; padding:5px;');
	$s->clist()->{Review} = HTML::Entities::encode($p->as_trimmed_text()) if ($p);
	my $ul = $tree->look_down('_tag' => 'ul', class => 'relatedmanga');
	$s->clist()->{Seealso} = join ', ' , map { $_->attr('href') =~ m'animea.net/([^/]*)\.html$'; my $r = $1; $r =~ s/\W/_/g; $r} $ul->look_down(_tag=>'a');
	my @table = $tree->look_down(_tag=>'table',id=>'chapterslist')->content_list();
	$s->clist()->{urlstart} = $table[-2]->look_down(_tag=>'a')->attr('href');
	$s->clist()->{urlstart} =~ s/\.html$/-page-1\.html/;
	$s->clist()->{moreinfo} = 1;
	return $s->save_clist();
}


package Core::Animea::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', class=>'chapter_img');
	map {$s->{$_} = $img->attr($_)} qw( src width height);
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = $a_next->attr('href');
	}
	$s->{next} =~ /-chapter-(\d+)-page-/;
	$s->{chapter} = $1;
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$');
	return 1;
}

1;
