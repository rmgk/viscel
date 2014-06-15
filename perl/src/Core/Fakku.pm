#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Fakku v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::Fakku;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg,$state) = @_;
	my %clist;
	my $url = $state ? $state : 'http://www.fakku.net/manga/english';
	Log->trace('fetch list of known remotes ', $url);
	my $tree = DlUtil::get_tree($url) or return;
	foreach my $main ($$tree->look_down('_tag' => 'div', 'class' => 'content_row')) {
		my $a = $main->look_down('_tag'=> 'div', 'class' => 'manga_row1')->look_down('_tag' => 'a');
		my $href = $a->attr('href');
		my $name = HTML::Entities::encode($a->as_trimmed_text());
		my ($num) = ($href =~ m'id=(\d+)'i);
		unless ($num) {
			Log->debug("could not parse $href");
			next;
		}
		my $id = 'Fakku_' . $num;
		$clist{$id} = {start => "001:$num" , name => $name}; #url start is start state
		$clist{$id}->{Series} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
		my $trans_link = $main->look_down('_tag'=> 'div', 'class' => 'manga_row2')->look_down('_tag' => 'span',class => 'english')->look_down(_tag => 'a');
		$clist{$id}->{Scanlator} = $trans_link->as_trimmed_text(extra_chars => '\xA0') if $trans_link;
		$clist{$id}->{Artist} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row3')->look_down('_tag' => 'div',class => 'item2')->as_trimmed_text(extra_chars => '\xA0');
		$clist{$id}->{Stats} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_left')->as_trimmed_text(extra_chars => '\xA0');
		$clist{$id}->{Date} = $main->look_down('_tag'=> 'div', 'class' => 'manga_row4')->look_down('_tag' => 'div',class => 'row4_right')->look_down(_tag=>'b')->as_trimmed_text(extra_chars => '\xA0');
		my $desc = $main->look_down('_tag'=> 'div', 'class' => 'tags')->as_trimmed_text(extra_chars => '\xA0');
		$desc =~ s/^Description://i;
		$clist{$id}->{Detail} = $desc unless ($desc =~ m/No description has been written/i);
		$clist{$_} = HTML::Entities::encode $clist{$_} for grep {$clist{$_}} qw(Series Scanlator Artist Stats Date Detail);
	}
	my $next = $$tree->look_down('_tag' => 'div', 'id' => 'pagination')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^\s*Next\s*$/});
	$url = $next ? URI->new_abs($next->attr('href'),$url)->as_string : undef;
	#$tree->delete();
		
	return (\%clist, $url);
}

#returns a list of keys to search for
sub _searchkeys {
	qw(name Series Scanlator Artist Stats Date);
}

1;
