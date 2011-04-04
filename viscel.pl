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
my $purge;
my $result = GetOptions (Globals::getoptarray(),"test" => \$test, 'purge=s' => \$purge);

if ($purge) {
	if (Controller::init()) {
		given($purge) {
			when(/\w+_\w+/) {
				Collection->get($purge)->purge();
			}
			when('collections') {
				Controller::trim_collections();
			}
			when('cache') {
				Controller::trim_cache();
			}
			default {
				Controller::trim_collections(1);
				Controller::trim_cache(1);
				Log->info('this was a dry run');
			}
		}
	}
}
elsif ($test) {
	my $dirname = $FindBin::Bin . '/t/';
	opendir(my $testdir, $dirname);
	runtests map { $dirname . $_ } grep /\.t$/i , readdir $testdir;

}
else {
	unless (Controller::init()) {
		Log->fatal('could not initialise controller');
		die;
	}

	Controller::start();
}


