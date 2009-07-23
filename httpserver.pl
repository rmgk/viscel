#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009

use 5.010;
use strict;
use warnings;
use lib "./lib";

use HTTP::Daemon;

#we use the standard plugins to get debug messages and such
use ServerPlugin::index;
use ServerPlugin::strips;
use ServerPlugin::css;
use ServerPlugin::front;
use ServerPlugin::pages;
use ServerPlugin::tools;
use ServerPlugin::striplist;
use ServerPlugin::pod;

our $VERSION = '3.0.1';

my $d = HTTP::Daemon->new(LocalAddress=>'127.0.0.1',LocalPort => 80);
die "could not listen on port 80 - someones listening there already?" unless $d;

print "Please contact me at: <URL:", "http://127.0.0.1/index" ,">\n";
while (my $c = $d->accept) {
	#say "connection accepted";
	if (my $r = $c->get_request) {
		#say "got request " . $r->method;
		if (($r->method eq 'GET')) {
			#say "handling get";
			if ($r->url->path =~ m#^/favicon.ico$#) {}
			elsif ($r->url->path =~ m#^/(?<plugin>\w+)/?(?<args>.*?)/?$#i) {
				my @args = split('/',$+{args});
				#say "path matches";
				my $plugin = "ServerPlugin::$+{plugin}";
				#say "using $plugin";
				if (eval("require $plugin")) {
					#say "success $plugin";
					$plugin->handle_request($c,$r,@args);
				}
				else {
					say "err message: $! - plugin: $plugin";
				}		
			}
		}
		#say "sending crlf";
		$c->send_crlf;
	}
	else {
		say "could not get requeset: " . $c->reason;
	}
	#say "closing connection\n";
	$c->close;	
	undef($c);
}




