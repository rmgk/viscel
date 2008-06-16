#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:17 11.01.2008

use strict;
use warnings;
use lib "./lib";
use Comic;

use vars qw($VERSION);

$VERSION = '3.70';


our $TERM = 0;
$SIG{'INT'} = sub { 
		print "Terminating (expect errors)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "press ctrl+c to abort (don't close it otherwise)\n";
print "comic3.pl version $VERSION\n";

my @opts = @ARGV;

{	#need to export each package to an own file ...just too lazy
	use Config::IniHash;
	my $comics = ReadINI('comic.ini',{'case'=>'preserve', 'sectionorder' => 1});
	my $user = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});

	unless (defined $user->{_CFG_}->{update_interval}) {
		$user->{_CFG_}->{update_interval} = 25000;
		print "no update interval specified using default = 25000 seconds\n";
	}
	
	my @comics;
	@comics = @{$comics->{__SECTIONS__}};
	foreach my $comic (@comics) {
		my $skip = 0;
		if (@opts) {
			for my $opt (@opts) {
				$skip = 1 unless ($comic =~ m/$opt/i);
			}
		}
		else {
			$skip = 1 if (
				(((time - $user->{_CFG_}->{update_interval}) < ($user->{$comic}->{last_update}||0)) or
				($user->{$comic}->{hiatus}) or ($comics->{$comic}->{broken})
				));
		}
		next if ($skip);
		last if $TERM;
		Comic::get_comic({"name" => $comic});
		last if $TERM;
	}
}