#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;
use lib "./lib";

our $VERSION = v4.0.0;

use Log;
use Controller;

# ----- global default variables -----

our $LOGLVL = $Log::TRACE;
our $PORT = 80; 
our $DIRCACHE = './cache/';
our $DIRDATA = './data/';

# ------------------------------------

my $l = Log->new();

mkdir $main::DIRDATA unless (-e $DIRDATA);
mkdir $main::DIRCACHE unless (-e $DIRCACHE);

unless (Controller::init()) {
	$l->fatal('could not initialise controller');
	die;
}

Controller::start();

