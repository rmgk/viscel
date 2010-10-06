#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Universal;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	return {Universal_Zona => { name => "The challenges of Zona",
								urlstart => 'http://www.soulgeek.com/comics/zona/2005/08/01/page-01/',
								}};
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
	my $img = $tree->look_down(_tag => 'div', id=>'comic')->look_down(_tag=>'img');
	map {$s->{$_} = $img->attr($_)} qw( src title alt );
	$s->{next} = $tree->look_down(_tag=>'div',class=>'nav')->look_down(_tag=>'a', sub {$_[0]->as_text =~ m/^Next/})->attr('href');
	$s->{page_url} = $s->{state};
	($s->{filename}) = ($s->{src} =~ m'/comics/([^/]+)'i) ;
	return 1;
}

1;
