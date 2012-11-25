#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Mangafox v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', id => 'image');
	unless ($img) {
		Log->error('could not parse page');
		return;
	}
	map {$s->{$_} = $img->attr($_)} qw( src alt);
	
	my $next = $img->parent()->attr('href');
	if ($next eq 'javascript:void(0);') {
		$next = $tree->look_down(_tag => 'span', sub { $_[0]->as_text() =~ m'^Next Chapter:$' } )->parent()->look_down(_tag => 'a');
		$next = ($next and $next->attr('href'));
	}
	unless ($next) {
		Log->warn('could not find next ', $s->id());
		return 1;
	}
	$s->{next} = URI->new_abs($next,$s->{page_url})->as_string();
	return 1;
}

1;
