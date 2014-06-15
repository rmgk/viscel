#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Animea v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::Animea;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg,$state) = @_;
	my %clist;
	my $url = $state ? $state : 'http://manga.animea.net/browse.html?page=0';
	Log->trace('fetch list of remotes ', $url);

	my $tree = DlUtil::get_tree($url) or return;
	foreach my $td ($$tree->look_down('_tag' => 'td', 'class' => 'c')) {
		my $tr = $td->parent();
		my $a = $tr->look_down(_tag=>'a',class=>qr/manga$/);
		my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
		my $href = $a->attr('href');
		my ($id) = ($href =~ m'^/(.*)\.html$'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		$href = URI->new_abs($href,$url)->as_string;
		$id =~ s/\W/_/g;
		$id = 'Animea_' . $id;
		$clist{$id} = {url_info => $href, name => $name};
		$clist{$id}->{Status} = ($a->attr('class') eq 'complete_manga') ? 'complete' : 'ongoing';
		$clist{$id}->{Chapter} = $td->as_trimmed_text();
		
	}
	my $next = $$tree->look_down('_tag' => 'ul', 'class' => 'paging')->look_down(_tag => 'a', sub { $_[0]->as_text =~ m/^Next$/});
	$url = $next ? URI->new_abs($next->attr('href'),$url)->as_string : undef;
	
	return (\%clist,$url);
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Status Chapter Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	$url .= '?skip=1';
	my $tree = DlUtil::get_tree($url);
	my $chapterslist = $$tree->look_down(_tag=>'table',id=>'chapterslist');
	unless ($chapterslist) { # if chapterslist could not be found, its most likely that something is wrong with the page download
		Log->warn('could not parse page ' , $url);
		die 'parse page ' . $url;
	}
	$cfg->{Tags} = join ', ' , map {$_->as_trimmed_text} $$tree->look_down(_tag=>'a', href=>qr'/genre/'i);
	my $p = $$tree->look_down('_tag' => 'p', style => 'width:570px; padding:5px;');
	$cfg->{Detail} = HTML::Entities::encode($p->as_trimmed_text()) if ($p);
	my $ul = $$tree->look_down('_tag' => 'ul', class => 'relatedmanga');
	if ($ul) {
		$cfg->{Seealso} = join ', ' , map { $_->attr('href') =~ m'animea.net/([^/]*)\.html$'; my $r = $1; $r =~ s/\W/_/g; $r} $ul->look_down(_tag=>'a');
	}
	my @table = $chapterslist->content_list();
	my $a = $table[-2]->look_down(_tag=>'a');
	if ($a) {
		$cfg->{start} = $a->attr('href');
		$cfg->{start} =~ s/\.html$/-page-1\.html/;
	}
	else {
		Log->warn("animea no longer makes this collection available");
		$cfg->{Status} = 'down';
	}
	return $cfg;
}

1;
