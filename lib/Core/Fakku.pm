#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Fakku v1.2.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of comic
sub _create_list {
	my ($pkg,$state) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $url = ref $state ? $state->[0] : 'http://www.fakku.net/manga.php?select=english';
	
	my $tree = DlUtil::get_tree($url) or return;
	foreach my $main ($$tree->look_down('_tag' => 'div', 'class' => 'content_row')) {
		my $a = $main->look_down('_tag'=> 'div', 'class' => 'manga_row1')->look_down('_tag' => 'a');
		my $href = $a->attr('href');
		my $name = HTML::Entities::encode($a->as_trimmed_text());
		my ($num) = ($href =~ m'id=(\d+)'i);
		unless ($num) {
			$l->debug("could not parse $href");
			next;
		}
		my $id = 'Fakku_' . $num;
		$clist{$id} = {url_start => "001:$num" , name => $name}; #url start is start state
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
	my $next = $$tree->look_down('_tag' => 'div', 'id' => 'pagination')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^\s*>\s*$/});
	$url = $next ? [URI->new_abs($next->attr('href'),$url)->as_string] : undef;
	#$tree->delete();
		
	return (\%clist, $url);
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name Series Scanlator Artist Stats Date);
}



package Core::Fakku::Spot;

use parent -norequire, 'Core::Template::Spot';

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	$l->trace('mount ' . $s->{id});
	my ($pos,$id,$max,$section,$folder) = split(':',$s->{state});
	unless ($max) {
		($max,$section,$folder) = $s->_mount_parse($id);
		unless ($max) {
			$l->warn('could not parse page');
			$s->{fail} = 'could not parse page';
			return;
		}
	}
	if ($pos > $max) {
		$s->{fail} = 'last page';
		return;
	}
	$s->{page_url} = 'http://www.fakku.net/viewonline.php?id='.$id . '#page='.$pos;
	$s->{src} = 'http://c.fakku.net/manga/' . $section . '/' . $folder . '/images/'. $pos . '.jpg';
	$pos++;
	$pos = ('0' x (3-length($pos))) . $pos; #leading zeroes
	$s->{next} = join(':', $pos,$id,$max,$section,$folder);
	$s->{fail} = undef;
	return 1;
}


#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$id) = @_;
	
	my $url = 'http://www.fakku.net/viewonline.php?id='.$id;
	my $page = DlUtil::get($url);
	if (!$page->is_success() or !$page->header('Content-Length')) {
		$l->error("error get: ", $url);
		return;
	}
	my $content = $page->decoded_content();
	
	#say $content;

	if ($content =~ /"section":"(\w+)".*?"folder":"(\w+)".*?"thumbs":\[(.*?)\]\}\;/m) {
		my $section = $1;
		my $folder = $2;
		my $thumbs = $3;
		$thumbs = ($thumbs =~ tr/,//)+1;
		return $thumbs,$section,$folder;
	}
	return;
}

1;
