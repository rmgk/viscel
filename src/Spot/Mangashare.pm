#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Mangashare v1.3.0;

use 5.012;
use warnings;

use parent qw(Spot::Template);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'div', id => 'page')->look_down(_tag => 'img');
	unless ($img) {
		Log->error('could not parse page');
		return;
	}
	map {$s->{$_} = $img->attr($_)} qw(src alt);
	
	my $next = $img->parent()->attr('href');
	return unless $next;
	$s->{next} = URI->new_abs($next,$s->{page_url})->as_string();
	return 1;
}

1;
