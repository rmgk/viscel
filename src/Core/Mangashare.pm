#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Mangashare v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core::Template);
use Spot::Mangashare;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %clist;
	Log->trace('fetch list of known remotes');
	my $url = 'http://read.mangashare.com/dir';
	my $tree = DlUtil::get_tree($url);
	my $content = $$tree->look_down(_tag => 'div', id => 'content');
	foreach my $tr ($content->look_down('_tag' => 'tr', class => 'datarow')) {
		my @td = $tr->look_down(_tag => 'td');
		next unless @td;
		my $href = $td[3]->look_down(_tag => 'a')->attr('href');
		
		my $name = HTML::Entities::encode($td[0]->as_trimmed_text(extra_chars => '\xA0'));
		my $chapters = $td[2]->as_trimmed_text();
		my $update = $td[1]->as_trimmed_text();
		
		my ($id) = ($href =~ m'^/(.*)$'i);
		unless ($id) {
			Log->debug("could not parse $href");
			next;
		}
		$href = URI->new_abs($href,$url)->as_string;
		$id =~ s/\W/_/g;
		$id = 'Mangashare_' . $id;
		$clist{$id} = {url_info => $href, name => $name } ;
		$clist{$id}->{Chapter} = $chapters;
		$clist{$id}->{Updated} = $update;
		
	}
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Artist Tags Chapter Updated);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my $tree = DlUtil::get_tree($url);
	my $content = $$tree->look_down(_tag => 'div', id => 'content');
	my @chapter = $content->look_down(_tag=>'tr',class=>'datarow');
	if (@chapter) {
		my $start = $chapter[-1]->look_down(_tag => 'a')->attr('href');
		$cfg->{start} = URI->new_abs($start,$url)->as_string;
	}
	else {
		Log->warn($s->{id} . ' is no longer available');
		$cfg->{start} = undef;
		$cfg->{Status} = 'down';
	}
	
	my @contentlist = $content->content_list();
	while (my $tag = shift @contentlist) {
		if (ref $tag) {
			last if $tag->tag eq 'h3';
			next unless $tag->tag eq 'strong';
			my $text = HTML::Entities::encode(shift @contentlist);
			given ($tag->as_trimmed_text) {
				when(/^Author/) { $cfg->{Artist} = join ', ', grep {defined} $cfg->{Artist},$text; }
				when(/^Artist/) { $cfg->{Artist} = join ', ', grep {defined} $cfg->{Artist},$text; }
				when(/^Summary/){ $cfg->{Detail} = $text; }
				when(/^Genre/)  { $cfg->{Tags} = $text; }
				default {}
			}
		}
	}
	
	return $cfg;
}

1;
