#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::ComicGenesis v1.3.0;

use 5.012;
use warnings;

use parent qw(Spot::Template);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', src => qr'/comics/.*\d{8}'i,width=>qr/\d+/,height=>qr/\d+/);
	unless ($img) {
		Log->error('could not get image');
		$s->{fail} = 'could not get image';
		return;
	}
	map {$s->{$_} = $img->attr($_)} qw( src title alt width height);
	$s->{src} = URI->new_abs($s->{src},$s->{state})->as_string;
	$s->{src} =~ s'^.*http://'http://'g; #hack to fix some broken urls
	$s->{src} =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
	my $a = $tree->look_down(_tag => 'a', sub {$_[0]->as_text =~ m/^Next comic$/});
	unless ($a) {
		my $img_next = $tree->look_down(_tag => 'img', alt => 'Next comic');
		unless($img_next) {
			Log->warn('could not get next');
		}
		else {
			$a = $img_next->parent();
		}
	}
	if ($a) {
		$s->{next} = $a->attr('href');
		$s->{next} = URI->new_abs($s->{next} ,$s->{state})->as_string;
		$s->{next} =~ s'^.*http://'http://'g; #hack to fix some broken urls
		$s->{next} =~ s'\.comicgen\.com'.comicgenesis.com'gi; #hack to fix more broken urls
	}
	return 1;
}

1;
