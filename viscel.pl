#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;
use lib "./lib";

our $VERSION = v4.0.0;

use Controller;

# ------ initialisation --------------------

my $l = Log->new();

unless (Controller::init()) {
	$l->fatal('could not initialise controller');
	die;
}

Controller::start();

