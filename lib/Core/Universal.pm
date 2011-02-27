#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Universal v1.1.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);
use FindBin;

my $l = Log->new();

#universal does not want to save its list
sub save_clist { return 1}

#tries to load the collection list from file, creates it if it cant be found
sub _load_list {
	my ($pkg) = @_;
	$pkg->update_list();
	return 1;
}

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %raw_list = do ($FindBin::Bin.'/uclist.txt');
	if ($@) {
		chomp $@;
		$l->error('could not load list: ' , $@);
		return;
	}
	my %list;
	for my $id (keys %raw_list) {
		my @l = @{$raw_list{$id}};
		$list{'Universal_'.$id} = {name => shift @l,
								url_start => shift @l };
		my @criteria;
		push(@criteria,shift @l) while ref $l[0];
		$list{'Universal_'.$id}->{criteria} = \@criteria;
		while ($l[0]) {
			given (shift @l) { 
				when ('next') {
					my @next;
					push(@next,shift @l) while ref $l[0];
					$list{'Universal_'.$id}->{next} = \@next;
				}
				when ('url_hack') {
					$list{'Universal_'.$id}->{url_hack} = shift @l;
				}
			}
		}
	}
	return \%list;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name);
}



package Core::Universal::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $criteria = Core::Universal->clist($s->id)->{criteria};
	my $tags = _find([$tree],@$criteria);
	unless (@$tags) {
		$l->error("could not find tag");
		return;
	}
	my @img = grep {defined} map {$_->look_down(_tag=>'img')} @$tags;
	@img = grep {defined} map {$_->look_down(_tag=>'embed')} @$tags unless @img;
	$l->warn('more than one image found') if @img > 1;
	my $img = $img[0];
	unless ($img) {
		$l->error('no object found');
		return;
	}
	$s->{$_} = $img->attr($_) for qw( src title alt width height );
	my $a;
	my $next_crit = Core::Universal->clist($s->id)->{next};
	if ($next_crit and @$next_crit) {
		my $tags = _find([$tree],@$next_crit);
		if (@$tags) {
			my @a = grep {defined} map {$_->look_down(_tag=>'a')} @$tags;
			   @a = grep {defined} map {$_->look_up(_tag=>'a')} @$tags unless @a;
			   @a = grep {defined} map {$_->look_down(_tag=>'area')} @$tags unless @a;
			$a = $a[0];
		}
	}
	else {
		$a = $img->look_up(_tag => 'a');
		if (!$a or !$a->attr('href') or $a->attr('href') =~ m/\.(jpe?g|gif|png|bmp)(\W|$)/i) {
			$a = $tree->look_down(_tag=>'a', rel => qr/next/)
			|| $tree->look_down(_tag=>'a', sub {($_[0]->as_HTML =~ m/next/i)});
		}
	}
	unless ($a) {
		$l->warn("could not find next");
	}
	else {
		$s->{next} = $a->attr('href');
		$s->{next} = URI->new_abs($s->{next},$s->{page_url})->as_string();
		$s->{next} =~ s/^([^#]*)#.*$/$1/; #trim url after '#'
	}
	$s->{src} = URI->new_abs($s->{src},$s->{page_url})->as_string();
	$s->{src} =~ s/^([^#]*)#.*$/$1/; #trim url after '#'
	my $url_hack = Core::Universal->clist($s->id)->{url_hack};
	if ($url_hack) {
		$l->trace('url hack');
		$s->{$_} =  $url_hack ->($s->{$_}) for qw(src next);
	}
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
