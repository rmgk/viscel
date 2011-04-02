#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Viscel v4.3.0;

use 5.012;
use warnings;
use FindBin;
use lib $FindBin::Bin.'/lib';
use Getopt::Long;
use Test::Harness;

use Log;
use Globals;
use Controller;

my $test;
my $result = GetOptions (Globals::getoptarray(),"test" => \$test);

if ($test) {
	my $dirname = $FindBin::Bin . '/t/';
	opendir(my $testdir, $dirname);
	runtests map { $dirname . $_ } grep /\.t$/i , readdir $testdir;

}
else {
	my $l = Log->new();
	
	
	unless (Controller::init()) {
		$l->fatal('could not initialise controller');
		die;
	}

	Controller::start();
}


