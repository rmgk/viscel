#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Snafu v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down( '_tag' => 'img', src => qr'/comics/\d{6}_');
	$s->{$_} = $img->attr($_) for qw(src alt);
	my $next = $tree->look_down(_tag => 'a', sub {$_[0]->as_text ~~ qr'Next'});
	if ($next) {
		$s->{next} = $next->attr('href');
	}
	$s->{$_} = URI->new_abs($s->{$_},$s->{page_url})->as_string() for qw(src next);
	return 1;
}

1;
