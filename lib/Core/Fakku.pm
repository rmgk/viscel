#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Fakku;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Entity;
use HTML::Entities;
use DlUtil;
use HTML::TreeBuilder;
use Digest::SHA;
use URI;
use Data::Dumper;

my $l = Log->new();
my $SHA = Digest::SHA->new();
my %clist;

$Data::Dumper::Purity = 1; 
$Data::Dumper::Indent = 0;

#initialises the list of known comics
sub init {
	$l->trace('initialising');
	$l->warn('list already initialised, reinitialising') if %clist;
	return _create_list();
}

#creates the list of comic
sub _create_list {
	%clist = %{UserPrefs::parse_file('FakkuData')};
	if (keys %clist) {
		$l->debug('loaded ' . keys(%clist) . ' collections');
		return 1;
	}
	$l->trace('create list of known collections');
	my $url = 'http://www.fakku.net/manga.php?select=english';
	while($url) {
		my $page = DlUtil::get($url);
		if ($page->is_error()) {
			$l->error($url);
			return undef;
		}
		$l->trace('parsing HTML');
		my $tree = HTML::TreeBuilder->new();
		$tree->parse_content($page->content());
		foreach my $main ($tree->look_down('_tag' => 'div', 'class' => 'content_row')) {
			my $name = encode_entities($main->attr('title'));
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
			$clist{$id}->{Series} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			my $trans_link = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'span',class => 'english')->look_down(_tag => 'a');
			$clist{$id}->{Translator} = $trans_link->as_trimmed_text(extra_chars => '\xA0') if $trans_link;
			$clist{$id}->{Artist} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row3')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{stats} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_left')->as_trimmed_text(extra_chars => '\xA0');
			$clist{$id}->{date} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_right')->look_down(_tag=>'b')->as_trimmed_text(extra_chars => '\xA0');
			my $desc = $main->look_down('_tag'=> 'div', 'class' => 'tags')->as_trimmed_text(extra_chars => '\xA0');
			$desc =~ s/^Description://i;
			$clist{$id}->{Description} = $desc unless ($desc =~ m/No description has been written/i);
			
		}
		my $next = $tree->look_down('_tag' => 'div', 'id' => 'pagination')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^\s*>\s*$/});
		$url = $next ? URI->new_abs($next->attr('href'),$url) : undef;
		$tree->delete();
	}
	$l->debug('found ' . keys(%clist) . ' collections');
	$l->debug('saving list to file');
	return UserPrefs::save_file('FakkuData',\%clist);
}

#->\%collection_hash
#returns a hash containing all the collection ids as keys and their names and urls as values
sub list {
	return map {$_ , $clist{$_}->{name}} keys %clist;
}

#$query,$regex -> %list
sub search {
	my ($pgk,@re) = @_;
	$l->debug('searching');
	return map {$_,$clist{$_}->{name}} grep {
		my $id = $_;
		@re == grep {
			substr($id,0,13) ~~ $_ or 
			(defined $clist{$id}->{Series} and $clist{$id}->{Series} ~~ $_) or 
			(defined $clist{$id}->{Translator} and $clist{$id}->{Translator} ~~ $_) or 
			(defined $clist{$id}->{Artist} and $clist{$id}->{Artist} ~~ $_) or 
			(defined $clist{$id}->{stats} and $clist{$id}->{stats} ~~ $_) or 
			(defined $clist{$id}->{date} and $clist{$id}->{date} ~~ $_) or 
			(defined $clist{$id}->{Description} and $clist{$id}->{Description} ~~ $_) or 
			$clist{$id}->{name} ~~ $_
			} @re
		} keys %clist;
}

#pkg, \%config -> \%config
#given a current config returns the configuration hash
sub config {
	my ($pkg,$cfg) = @_;
	return {};
}

#$class,$id -> @info
#returns a list (hash) of infos about the given id
sub about {
	my ($self,$id) = @_;
	$id = $self->id() if (!defined $id and ref($self));
	return map {"$_: " . $clist{$id}->{$_}} keys %{$clist{$id}};
}

#$self,$id -> $name
sub name {
	my ($self,$id) = @_;
	$id = $self->id() if (!defined $id and ref($self));
	return  $clist{$id}->{name} if $clist{$id};
	return undef;
}

#$class,$id -> \%self
#returns the first spot
sub first {
	my ($class,$id) = @_;
	$l->trace('creating first');
	unless($id ~~ %clist) {
		$l->error("unknown id: ", $id);
		return undef;
	}
	return $class->create($id,1,$clist{$id}->{url_start});
}

#$class, $id, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($class,$id,$pos,$state) = @_;
	my $self = {id => $id, position => $pos, state => $state};
	$l->debug('creating new core ' , $class, ' id: ', $id, ,' position: ', $pos);
	unless (exists $clist{$self->{id}}) {
		$l->error('id unknown: ' . $self->{id});
		return undef;
	}
	$class->new($self);
	return $self;
}

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
	$l->trace('mounting ' . $s->{id} .' '. $s->{state});
	my $page = DlUtil::get($s->{state});
	if ($page->is_error()) {
		$l->error('error getting ' . $s->{state});
		$s->{fail} = 'could not get page';
		return undef;
	}
	$l->trace('parsing page');
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->content());
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
	$s->{fail} = undef;
	$l->trace("found: " . $s->{src});
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
	$l->trace('fetching object');
	my $object = {};

	my $file = DlUtil::get($s->{src},$s->{state});
	if ($file->is_error()) {
		$l->error('error getting ' . $s->{src});
		return undef;
	}
	$object->{blob} = $file->content();

	($object->{filename}) = ($s->{src} =~ m'/([^/]+)$'i) ;
	$object->{type} = $file->header('Content-Type');
	$object->{sha1} = $SHA->add($object->{blob})->hexdigest();
	$object->{src} = $s->{src};
	$object->{page_url} = $s->{state};
	$object->{cid} = $s->{id};
	$object->{$_} = $s->{$_} for qw(position state title alt width height);
	$s->{entity} = Entity->new($object);
	return $s->{entity};
}


#returns the next spot
sub next {
	my ($s) = @_;
	$l->trace('creating next');
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	unless ($s->{next}) {
		$l->error('can not get next');
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
