#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;
use FindBin;
use lib $FindBin::Bin."/lib";

our $VERSION = v4.1.0;

use Log;
use Controller;

# ----- global default variables -----

our $LOGLVL = $Log::TRACE;
our $PORT = 80; 
our $DIRCACHE = './cache/';
our $DIRDATA = './data/';
our $IDLE = 60; #time until idle mode
our $EXPORT = './export/';

# ------------------------------------

my $l = Log->new();

unless (Controller::init()) {
	$l->fatal('could not initialise controller');
	die;
}

Controller::start();

