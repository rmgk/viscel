#!perl
#this program is free software it may be redistributed under the same terms as perl itself

use 5.012;
use warnings;
use lib "./lib";

our $VERSION = v4.0.0;

use Log;
use Server;
use Core::Comcol;

# ------ initialisation --------------------

my $l = Log->new();

#Server::init();

Core::Comcol::init();
my $col = Core::Comcol->create('Comcol_Inverloch',1);
$col->mount();
my $blob = $col->fetch()->{blob};

open my $fh , '>keks.jpg';
binmode $fh;
print $fh $blob;
close $fh;