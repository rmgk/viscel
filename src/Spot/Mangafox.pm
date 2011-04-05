#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Mangafox v1.3.0;

use 5.012;
use warnings;

use parent qw(Spot::Template);

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
		$next = undef;
		my $url_info = $s->{page_url};
		$url_info =~ s'/[^/]+/[^/]+/[^/]+$'/';
		my $tree = DlUtil::get_tree($url_info);
		my $url = $s->{page_url};
		my @chapter = reverse $$tree->look_down(_tag=>'a',class=>'chico');
		for (0..($#chapter - 1)) {
			my $href = $chapter[$_]->attr('href');
			if ($url =~ m/\Q$href\E/i) {
				$next = $chapter[$_+1]->attr('href');
				last;
			}
		}
	}
	return unless $next;
	$s->{next} = URI->new_abs($next,$s->{page_url})->as_string();
	return 1;
}

1;
