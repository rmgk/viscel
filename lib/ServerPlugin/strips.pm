#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::strips;

use 5.010;
use strict;
use warnings;

use ServerPlugin qw(dbstrps);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.0.0';

sub handle_request {
	my ($plugin,$connection,$request,$comic,$strip) = @_;
	if ($strip =~ /^\d+$/) {
		$strip = dbstrps($comic,'id'=>$strip,'file');
		$connection->send_redirect("http://127.0.0.1/strips/$comic/$strip" );
		#$connection->send_file_response("./strips/$comic/$strip");
	}
	else {
		$connection->send_file_response("./strips/$comic/$strip");
	}
}

1;
