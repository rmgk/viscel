#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Cartooniverse v1.3.0;

use 5.012;
use warnings;

use parent qw(Core::Template);
use Spot::Cartooniverse;
use Try::Tiny;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('fetch list of known remotes');
	my $tree = DlUtil::get_tree('http://www.cartooniverse.co.uk/beta/list.php') or return;
	foreach my $content ($$tree->look_down('_tag' => 'div', 'class' => 'postcontent')) {
		foreach my $td ($content->look_down('_tag' => 'td')) {
			my $a = $td->look_down(_tag=>'a');
			my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
			next unless $name;
			my $href = $a->attr('href');
			my ($id) = ($href =~ m'/([^/]*)\.html$'i);
			unless ($id) {
				Log->debug("could not parse $href");
				next;
			}
			$id =~ s/\W/_/g;
			$id = 'Cartooniverse_' . $id;
			$clist{$id} = {name => $name, url_info=>$href};
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

	my $td = $$tree->look_down('_tag' => 'td', width=>'317', align => 'left', valign => 'top');
	my @p = $td->content_list();
	if ($p[5]) {
		my $author = HTML::Entities::encode(($p[1]->content_list())[1]);
		$author =~  s/^\s*:\s*//;
		$cfg->{Artist} = HTML::Entities::encode(($p[2]->content_list())[1]);
		$cfg->{Artist} =~  s/^\s*:\s*+//;
		$cfg->{Artist} .= ' ' . $author;
		$cfg->{Tags} = ($p[2]->content_list())[1];
		$cfg->{Tags} =~  s/^\s*:\s*+//;
		$cfg->{Detail} = HTML::Entities::encode($p[5]->as_trimmed_text());
	}
	
	my @chaplist = $$tree->look_down(_tag=>'table',align=>'center',width => '520')->look_down(_tag=>'tr');
	$cfg->{start} = $chaplist[-1]->look_down(_tag=>'td')->look_down(_tag=>'a')->attr('href');
	return $cfg;
}

1;
