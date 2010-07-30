#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009

use 5.010;
use strict;
use warnings;
use lib "./lib";

use HTTP::Daemon;
use CGI qw(:standard);

use Socket;

#we use the standard plugins to get debug messages and such
use ServerPlugin qw(dbstrps);
use ServerPlugin::index;
use ServerPlugin::strips;
use ServerPlugin::css;
use ServerPlugin::front;
use ServerPlugin::pages;
use ServerPlugin::tools;
use ServerPlugin::striplist;
use ServerPlugin::pod;

our $VERSION = '3.0.3';

my $d = HTTP::Daemon->new(LocalAddr => "127.0.0.1" ,LocalPort => 80, Blocking => 1);
die "could not listen on port 80 - someones listening there already?" unless $d;

print "Please contact me at: <URL:", "http://127.0.0.1/index" ,">\n";
while (1) {
	if (my ($c, $addr) = $d->accept) {
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		#say "connection accepted";
		if (my $r = $c->get_request) {
		
			if ($addr_str ne '127.0.0.1') {
				say "from $addr_str : $port path:" . $r->url->path;
			}
			
			$c->force_last_request();
			#say "got request " . $r->method;
			if (($r->method eq 'GET')) {
				#say "handling get";
				if ($r->url->path =~ m#^/favicon.ico$#) {
					$c->send_response(HTTP::Response->new( 404, 'File Not Found'));
				}
				elsif ($r->url->path =~ m#^/(?<plugin>\w+)/?(?<args>.*?)/?$#i) {
					my @args = split('/',$+{args});
					my $plugin = "ServerPlugin::$+{plugin}";
					
					if ($plugin =~ m/strips$/i) {
						my $comic = $args[0];
						my $strip = $args[1];
						if ((not defined $comic) or (not defined $strip)) {
							$c->send_response(HTTP::Response->new( 500, 'Comic or Strip undefined'));
						} 
						elsif ($strip =~ /^\d+$/) {
							$strip = dbstrps($comic,'id'=>$strip,'file');
							unless ($strip) {
								$c->send_response(HTTP::Response->new( 500, 'strip id had no file'));
							} else {
								$c->send_redirect("http://127.0.0.1/strips/$comic/$strip" );
							}
						}
						else {
							$c->send_file_response("./strips/$comic/$strip");
						}
					}
					elsif (eval("require $plugin")) {
						#say "success $plugin";
						restore_parameters($r->url->query);
						my $res = $plugin->get_response();
						$res->content($plugin->get_content(@args));
						$c->send_response($res);
					}
					else {
						say "err message: $! - plugin: $plugin";
					}		
				}
			}
		}
		else {
			say "could not get request: " . $c->reason;
		}
	}
}



