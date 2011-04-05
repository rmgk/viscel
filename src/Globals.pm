#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Globals v1.3.0;

use 5.012;
use warnings;

# ----- global default variables -----

our $LOGLVL; #loglevel is set in log.pm
our $FILELOG; #filelog is set in log.pm
our $PORT = 80; 
our $CACHEDIR = './cache/';
our $DATADIR = './data/';
our $EXPORTDIR = './export/';
our $USERDIR = './user/';
our $UPDATEUNIVERSAL = undef; 

# ----- accessors --------------------

sub loglevel { 
	$LOGLVL = shift if @_;
	return $LOGLVL;
}
sub filelog { 
	$FILELOG = shift if @_;
	return $FILELOG;
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
sub userdir { 
	$USERDIR = shift if @_;
	return $USERDIR;
}
sub updateuniversal { 
	$UPDATEUNIVERSAL = shift if @_;
	return $UPDATEUNIVERSAL;
}

# config array for getopt long
sub getoptarray {
	return (
		"LOGLVL=i" => \$LOGLVL,
		"FILELOG=i" => \$FILELOG,
		"PORT=i" => \$PORT,
		"CACHEDIR=s" => \$CACHEDIR,
		"DATADIR=s" => \$DATADIR,
		"EXPORTDIR=s" => \$EXPORTDIR,
		"USERDIR=s" => \$USERDIR,
		"UPDATEUNIVERSAL" => \$UPDATEUNIVERSAL,
	);
}


1;
