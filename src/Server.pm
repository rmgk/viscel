#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Server v1.3.0;

use 5.012;
use warnings;
use utf8;

use Log;
use Config;
use HTTP::Daemon;
use Time::HiRes;
use Encode;

my $d;
my %req_handler;

#$port -> $daemon
#initialises the http server
sub init {
	my $port = Globals::port();
	Log->info("launch server on port $port");
	$d = HTTP::Daemon->new(LocalPort => $port);
	#setting the daemon timeout makes $d->accept() return immediately if there is no connection waiting
	#this timeout will also be the default timeout of the connection returned by $d->accept()
	#but that connection will never timeout if it has a timeout value of 0
	#$d->timeout(0);
	unless($d){
		Log->error("could not listen on port $port");
		return;
	}
	return 1;
}

#$path, \&request_handler -> \&request_handler
#sets the request handler for $path and returns it
sub register_handler {
	my ($path,$handler) = @_;
	unless ($path) {
		Log->error('path not specified');
		return;
	}
	if ($handler) {
		Log->trace('add request handler for ', $path); 
		$req_handler{$path} = $handler;
		return 1;
	}
}

#$timeout , $timespan -> $was_accepted
#waits $timeout seconds for a connections
#and handles all connections for $timespan seconds
#returns tue if at least one connection was accepted
sub accept {
	my ($timeout,$timespan) = @_;
	Log->trace('accept connections (timeout ', $timeout , ')');
	return unless _accept_connection($timeout,0.1); #connection timed out
	my $t = Time::HiRes::time;
	while (Time::HiRes::time - $t < $timespan) {
		_accept_connection($timespan - Time::HiRes::time + $t,0.1);
	}
	return 1;
}

#$timeout, $timespan
#$accepts connections for at most $timeout seconds, 
#and listens on them for $timespan $seconds
sub _accept_connection {
	my ($timeout,$timespan) = @_;
	$d->timeout($timeout);
	my $ls_time = Time::HiRes::time;
	if ((my ($c, $addr) = $d->accept)) {
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		Log->debug("connection from ",$addr_str ,":",$port, " after ", Time::HiRes::time - $ls_time);
		#the timout value should be big enough to let useragent sent multiple request on the same connection
		#but it should be also small enough that it times out shortly after all request for a given page
		#are made to allow the controller to do his work
		$c->timeout($timespan);
		_accept_requests($c,$addr_str);
		return 1;
	}
	return;
}

#$connection
#handles requests on the $connection
sub _accept_requests {
	my ($c,$addr) = @_;
	while (my $r = $c->get_request) {
		_handle_request($c,$r,$addr);
	}
	Log->trace("no more requests: " . $c->reason);
}

#$connection, $request
#dispatches the request to the request handler or sends a 404 error if no handler is registered
sub _handle_request {
	my ($c,$r,$addr) = @_;
	Log->debug("request: " , $r->method(), ' ', $r->url->as_string());
	if ($addr ne '127.0.0.1') {
		Log->error('non local address send 403 ', $addr, $r-> method(), $r->url->as_string());
		$c->send_response(HTTP::Response->new( 403, 'Forbidden',undef,'Forbidden'));
	}
	# if ($r->method() ne 'GET' and $r->method() ne 'HEAD' and $addr ne '127.0.0.1') {
		# Log->warn('non GET request from foreign address send 403');
		# $c->send_response(HTTP::Response->new( 403, 'Forbidden',undef,'You are only allowed to make GET requests'));
	# }
	if ($r->url->path eq '/') {
		if ($req_handler{'main'}) {
			return $req_handler{'main'}($c,$r);
		}
		else {
			send_404($c);
			return;
		}
	}
	elsif ($r->url->path =~ m#^/(?<path>[^/]+)/?(?<args>.*?)/?$#i) {
		my $path = $+{path};
		my @args = split '/',$+{args};
		if ($req_handler{$path}) {
			return $req_handler{$path}($c,$r,@args);
		}
		else {
			Log->error('unknown handler ', $path, $r);
			send_404($c);
			return;
		}
	}
	else {
		Log->error('unexpected url path: ', $r->url->path, $r);
		send_404($c);
		return;
	}
}

# ---- utility functions

#$c, $html
#sends a default html response
sub send_response {
	my ($c,$html) = @_;
	Log->trace('send response');
	my $res = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html; charset=UTF-8']);
	$res->content(encode('utf8',$html));
	$c->send_response($res);
}

#$c
#sends a 404 file not found response
sub send_404 {
	my ($c) = @_;
	Log->trace('send 404');
	my $res = HTTP::Response->new( 404, 'File Not Found',undef,'file not found');
	$c->send_response($res);
}

1;
