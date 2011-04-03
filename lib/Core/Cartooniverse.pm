#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Cartooniverse v1.3.0;

use 5.012;
use warnings;

use parent qw(Core::Template);
use Spot::Cartooniverse;
use Try::Tiny;

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('create list of known collections');
	my $tree = DlUtil::get_tree('http://www.cartooniverse.co.uk/beta/list.php') or return;
	foreach my $content ($$tree->look_down('_tag' => 'div', 'class' => 'postcontent')) {
		foreach my $td ($content->look_down('_tag' => 'td')) {
			my $a = $td->look_down(_tag=>'a');
			my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
			my $href = $a->attr('href');
			my ($id) = ($href =~ m'/([^/]*)\.html$'i);
			unless ($id) {
				Log->debug("could not parse $href");
				next;
			}
			$id =~ s/\W/_/g;
			$id = 'Cartooniverse_' . $id;
			$clist{$id} = {start => $href. '1/0/', name => $name, url_info=>$href};
		}
	}
	#$tree->delete();
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Author Artist Scanlator Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my $tree;
	try {
		$tree = DlUtil::get_tree($url);
	}
	catch {
		if (ref($_) eq 'ARRAY' and $_->[0] eq 'get page') {
			if ($_->[1] == 404) { #404 is a permanent error and means the collection is broken
				Log->warn('collection is not available');
				$cfg->{Status} = 'down'; 
				return $cfg;
			}
		}
		die $_;
	};

	my @postcontent = $$tree->look_down('_tag' => 'div', class=>'postcontent');
	my $td = $postcontent[0]->look_down(_tag=>'table',align=>'center')->look_down(_tag=>'td'); #first postcontent, first td
	my @p = $td->look_down(_tag=>'p');
	if ($p[6]) {
		my $author = HTML::Entities::encode(($p[1]->content_list())[1]);
		$author =~  s/^\s*:\s*//;
		$cfg->{Artist} = HTML::Entities::encode(($p[2]->content_list())[1]);
		$cfg->{Artist} =~  s/^\s*:\s*+//;
		$cfg->{Artist} .= ' ' . $author;
		$cfg->{Scanlator} = HTML::Entities::encode(($p[3]->content_list())[1]);
		$cfg->{Scanlator} =~  s/^\s*:\s*+//;
		$cfg->{Tags} = join ", ", map {$_->as_trimmed_text()} $p[4]->look_down(class => 'series-info');
		$cfg->{Detail} = HTML::Entities::encode(($p[6]->content_list())[2]);
	}
	
	my @chaplist = $postcontent[-1]->look_down(_tag=>'table',align=>'center')->look_down(_tag=>'tr');
	$cfg->{start} = $chaplist[-1]->look_down(_tag=>'td')->look_down(_tag=>'a')->attr('href');
	return $cfg;
}

1;
