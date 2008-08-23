#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:17 11.01.2008

use strict;
use warnings;
use lib "./lib";
use Comic;

use vars qw($VERSION);
$VERSION = '71' . '.' . $Comic::VERSION . '.' . $Page::VERSION;


our $TERM = 0;
$SIG{'INT'} = sub { 
		print "Terminating (expect errors)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "press ctrl+c to abort (don't close it otherwise)\n";
print "comic3.pl version $VERSION\n";

my @opts = @ARGV;

{
	use Config::IniHash;
	my $comics = ReadINI('comic.ini',{'case'=>'preserve', 'sectionorder' => 1});
	my $user = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});
		my @comics;
	@comics = @{$comics->{__SECTIONS__}};
	my $opmode;
	if ($opts[0]) {
		$opmode = "std";
		if (($opts[0] eq '-r') and (@opts > 1)) {
			$opmode = 'repair';
			shift @opts;
			foreach (@opts) {
				$user->{$_}->{url_current} = undef;
				delete $user->{$_}->{url_current};
			}
			WriteINI('user.ini',$user);
		}
		elsif (($opts[0] eq '-e') and (@opts > 1)) {
			shift @opts;
			$opmode = 'exact';
		}
		elsif (($opts[0] eq '-rd') and (@opts > 1)) {
			$opmode = 'repairdelete';
			shift @opts;
			foreach (@opts) {
				$user->{$_}->{url_current} = undef;
				delete $user->{$_}->{url_current};
				open(DEL,">./data/$_.dat");
				close DEL;
			}
			WriteINI('user.ini',$user);
		}
	}
	
	
	unless (defined $user->{_CFG_}->{update_interval}) {
		$user->{_CFG_}->{update_interval} = 25000;
		print "no update interval specified using default = 25000 seconds\n";
	}
	

	foreach my $comic (@comics) {
		my $skip = 0;
		if (defined $opmode) {
			if ($opmode eq 'std') {
				for my $opt (@opts) {
					$skip = 1 unless ($comic =~ m/$opt/i);
				}
			}
			elsif (($opmode eq 'repair') or ($opmode eq 'exact') or ($opmode eq 'repairdelete')) {
				for my $opt (@opts) {
					$skip = 1 unless ($comic eq $opt);
				}
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