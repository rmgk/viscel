#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Globals v1.1.0;

use 5.012;
use warnings;

use Log;

# ----- global default variables -----

our $LOGLVL = $Log::DEBUG;
our $PORT = 80; 
our $CACHEDIR = './cache/';
our $DATADIR = './data/';
our $EXPORTDIR = './export/';
our $USERPREFSFILE = 'userprefs'; 

# ----- accessors --------------------

sub loglevel { 
	$LOGLVL = shift if @_;
	Log::setlevel($LOGLVL);
	return $LOGLVL;
}
sub port { 
	$PORT = shift if @_;
	return $PORT;
}
sub cachedir { 
	$CACHEDIR = shift if @_;
	return $CACHEDIR;
}
sub datadir { 
	$DATADIR = shift if @_;
	return $DATADIR;
}
sub exportdir { 
	$EXPORTDIR = shift if @_;
	return $EXPORTDIR;
}
sub userprefsfile { 
	$USERPREFSFILE = shift if @_;
	return $USERPREFSFILE;
}

loglevel($LOGLVL);


1;
