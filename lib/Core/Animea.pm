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
	my %clist;
	$l->trace('create list of known collections');
	my $url = 'http://manga.animea.net/browse.html?page=0';
	while($url) {
		my $page = DlUtil::get($url);
		if (!$page->is_success()) {
			$l->error($url);
			return undef;
		}
		$l->trace('parse HTML');
		my $tree = HTML::TreeBuilder->new();
		$tree->parse_content($page->decoded_content());
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
			$clist{$id} = {url_start => $href, name => $name};
			$clist{$id}->{status} = ($a->{class} eq 'complete_manga') ? 'complete' : 'ongoing';
			$clist{$id}->{chapter} = $td->as_trimmed_text();
			
		}
		my $next = $tree->look_down('_tag' => 'ul', 'class' => 'paging')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^Next$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url) : undef;
		$tree->delete();
	}
	return \%clist;
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
	$url =~ s/-chapter-.*-page-1//;
	$url .= '?skip=1';
	my $page = DlUtil::get($url);
	if (!$page->is_success()) {
		$l->error($url);
		return undef;
	}
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->decoded_content());
	$s->clist()->{tags} = join ', ' , map {$_->as_trimmed_text} $tree->look_down(_tag=>'a', href=>qr'/genre/'i);
	my $p = $tree->look_down('_tag' => 'p', style => 'width:570px; padding:5px;');
	$s->clist()->{review} = HTML::Entities::encode($p->as_trimmed_text()) if ($p);
	my $ul = $tree->look_down('_tag' => 'ul', class => 'relatedmanga');
	$s->clist()->{seealso} = join ', ' , map { $_->attr('href') =~ m'animea.net/([^/]*)\.html$'; my $r = $1; $r =~ s/\W/_/g; $r} $ul->look_down(_tag=>'a');
	my @table = $tree->look_down(_tag=>'table',id=>'chapterslist')->content_list();
	$s->clist()->{url_start} = $table[-2]->look_down(_tag=>'a')->attr('href');
	$s->clist()->{url_start} =~ s/\.html$/-page-1\.html/;
	$s->clist()->{moreinfo} = 1;
	return $s->save_clist();
}


package Core::Animea::Spot;

my $SHA = Digest::SHA->new();

#$class, \%self -> \%self
#creates a new collection instance of $id at position $pos
sub new {
	my ($class,$self) = @_;
	$l->trace('new ',$class,' instance');
	$self->{fail} = 'not mounted';
	bless $self, $class;
	return $self;
}

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	$l->trace('mount ' . $s->{id} .' '. $s->{state});
	my $page = DlUtil::get($s->{state});
	if (!$page->is_success()) {
		$l->error('error get ' . $s->{state});
		$s->{fail} = 'could not get page';
		return undef;
	}
	$l->trace('parse page');
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->decoded_content());
	my $img = $tree->look_down(_tag => 'img', class=>'chapter_img');
	map {$s->{$_} = $img->attr($_)} qw( src width height);
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = $a_next->attr('href');
	}
	$s->{next} =~ /-chapter-(\d+)-page-/;
	$s->{chapter} = $1;
	$s->{fail} = undef;
	$l->trace(join "\n\t\t\t\t", map {"$_: " .($s->{$_}//'')} qw(src next)); #/padre display bug
	$tree->delete();
	return 1;
}

#-> \%entity
#returns the entity
sub fetch {
	my ($s) = @_;
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	$l->trace('fetch object');

	my $file = DlUtil::get($s->{src},$s->{state});
	if (!$file->is_success()) {
		$l->error('error get ' . $s->{src});
		$s->{fail} = 'could not fetch object';
		return undef;
	}
	my $blob = $file->content();

	$s->{type} = $file->header('Content-Type');
	$s->{sha1} = $SHA->add($blob)->hexdigest();

	return \$blob;
}

#-> \%entity
#returns the entity
sub entity {
	my ($s) = @_;
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	my $object = {};
	($object->{filename}) = ($s->{src} =~ m'/([^/]+)$');
	$object->{page_url} = $s->{state};
	$object->{cid} = $s->{id};
	$object->{$_} = $s->{$_} for qw(type sha1 src position state width height chapter);
	return Entity->new($object);
}

#returns the next spot
sub next {
	my ($s) = @_;
	$l->trace('create next');
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	unless ($s->{next}) {
		$l->error('no next was found');
		return undef;
	}
	my $next = {id => $s->{id}, position => $s->{position} + 1, state => $s->{next} };
	$next = ref($s)->new($next);
	return $next;
}

#accessors:
sub id { return $_[0]->{id} }
sub position { return $_[0]->{position} }


1;
