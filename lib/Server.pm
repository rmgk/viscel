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
	my $port = shift // $main::PORT;
	$l->info("launch server on port $port");
	$d = HTTP::Daemon->new(LocalPort => $port);
	#setting the daemon timeout makes $d->accept() return immediately if there is no connection waiting
	#this timeout will also be the default timeout of the connection returned by $d->accept()
	#but that connection will never timeout if it has a timeout value of 0
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
		$l->trace('add request handler for ', $path); 
		$req_handler{$path} = $handler ;
	}
	return $req_handler{$path};
}

#-> $bool
#returns 1 if an incoming connection was handled 0 if not
sub handle_connections {
	my ($timeout) = @_;
	$l->trace('accept connections (timout ', $timeout , ' )');
	$d->timeout($timeout); #we enter idle mode if we timout once, so we can do other stuff while still checking back for connections
	if (my ($c, $addr) = $d->accept) {
		$d->timeout($main::IDLE); # new connection -> no longer idle
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		$l->debug("connection accepted from ",$addr_str ,":",$port);
		#the timout value should be big enough to let useragent sent multiple request on the same connection
		#but it should be also small enough that it times out shortly after all request for a given page
		#are made to allow the controller to do his work
		$c->timeout(0.1);		
		return handle_connection($c,$addr_str)
	}
	
	return undef;
}

#$connection
#handles requests on the $connection
sub handle_connection {
	my ($c,$addr) = @_;
	$l->trace("handle connection");
	my @hint;
	while (my $r = $c->get_request) {
		push(@hint,handle_request($c,$r,$addr));
	}
	$l->debug("no more requests: " . $c->reason);
	return \@hint;
}

#$connection, $request
#dispatches the request to the request handler or sends a 404 error if no handler is registered
sub handle_request {
	my ($c,$r,$addr) = @_;
	$l->debug("handle request: " , $r->method(), ' ', $r->url->as_string());
	if ($r->method() ne 'GET' and $r->method() ne 'HEAD' and $addr ne '127.0.0.1') {
		$l->warn('non GET request from foreign address send 403');
		$c->send_response(HTTP::Response->new( 403, 'Forbidden',undef,'You are only allowed to make GET requests'));
	}
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
	$l->trace('send response');
	my $res = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html']);
	$res->content($html);
	$c->send_response($res);
}

#$c
#sends a 404 file not found response
sub send_404 {
	my ($c) = @_;
	$l->trace('send 404');
	my $res = HTTP::Response->new( 404, 'File Not Found',undef,'file not found');
	$c->send_response($res);
}

1;
