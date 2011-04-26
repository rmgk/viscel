#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Animea v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', class=>'chapter_img');
	unless ($img) {
		Log->error('could not parse page');
		return;
	}
	map {$s->{$_} = $img->attr($_)} qw( src width height);
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = $a_next->attr('href');
	}
	return 1;
}

1;
