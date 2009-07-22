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

our $VERSION = '3.0.0';

my $d = HTTP::Daemon->new(LocalAddress=>'127.0.0.1',LocalPort => 80);
die "could not listen on port 80 - someones listening there already?" unless $d;

print "Please contact me at: <URL:", "http://127.0.0.1/index" ,">\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if (($r->method eq 'GET')) {
			if ($r->url->path =~ m#^/(?<plugin>\w+)/?(?<args>.*?)/?$#i) {
				my @args = split('/',$+{args});
				$c->close and next if $+{plugin} eq 'favicon';
				my $plugin = "ServerPlugin::$+{plugin}";
				if (eval("require $plugin")) {
					$plugin->handle_request($c,$r,@args);
				}
				else {
					say "err message: $! - plugin: $plugin";
				}				
			}
			#$c->send_crlf;
			$c->close;	
		}
	}
	undef($c);
}




