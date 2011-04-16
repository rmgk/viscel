#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Homeunix v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Spot::Template);

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	#we need to use a custom regex because the page 
	#prints itself with javascript
	my $page = $tree->as_HTML();
	$page =~ m'<a (?:class="ne" )?href ="([^"]+)"><b>\[NEXT(?: CHAPTER)?\]</b></a>';
	my $href = HTML::Entities::encode($1);
	$page =~ m'<IMG ALT=" " STYLE="border: solid 1px #262626" SRC="([^"]+)" >';
	my $src = HTML::Entities::encode($1);
	unless ($src) {
		Log->error('could not parse page');
		return;
	}
	unless ($href) {
		$page =~ m'<a href ="([^"]+)"><b>\[RETURN\]</b></a>';
		my $chapter = HTML::Entities::encode($1);
		my $tree = DlUtil::get_tree($chapter) or return;
		my $ch = $$tree->look_down(_tag=>'tr',class => qr/^snF sn(Even|Odd)$/);
		my $a = $ch->look_down(_tag=>'a');
		my $overview = $a->attr('href');
		#$tree->delete();
		$tree = DlUtil::get_tree($overview) or return;
		my @chlist = $$tree->look_down(_tag=>'tr',class => qr/^snF sn(Even|Odd)$/);
		for my $i ( reverse(1 .. $#chlist) ) {
			my $ch = $chlist[$i];
			if ($ch->look_down(_tag=>'a', href => $chapter)) {
				my $a = $chlist[$i-1]->look_down(_tag=>'a');
				my $url = $a->attr('href');
				#$tree->delete();
				my %urls;
				while ($url) {
					return if $urls{$url}; #abort recursion
					$urls{$url} = 1; 
					my $tree = DlUtil::get_tree($url) or return;
					my $fs = $$tree->look_down(_tag => 'fieldset', class=>qr'td2');
					if ($fs) {
						#we are at the last page and can finally find the first page
						my $a = $fs->look_down(_tag=>'a');
						$href = $a->attr('href');
						$url = undef;
					}
					else {
						my @chlist = $$tree->look_down(_tag=>'tr',class => qr/^snF sn(Even|Odd)$/);
						my $ch = $chlist[-1];
						my $a = $ch->look_down(_tag=>'a');
						$url = $a->attr('href');
					}
					#$tree->delete();
				}
				last;
			}
		}
		#$tree->delete();
	}
	$s->{src} = $src;
	$s->{next} = URI->new_abs($href,$s->{page_url})->as_string();;
	return 1;
}

1;
