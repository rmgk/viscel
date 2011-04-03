#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::KatBox v1.3.0;

use 5.012;
use warnings;

use parent qw(Core::Template);
use Spot::KatBox;

#creates the list of known colections
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('create list of known collections');

	my $tree = DlUtil::get_tree('http://www.katbox.net/') or return;
	foreach my $area ($$tree->look_down('_tag' => 'area', 'shape' => 'poly')) {
		my $name = HTML::Entities::encode($area->attr('alt'));
		my $href = $area->attr('href');
		my ($id) = ($href =~ m'^http://(\w+)\.'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		$id =~ s/\W/_/g;
		$id = 'KatBox_' . $id;
		$clist{$id} = {start => $href.'index.php?strip_id=1', name => $name};
		
	}
	#$tree->delete();

	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}

1;
