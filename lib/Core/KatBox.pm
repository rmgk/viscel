#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::KatBox v1.2.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);
use FindBin;

my $l = Log->new();


#creates the list of known colections
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	$l->trace('create list of known collections');

	my $tree = DlUtil::get_tree('http://www.katbox.net/') or return;
	foreach my $area ($$tree->look_down('_tag' => 'area', 'shape' => 'poly')) {
		my $name = HTML::Entities::encode($area->attr('alt'));
		my $href = $area->attr('href');
		my ($id) = ($href =~ m'^http://(\w+)\.'i);
		unless ($id) {
			$l->debug("could not parse $href");
			next;
		}
		$id =~ s/\W/_/g;
		$id = 'KatBox_' . $id;
		$clist{$id} = {url_start => $href.'index.php?strip_id=1', name => $name};
		
	}
	#$tree->delete();

	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}



package Core::KatBox::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down( '_tag' => 'img', src => qr'istrip_files/strips/');
	$s->{$_} = $img->attr($_) for qw(src alt);
	my $next = $tree->look_down(id => 'next');
	if ($next) {
		$s->{next} = $next->look_down(_tag=> 'a')->attr('href');
	}
	$s->{$_} = URI->new_abs($s->{$_},$s->{page_url})->as_string() for qw(src next);
	return 1;
}

1;
