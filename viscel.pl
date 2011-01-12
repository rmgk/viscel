#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Viscel v4.2.0;

use 5.012;
use warnings;
use FindBin;
use lib $FindBin::Bin."/lib";
use Getopt::Long;

use Log;
use Globals;

my $l = Log->new();

my $add;
my $result = GetOptions (Globals::getoptarray(),"add" => \$add);

if ($add) {
	use Adder;
	Globals::port(8080);
	
	unless (Adder::init()) {
		$l->fatal('could not initialise controller');
		die;
	}

	Adder::start();
}
else {
	use Controller;
	
	unless (Controller::init()) {
		$l->fatal('could not initialise controller');
		die;
	}

	Controller::start();
}


