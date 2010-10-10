#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Universal;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use parent qw(Core::Template);

my $l = Log->new();

#loads and saves the collection list
sub _load_list {
	my ($pkg) = @_;
	$l->trace('initialise ',$pkg);
	$l->warn('list already initialised, reinitialise') if $pkg->clist();
	$pkg->clist($pkg->_create_list());
	$l->debug('has ' .  scalar($pkg->clist()) . ' collections');
}

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %raw_list = do ('uclist.txt');
	if ($@) {
		chomp $@;
		$l->error('could not load list: ' , $@);
		return undef;
	}
	my %list = map {
		'Universal_'.$_ => {name => shift @{$raw_list{$_}},
							url_start => shift @{$raw_list{$_}},
							criteria => $raw_list{$_}
		}} keys %raw_list; 
	return \%list;
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
	my @criteria = @{Core::Universal->clist($s->id)->{criteria}};
	my $tags = _find([$tree],@criteria);
	unless (@$tags) {
		$l->error("could not find image");
		return undef;
	}
	my @img = grep {defined} map {$_->look_down(_tag=>'img')} @$tags;
	@img = grep {defined} map {$_->look_down(_tag=>'embed')} @$tags unless @img;
	$l->warn('more than one image found') if @img > 1;
	my $img = $img[0];
	map {$s->{$_} = $img->attr($_)} qw( src title alt width height );
	
	my $a = $tree->look_down(_tag=>'a', rel => qr/next/)
		 || $tree->look_down(_tag=>'a', sub {($_[0]->as_HTML =~ m/next/i)});
	unless ($a) {
		$l->warn("could not find next");
		return undef;
	}
	$s->{next} = $a->attr('href');
	$s->{$_} = URI->new_abs($s->{$_},$s->{page_url})->as_string() for qw(src next);
	return 1;
}

#\@tags,@criteria -> $tag
#recursive find tags
sub _find {
	my $tags = shift;
	my $c = shift;
	unless ($c and ref $c) {
		return $tags;
	}
	for my $tag (@$tags) {
		my $t = _find([$tag->look_down(@{$c})],@_);
		return $t if @$t;
	}
	return [];
}

1;
