#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Universal v1.3.0;

use 5.012;
use warnings;

use parent qw(Core::Template);
use Spot::Universal;
use FindBin;

#universal does not want to save its list
sub save_clist { return 1}

#tries to load the collection list from file, creates it if it cant be found
sub _load_list {
	my ($pkg) = @_;
	$pkg->clist($pkg->fetch_list()->());
	Log->info($pkg . ' loaded ' . scalar($pkg->clist()) . ' collections');
	return 1;
}

#creates the list of known collections
sub _create_list {
	my ($pkg) = @_;
	my %raw_list = do ($FindBin::Bin.'/uclist.txt');
	if ($@) {
		chomp $@;
		Log->error('could not load list: ' , $@);
		return;
	}
	my %list;
	for my $id (keys %raw_list) {
		my @l = @{$raw_list{$id}};
		$list{'Universal_'.$id} = {name => shift @l,
								start => shift @l };
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

1;
