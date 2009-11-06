use 5.010;
use strict;
use File::Path qw (remove_tree);
use File::Find;
use Archive::Zip qw(:ERROR_CODES :CONSTANTS);

system(qw(git clone git://comcol.git.sourceforge.net/gitroot/comcol/comcol temp_comcol_package));
remove_tree('temp_comcol_package/.git', {
  verbose => 1,
});
find( sub { say "unlink $File::Find::name" and unlink($_) if $_ =~ /^\../} , "temp_comcol_package");

use lib "./temp_comcol_package/lib";
require Comic;
require dbutil;

my $version;
my $build;
open(COMCOL, "<temp_comcol_package/comic3.pl");
while(<COMCOL>) {
	if ($_ =~ /^my \$build = (.+)$/i) {
		$build = eval($1);
	}
	if ($_ =~ /^our \$VERSION = (\S+)/i) {
		$version = $1;
	}
}
close COMCOL;

$version .= ".$build";
$version =~ s/\./_/g;

my $zip = Archive::Zip->new();
$zip->addTree( "temp_comcol_package" );
$zip->writeToFileNamed("comcol-$version.zip");

remove_tree('temp_comcol_package', {
  verbose => 1,
});