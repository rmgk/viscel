#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::AnyManga v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', title => qr'Click to view next page or press next or back buttons'i);
	map {$s->{$_} = $img->attr($_)} qw( src title alt );
	my $a_next = $img->look_up(_tag => 'a');
	if ($a_next) {
		$s->{next} = 'http://www.anymanga.com' . $a_next->attr('href');
	}
	$s->{src} = $s->{src};
	$s->{title} =~ s/\)\s*\[.*$/)/s;
	$s->{alt} =~ s/\)\s*\[.*$/)/s;
	return 1;
}

1;
