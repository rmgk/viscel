#pack with "pp -M  Tie::Hash::NamedCapture c3.pl"
use 5.010;
use strict;
use warnings;
use Cwd 'abs_path';


$ARGV[0] //= '-c';

if ($ARGV[0] eq '-c') {
	shift @ARGV;
	require scalar(abs_path("comic3.pl")),@ARGV;
}
elsif ($ARGV[0] eq '-h') {
	shift @ARGV;
	require scalar(abs_path('httpserver.pl')),@ARGV;
}
else {
	require scalar(abs_path("comic3.pl")),@ARGV;
}


#use Comic;
#use Page;
#use Strip;
#use dbutil;
#use dlutil;

use DBI;
use URI;
use Digest::SHA;


use LWP;
use LWP::UserAgent;
use LWP::ConnCache;

#use HTTP::Daemon;
#use HTTP::Status;
#use CGI qw(:standard *table :html3);
#use Data::Dumper;
use Time::HiRes;