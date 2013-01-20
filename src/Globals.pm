#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Globals v1.3.0;

use 5.012;
use warnings;
use utf8;

# ----- global default variables -----

our $LOGLVL; #loglevel is set in log.pm
our $FILELOG; #filelog is set in log.pm
our $PORT = 80;
our $CACHEDIR = './cache/';
our $DATADIR = './data/';
our $EXPORTDIR = './export/';
our $USERDIR = './user/';
our $UPDATEUNIVERSAL = undef;
our $NOMAINTENANCE = 0;
our $MAINTENANCETIMEOUT = 60;
our $LONGMAINTENANCETIMEOUT = 3600;

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
sub nomaintenance {
	$NOMAINTENANCE = shift if @_;
	return $NOMAINTENANCE;
}
sub maintenancetimeout {
	$MAINTENANCETIMEOUT = shift if @_;
	return $MAINTENANCETIMEOUT;
}
sub longmaintenancetimeout {
	$LONGMAINTENANCETIMEOUT = shift if @_;
	return $LONGMAINTENANCETIMEOUT;
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
		"NOMAINTENANCE" => \$NOMAINTENANCE,
		"MAINTENANCETIMEOUT=i" => \$MAINTENANCETIMEOUT,
		"LONGMAINTENANCETIMEOUT=i" => \$LONGMAINTENANCETIMEOUT,
	);
}


1;
