#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::ComicGenesis v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::ComicGenesis;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg,$state) = @_;
	my %comiclist;
	my @letters = ('0','A'..'Z');
	my $index = ref $state ? $state->[0] : 0;
	my $letter =  $letters[$index];
	Log->trace('fetch list of known remotes ', $letter);
	my $tree = DlUtil::get_tree("http://guide.comicgenesis.com/Keenspace_$letter.html") or return; 
	foreach my $main ($$tree->look_down('_tag' => 'div', 'class' => 'comicmain', sub { $_[0]->as_text =~ m/Number of Days: (\d+)/i; $1 > 20} )) {
		my $a = $main->look_down('_tag'=> 'a', 'target' => '_blank', sub {$_[0]->as_text =~ /^\d{8}$/});
		next unless $a;
		my $href = URI->new($a->attr('href'))->as_string();
		$href =~ s'^.*http://'http://'g; #hack to fix some broken urls
		$href =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
		my ($id) = ($href =~ m'^http://(.*?)\.comicgenesis?\.com/'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		my $name = HTML::Entities::encode($main->parent->look_down(_tag=>'div',class=>'comictitle')->look_down(_tag=>'a',href=>qr"http://$id\.comicgen(esis)?\.com")->as_trimmed_text(extra_chars => '\xA0'));
		unless ($name) {
			Log->warn("could not get name for $id using id as name");
			$name = $id;
			
		}
		$id =~ s/\W/_/g;
		$id = 'ComicGenesis_' . $id;
		$comiclist{$id} = {start => $href, name => $name};
	}
	#$tree->delete();
	
	$index = $index < 26 ? [$index + 1] : undef;
	return (\%comiclist,$index);
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}

1;
