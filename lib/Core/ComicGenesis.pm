#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::ComicGenesis;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of comic
sub _create_list {
	my %comiclist;
	$l->trace('create list of known collections');
	foreach my $letter('0','A'..'Z') {
		$l->trace("get page for letter $letter");
		my $page = DlUtil::get("http://guide.comicgenesis.com/Keenspace_$letter.html");
		if ($page->is_error()) {
			$l->error("http://guide.comicgenesis.com/Keenspace_$letter.html");
			return undef;
		}
		$l->trace('parse HTML');
		my $tree = HTML::TreeBuilder->new();
		$tree->parse_content($page->decoded_content());
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
			my $name = HTML::Entities::encode($main->parent->look_down(_tag=>'div',class=>'comictitle')->look_down(_tag=>'a',href=>qr"http://$id\.comicgen(esis)?\.com")->as_trimmed_text(extra_chars => '\xA0'));
			unless ($name) {
				$l->warn("could not get name for $id using id as name");
				$name = $id;
				
			}
			$id =~ s/\W/_/g;
			$id = 'ComicGenesis_' . $id;
			$comiclist{$id} = {urlstart => $href, name => $name};
		}
		$tree->delete();
	}
	return \%comiclist;
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}

package Core::ComicGenesis::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
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
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$'i) ;
	return 1;
}

1;
