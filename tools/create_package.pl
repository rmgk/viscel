#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.

use 5.012;
use warnings;

our $VERSION = v1.1.0;

use File::Path qw (remove_tree);
use File::Find;
use Archive::Zip qw(:ERROR_CODES :CONSTANTS);

system(qw(git clone git://comcol.git.sourceforge.net/gitroot/comcol/comcol temp));
remove_tree('temp/.git', {
  verbose => 1,
});
find( sub { say "unlink $File::Find::name" and unlink($_) if $_ =~ /^\../} , "temp");

my $version;
open(COMCOL, "<temp/viscel.pl");
while(<COMCOL>) {
	if ($_ =~ /^\s*package\s*Viscel\s*(\S+);/i) {
		$version = $1;
	}
}
close COMCOL;

my $zip = Archive::Zip->new();
$zip->addTree( "temp" );
$zip->writeToFileNamed("viscel-$version.zip");

remove_tree('temp', {
  verbose => 1,
});
