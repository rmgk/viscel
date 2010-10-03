#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::ComicGenesis;

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
my %comiclist;

$Data::Dumper::Purity = 1; 
$Data::Dumper::Indent = 0;

#initialises the list of known comics
sub init {
	$l->trace('initialising');
	$l->warn('list already initialised, reinitialising') if %comiclist;
	return _create_list();
}

#creates the list of comic
sub _create_list {
	if (-e $main::DIRDATA.'ComicGenesis.txt') {
		$l->debug('loading comicgenesis comics from file');
		if (open (my $fh, '<', $main::DIRDATA.'ComicGenesis.txt')) {
			local $/;
			#%comiclist
			my $txt = <$fh>;
			close $fh;
			%comiclist = %{eval($txt)};
			$l->debug('loaded ' . keys(%comiclist) . ' collections');
			return 1;
		}
		else {
			$l->warn('failed to open filehandle');
		}
	}
	$l->trace('create list of known collections');
	foreach my $letter('0','A'..'Z') {
		$l->trace("get page for letter $letter");
		my $page = DlUtil::get("http://guide.comicgenesis.com/Keenspace_$letter.html");
		if ($page->is_error()) {
			$l->error("http://guide.comicgenesis.com/Keenspace_$letter.html");
			return undef;
		}
		$l->trace('parsing HTML');
		my $tree = HTML::TreeBuilder->new();
		$tree->parse_content($page->content());
		foreach my $main ($tree->look_down('_tag' => 'div', 'class' => 'comicmain', sub { $_[0]->as_text =~ m/Number of Days: (\d+)/i; $1 > 20} )) {
			my $a = $main->look_down('_tag'=> 'a', 'target' => '_blank', sub {$_[0]->as_text =~ /^\d{8}$/});
			next unless $a;
			my $href = URI->new($a->attr('href'))->as_string();
			$href =~ s'^.*http://'http://'g; #hack to fix some broken urls
			$href =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
			my ($id) = ($href =~ m'^http://(.*?)\.comicgenesis?\.com/'i);
			unless ($id) {
				$l->debug("could not parse $href");
				next;
			}
			my $name = encode_entities($main->parent->look_down(_tag=>'div',class=>'comictitle')->look_down(_tag=>'a',href=>qr"http://$id\.comicgen(esis)?\.com")->as_text());
			unless ($name) {
				$l->warn("could not get name for $id using id as name");
				$name = $id;
				
			}
			$id =~ s/\W/_/g;
			$id = 'ComicGenesis_' . $id;
			$comiclist{$id} = {url_start => $href, name => $name};
		}
		$tree->delete();
	}
	$l->debug('found ' . keys(%comiclist) . ' collections');
	$l->debug('saving list to file');
	if (open (my $fh, '>', $main::DIRDATA.'ComicGenesis.txt')) {
		print $fh 'my ',Dumper(\%comiclist);
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
	return map {$_ , $comiclist{$_}->{name}} keys %comiclist;
}

#$class,$id -> \%self
#returns the first spot
sub first {
	my ($class,$id) = @_;
	$l->trace('creating first');
	unless($id ~~ %comiclist) {
		$l->error("unknown id: ", $id);
		return undef;
	}
	return $class->create($id,1,$comiclist{$id}->{url_start});
}

#$class, $id, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($class,$id,$pos,$state) = @_;
	my $self = {id => $id, position => $pos, state => $state};
	$l->debug('creating new core ' , $class, ' id: ', $id, ,' position: ', $pos);
	unless (exists $comiclist{$self->{id}}) {
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
	my $img = $tree->look_down(_tag => 'img', src => qr'/comics/.*\d{8}'i,width=>qr/\d+/,height=>qr/\d+/);
	unless ($img) {
		$l->error('could not get image');
		$s->{fail} = 'could not get image';
		return undef;
	}
	map {$s->{$_} = $img->attr($_)} qw( src title alt width height);
	$s->{src} = URI->new_abs($s->{src},$s->{state});
	$s->{src} =~ s'^.*http://'http://'g; #hack to fix some broken urls
	$s->{src} =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
	my $a = $tree->look_down(_tag => 'a', sub {$_[0]->as_text =~ m/^Next comic$/});
	unless ($a) {
		my $img_next = $tree->look_down(_tag => 'img', alt => 'Next comic');
		unless($img_next) {
			$l->warn('could not get next');
		}
		else {
			$a = $img_next->parent();
		}
	}
	if ($a) {
		$s->{next} = $a->attr('href');
		$s->{next} = URI->new_abs($s->{next} ,$s->{state});
		$s->{next} =~ s'^.*http://'http://'g; #hack to fix some broken urls
		$s->{next} =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
	}
	$s->{fail} = undef;
	$l->trace(join "\n\t\t\t\t", map {"$_: " .($s->{$_} // '')} qw(src next title alt)); #/padre syntax?!
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
