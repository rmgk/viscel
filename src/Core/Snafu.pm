#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Snafu v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::Snafu;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('fetch list of known remotes');

	my $tree = DlUtil::get_tree('http://www.snafu-comics.com/') or return;
	foreach my $elem ($$tree->look_down('_tag' => 'img', hsrc => qr'http://www.snafu-comics.com/images/comic\w+_over.jpg')) {
		my $name = HTML::Entities::encode($elem->attr('alt'));
		my $href = $elem->parent()->attr('href');
		my $img_src = $elem->attr('src');
		my ($id) = $img_src =~ qr'/comic(\w+).jpg';
		$id = 'Snafu_' . $1;
		$clist{$id} = {start => $href.'/?comic_id=0', name => $name};

	}

	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}

1;
