#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Animea v1.0.0;

use 5.012;
use warnings;
use lib "..";

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
			$href = URI->new_abs($href,$url)->as_string;
			$id =~ s/\W/_/g;
			$id = 'Animea_' . $id;
			$clist{$id} = {url_info => $href, name => $name};
			$clist{$id}->{Status} = ($a->{class} eq 'complete_manga') ? 'complete' : 'ongoing';
			$clist{$id}->{Chapter} = $td->as_trimmed_text();
			
		}
		my $next = $tree->look_down('_tag' => 'ul', 'class' => 'paging')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^Next$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url)->as_string : undef;
		$tree->delete();
	}
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s) = @_;
	my $url = $s->clist()->{url_info};
	$url .= '?skip=1';
	my $tree = $s->_get_tree($url) or return undef;
	$s->clist()->{Tags} = join ', ' , map {$_->as_trimmed_text} $tree->look_down(_tag=>'a', href=>qr'/genre/'i);
	my $p = $tree->look_down('_tag' => 'p', style => 'width:570px; padding:5px;');
	$s->clist()->{Detail} = HTML::Entities::encode($p->as_trimmed_text()) if ($p);
	my $ul = $tree->look_down('_tag' => 'ul', class => 'relatedmanga');
	$s->clist()->{Seealso} = join ', ' , map { $_->attr('href') =~ m'animea.net/([^/]*)\.html$'; my $r = $1; $r =~ s/\W/_/g; $r} $ul->look_down(_tag=>'a');
	my @table = $tree->look_down(_tag=>'table',id=>'chapterslist')->content_list();
	my $a = $table[-2]->look_down(_tag=>'a');
	if ($a) {
		$s->clist()->{url_start} = $a->attr('href');
		$s->clist()->{url_start} =~ s/\.html$/-page-1\.html/;
	}
	else {
		$l->warn("animea no longer makes this collection available");
		$s->clist()->{Status} = 'down';
	}
}


package Core::Animea::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', class=>'chapter_img');
	unless ($img) {
		$l->error('could not parse page');
		return undef;
	}
	map {$s->{$_} = $img->attr($_)} qw( src width height);
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = $a_next->attr('href');
	}
	return 1;
}

1;
