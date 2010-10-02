#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Server;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;
use HTTP::Daemon;

my $d;
my $l = Log->new();
my %req_handler;

#$port -> $daemon
#initialises the http server
sub init {
	my $port = shift // 80;
	$l->info("launching server on port $port");
	$d = HTTP::Daemon->new(LocalPort => $port);
	#$d->timeout(0);
	unless($d){
		$l->error("could not listen on port $port");
		return undef;
	}
	return 1;
}

#$path, \&request_handler -> \&request_handler
#sets the request handler for $path and returns it
sub req_handler {
	my ($path,$handler) = @_;
	unless ($path) {
		$l->error('path not specified');
		return undef;
	}
	if ($handler) {
		$l->debug('adding request handler for ', $path); 
		$req_handler{$path} = $handler ;
	}
	return $req_handler{$path};
}

#-> $bool
#returns 1 if an incoming connection was handled 0 if not
sub handle_connections {
	#my @hint;
	$l->trace('accepting connections');
	if (my ($c, $addr) = $d->accept) {
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		$l->debug("connection accepted from ",$addr_str ,":",$port);
		$c->timeout(0.1);
		#push (@hint, handle_connection($c));
		return handle_connection($c)
	}
	#return \@hint;
	return undef;
}

#$connection
#handles requests on the $connection
sub handle_connection {
	my $c = shift;
	$l->trace("handling connection");
	my @hint;
	while (my $r = $c->get_request) {
		push(@hint,handle_request($c,$r));
	}
	$l->debug("no more requests: " . $c->reason);
	return \@hint;
}

#$connection, $request
#dispatches the request to the request handler or sends a 404 error if no handler is registered
sub handle_request {
	my ($c,$r) = @_;
	$l->debug("handling request: " , $r->method(), ' ', $r->url->as_string());
	#$l->trace($r->as_string());
	if ($r->url->path eq '/') {
		$c->send_redirect( "http://127.0.0.1/index",301);
	}
	elsif ($r->url->path =~ m#^/(?<path>[^/]+)/?(?<args>.*?)/?$#i) {
		my $path = $+{path};
		my @args = split '/',$+{args};
		if ($req_handler{$path}) {
			return $req_handler{$path}($c,$r,@args);
		}
		else {
			send_404($c);
			return undef;
		}
	}
	else {
		$l->warn('unexpected url path: ', $r->url->path);
		send_404($c);
		return undef;
	}
}


#$c
#sends a default html response
sub send_response {
	my ($c,$html) = @_;
	$l->trace('sending response');
	my $res = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html']);
	$res->content($html);
	$c->send_response($res);
}

#$c
#sends a 404 file not found response
sub send_404 {
	my ($c) = @_;
	$l->trace('sending 404');
	my $res = HTTP::Response->new( 404, 'File Not Found',undef,'file not found');
	$c->send_response($res);
}

1;
