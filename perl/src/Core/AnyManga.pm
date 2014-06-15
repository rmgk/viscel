#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::AnyManga v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw(Core);
use Spot::AnyManga;

#fetches the list of known remotes
sub _fetch_list {
	my ($pkg) = @_;
	my %mangalist;
	Log->trace('fetch list of known remotes');
	my $tree = DlUtil::get_tree('http://www.anymanga.com/directory/all/') or return;
	foreach my $list ($$tree->look_down('_tag' => 'ul', 'class' => 'mainmangalist')) {
		foreach my $item ($list->look_down('_tag'=>'li')) {
			my $a = $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->look_down('_tag'=>'a');
			my $href = $a->attr('href');
			my $name = $a->as_trimmed_text(extra_chars => '\xA0');
			my ($id) = ($href =~ m'^/(.*)/$');
			$id =~ s/\W/_/g;
			$id = 'AnyManga_' . $id;
			$href = 'http://www.anymanga.com' . $href;
			if ($mangalist{$id}) { #its an alias
				if ($mangalist{$id}->{Alias}) {
					$mangalist{$id}->{Alias} .= ', '.$name;
				}
				else {
					$mangalist{$id}->{Alias} = $name;
				}
				next;
			}
			$mangalist{$id} = {start => $href . '001/001/', url_info => $href, name => $name};
			$mangalist{$id}->{Status} = $item->look_down('_tag' => 'span', title => 'Manga Complete') ? 'complete' : 'ongoing' ;
			$mangalist{$id}->{Artist} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/bolder/)->content_list();
			$mangalist{$id}->{Artist} =~ s/^\s*by\s*//;
			$mangalist{$id}->{Tags} = join '', grep {!ref($_)} $item->look_down('_tag'=> 'span', 'style' => qr/normal/)->content_list();
			$mangalist{$id}->{$_} = HTML::Entities::encode($mangalist{$id}->{$_}) for qw(Artist Tags);
		}
	}
	return \%mangalist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Alias Tags Artist Chapter Scanlator Status);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s,$cfg) = @_;
	my $url = $cfg->{url_info};
	my $tree = DlUtil::get_tree($url);
	$cfg->{Tags} = ($$tree->look_down('_tag' => 'td', sub { $_[0]->as_text eq 'Categories:' })->parent()->content_list())[1]->as_text();
	$cfg->{Chapter} = ($$tree->look_down('_tag' => 'td', sub { $_[0]->as_text eq 'Info:' })->parent()->content_list())[1]->as_text();
	$cfg->{Scanlator} = ($$tree->look_down('_tag' => 'td', sub { $_[0]->as_text eq 'Manga scans by:' })->parent()->content_list())[1]->as_text();
	#$cfg->{update} = ($tree->look_down('_tag' => 'strong', sub { $_[0]->as_text eq 'Last Manga Update:' })->parent()->content_list())[1];
	$cfg->{Status} = ($$tree->look_down('_tag' => 'td', sub { $_[0]->as_text eq 'Status:' })->parent()->content_list())[1]->as_text();
	$cfg->{Detail} = ($$tree->look_down('_tag' => 'div', style => qr/font-weight: bolder;$/)->parent()->content_list())[3]->as_text();
	my @seealso = $$tree->look_down('_tag' => 'span', style => 'font-weight: bolder;');
	$cfg->{Seealso} = join(', ', map {$_->look_down('_tag'=> 'a')->attr('href') =~ m'^/(.*)/$'; my $id = $1; $id =~ s/\W/_/g; $id;} @seealso);
	return $cfg;
}

1;
