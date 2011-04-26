#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Mangareader v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', id=>'img');
	unless ($img) {
		Log->error('could not parse page');
		return;
	}
	map {$s->{$_} = $img->attr($_)} qw( src width height);
	my $a_next = $img->parent();
	if ($a_next) {
		$s->{next} = URI->new_abs($a_next->attr('href'),$s->{page_url})->as_string();
	}
	return 1;
}

1;
