#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::KatBox v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot::Template);

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
