#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::AnyManga;

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
use Data::Dumper;

my $l = Log->new();
my $SHA = Digest::SHA->new();
my %mangalist;

#initialises the database connection
sub init {
	$l->trace('initialising Core::AnyManga');
	$l->warn('list already initialised, reinitialising') if %mangalist;
	return _create_list();
}

#creates the list of known manga
sub _create_list {
	if (-e $main::DIRDATA.'AnyManga.txt') {
		$l->debug('loading anymanga manga from file');
		if (open (my $fh, '<', $main::DIRDATA.'AnyManga.txt')) {
			local $/;
			my $txt = <$fh>;
			close $fh;
			%mangalist = %{eval($txt)};
			$l->debug('loaded ' . keys(%mangalist) . ' collections');
			return 1;
		}
		else {
			$l->warn('failed to open filehandle');
		}
	}
	$l->trace('create list of known collections');
	my $page = DlUtil::get('http://www.anymanga.com/directory/all/');
	if ($page->is_error()) {
		$l->error('error getting http://www.anymanga.com/directory/all/');
		return undef;
	}
	$l->trace('parsing HTML');
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->content());
	foreach my $list ($tree->look_down('_tag' => 'ul', 'class' => 'mainmangalist')) {
		foreach my $item ($list->look_down('_tag'=>'li')) {
			my $a = $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->look_down('_tag'=>'a');
			my $href = $a->attr('href');
			my $name = $a->as_text();
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
			$mangalist{$id}->{complete} = $item->look_down('_tag' => 'span', title => 'Manga Complete') ? 'true' : 'false' ;
			$mangalist{$id}->{author} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->content_list();
			$mangalist{$id}->{author} =~ s/^\s*by\s*//;
			$mangalist{$id}->{tags} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/normal/)->content_list();
			#my $alias = $item->look_down('_tag'=> 'div', 'class' => 'mangalistalias');
			#$mangalist{$id}->{alias} = join '', grep {!ref($_)} $alias->content_list() if $alias;
		}
	}
	$tree->delete();
	$l->debug('found ' . keys(%mangalist) . ' collections');
	
	$l->debug('saving list to file');
	if (open (my $fh, '>', $main::DIRDATA.'AnyManga.txt')) {
		print $fh 'my ',Dumper(\%mangalist);
		close $fh;
	}
	else {
		$l->warn('failed to open filehandle');
	}
	
	return 1;
}

#->\%collection_hash
#returns a hash containing all the collection ids as keys and their names and urls as values
sub list {
	return map {$_ , $mangalist{$_}->{name}} keys %mangalist;
}

#$query,$regex -> %list
sub search {
	my ($pgk,@re) = @_;
	$l->debug('searching');
	return map {$_,$mangalist{$_}->{name}} grep {
		my $id = $_;
		@re == grep {
			substr($id,0,9) ~~ $_ or 
			$mangalist{$id}->{name} ~~ $_ or
			(defined $mangalist{$id}->{alias} and $mangalist{$id}->{alias} ~~ $_) or
			(defined $mangalist{$id}->{tags} and $mangalist{$id}->{tags} ~~ $_) or
			(defined $mangalist{$id}->{author} and $mangalist{$id}->{author} ~~ $_)
			} @re
		} keys %mangalist;
}

#pkg, %config -> \%config
#given a hash
sub config {
	my $pkg = shift;
	# my $cfg = UserPrefs->section();
	# while (my ($k,$v) = splice(@_,0,2)) {
		# $cfg->set($k,$v);
	# }
	return {};
}

#$class,$id -> @info
#returns a list (hash) of infos about the given id
sub about {
	my ($self,$id) = @_;
	$id = $self->id() if (!defined $id and ref($self));
	return %{$mangalist{$id}};
}

#$self,$id -> $name
sub name {
	my ($self,$id) = @_;
	$id = $self->id() if (!defined $id and ref($self));
	return  $mangalist{$id}->{name} if $mangalist{$id};
	return undef;
}

#$class,$id -> \%self
#returns the first spot
sub first {
	my ($class,$id) = @_;
	$l->trace('creating first');
	return $class->create($id,1,$mangalist{$id}->{url_start});
}

#$class, $id, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($class,$id,$pos,$state) = @_;
	my $self = {id => $id, position => $pos, state => $state};
	$l->debug('creating new core ' , $class, ' id: ', $id, ,' position: ', $pos);
	unless (exists $mangalist{$self->{id}}) {
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
	my $img = $tree->look_down(_tag => 'img', title => qr'Click to view next page or press next or back buttons'i);
	map {$s->{$_} = $img->attr($_)} qw( src title alt );
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = 'http://www.anymanga.com' . $a_next->attr('href');
	}
	$s->{src} = 'http://www.anymanga.com' . $s->{src};
	$s->{title} =~ s/\)\s*\[.*$/)/s;
	$s->{alt} =~ s/\)\s*\[.*$/)/s;
	$s->{fail} = undef;
	$l->trace(join "\n\t\t\t\t", map {"$_: " .($s->{$_}//'')} qw(src next title alt)); #/padre display bug
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

	($object->{filename}) = ($s->{src} =~ m'/manga/[^/]+/(.*+)'i) ;
	$object->{filename} =~ s'/''g;
	$object->{type} = $file->header('Content-Type');
	$object->{sha1} = $SHA->add($object->{blob})->hexdigest();
	$object->{src} = $s->{src};
	$object->{page_url} = $s->{state};
	$object->{cid} = $s->{id};
	$object->{position} = $s->{position};
	$object->{state} = $s->{state};
	$object->{title} = $s->{title};
	$object->{alt} = $s->{alt};
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
