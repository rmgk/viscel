#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Viscel v4.1.0;

use 5.012;
use warnings;
use FindBin;
use lib $FindBin::Bin."/lib";

use Log;
use Globals;
use Controller;

my $l = Log->new();

unless (Controller::init()) {
	$l->fatal('could not initialise controller');
	die;
}

Controller::start();

