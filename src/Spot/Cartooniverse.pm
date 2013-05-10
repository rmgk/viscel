#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Cartooniverse v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', id=>'supermanga');
	map {$s->{$_} = $img->attr($_)} qw( src );
	my $js = $tree->look_down(_tag => 'input', value=>'next')->attr('onclick');
	if ($js =~ m#javascript:window.location='(.*)';$#) {
		$s->{next} = $1;
	}
	#my $title = $tree->look_down(_tag=>'title')->as_trimmed_text();
	#if ($title =~ m/Chapter (\d+) page \d+ : Cartooniverse.co.uk/) { 
	#	$s->{chapter} = $1;
	#}
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$');
	return 1;
}

1;
