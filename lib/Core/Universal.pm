#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Universal;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#initialises collection list
sub init {
	my ($pkg) = @_;
	$l->trace('initialise ',$pkg);
	$l->warn('list already initialised, reinitialise') if $pkg->clist();
	$pkg->clist($pkg->_create_list());
	$l->debug('has ' .  scalar($pkg->clist()) . ' collections');
}

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	return {Universal_Zona => { name => 'The challenges of Zona',
								url_start => 'http://www.soulgeek.com/comics/zona/2005/08/01/page-01/'},
			Universal_Unstuffed => { name =>'The Unstuffed',
									url_start => 'http://www.plushandblood.com/Comic.php?strip_id=0'},
			Universal_PennyAndAggie => { name => 'Penny And Aggie',
										url_start => 'http://www.pennyandaggie.com/index.php?p=1'},
			Universal_Kukubiri => { name => 'Kukubiri',
									url_start => 'http://www.kukuburi.com/v2/2007/08/09/one/' },
			Universal_KhaosKomic => { name => 'Khaos Komik',
									url_start => 'http://www.khaoskomix.com/cgi-bin/comic.cgi?chp=1',
									criteria => [id => 'currentcomic'] },
			Universal_Catalyst => { name => 'Catalyst',
									url_start => 'http://catalyst.spiderforest.com/comic.php?comic_id=0',
									criteria => [src => qr/comics/] },
			Universal_EmergencyExit => { name => 'Emergency Exit',
									url_start => 'http://www.eecomics.net/?strip_id=0',
									criteria => [src => qr'comics/\d{6}\.']},
			Universal_Drowtales => { name => 'Drowtales',
									 url_start => 'http://www.drowtales.com/mainarchive.php?order=chapters&id=0&overview=1&chibi=1&cover=1&extra=1&page=1&check=1',
									 criteria => [src => qr'mainarchive//']},
			Universal_HilarityComics => { name => 'Hilarity Comics', 
											url_start => 'http://www.eegra.com/show/sub/do/browse/cat/comics/id/4',
											criteria => [src => qr'comics/\d{4}/']},
			Universal_PBF => { name => 'The Perry Bible Fellowship',
								url_start => 'http://www.pbfcomics.com/?cid=PBF001-Stiff_Breeze.gif',
								criteria => [id => 'topimg'] },
								};
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}

#fetches more information about the comic
sub fetch_info {}



package Core::Universal::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my @tag;
	my $criteria = Core::Universal->clist($s->id)->{criteria};
	if ($criteria) {
		say "criteria";
		@tag = $tree->look_down(@{$criteria});
	}
	else {
		@tag = $tree->look_down(id=>qr/comic/i);
		@tag = $tree->look_down(class=>qr/comic/i) unless @tag;
	}
	my @img = grep {defined} map {$_->look_down(_tag=>'img')} @tag;
	$l->warn('more than one image found') if @img > 1;
	my $img = $img[0];
	map {$s->{$_} = $img->attr($_)} qw( src title alt width heigth );
	my $a = $tree->look_down(_tag=>'a', rel => qr/next/)
		 || $tree->look_down(_tag=>'a', sub {($_[0]->as_text =~ m/next|newer/i) or $_[0]->look_down(_tag=>'img',src=> qr/next/i)});
	$s->{next} = $a->attr('href');
	$s->{$_} = URI->new_abs($s->{$_},$s->{page_url})->as_string() for qw(src next);
	return 1;
}

1;
