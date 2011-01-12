#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Globals v1.2.0;

use 5.012;
use warnings;

# ----- global default variables -----

our $LOGLVL; #loglevel is set in log.pm
our $PORT = 80; 
our $CACHEDIR = './cache/';
our $DATADIR = './data/';
our $EXPORTDIR = './export/';
our $USERPREFSFILE = 'userprefs'; 
our $UPDATEUNIVERSAL = undef; 

# ----- accessors --------------------

sub loglevel { 
	$LOGLVL = shift if @_;
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
sub updateuniversal { 
	$UPDATEUNIVERSAL = shift if @_;
	return $UPDATEUNIVERSAL;
}

# config array for getopt long
sub getoptarray {
	return (
		"LOGLVL=i" => \$LOGLVL,
		"PORT=i" => \$PORT,
		"CACHEDIR=s" => \$CACHEDIR,
		"DATADIR=s" => \$DATADIR,
		"EXPORTDIR=s" => \$EXPORTDIR,
		"USERPREFSFILE=s" => \$USERPREFSFILE,
		"UPDATEUNIVERSAL" => \$UPDATEUNIVERSAL,
	);
}


1;
