#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package Server;

use 5.012;
use strict;
use warnings;

use HTTP::Daemon;
use Log::Log4perl qw(get_logger);

our $VERSION = '1';
my $d;
my $l = get_logger();
my %req_handler;

sub init {
	my $port = shift // 80;
	$l->info("launching server on port $port");
	$d = HTTP::Daemon->new(LocalPort => $port);
	return $d or $l->logdie("could not listen on port $port");
}

sub req_handler {
	my ($path,$handler) = @_;
	if ($handler) {
		$l->debug('adding request handler for ', $path); 
		$req_handler{$path} = $handler ;
	}
	return $req_handler{$path};
}

sub handle_connections {
	if (my ($c, $addr) = $d->accept) {
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		$l->debug("connection accepted from ",$addr_str ,":",$port);
		$c->timeout(1);
		handle_connection($c);
		return 1;
	}
	return;
}

sub handle_connection {
	my $c = shift;
	$l->trace("handling connection");
	while (my $r = $c->get_request) {
		handle_request($c,$r);
	}
	$l->debug("no more requests: " . $c->reason);
}

sub handle_request {
	my ($c,$r) = @_;
	$l->debug("handling request: " , $r->method(), ' ', $r->url->as_string());
	$l->trace($r->as_string());
	if ($r->url->path =~ m#^/([^/]*)#) {
		my $path = $1;
		if ($path ne '' and $req_handler{$path}) {
			$req_handler{$path}($c,$r);
		}
		else {
			$l->debug('sending 404');
			$c->send_response(HTTP::Response->new( 404, 'File Not Found'));
		}
	}
	else {
		$l->warn('unexpected uri path: ', $r->url->path);
	}
}

# while (my ($c, $addr) = $d->accept) {
	# my ($port, $iaddr) = sockaddr_in($addr);
	# my $addr_str = inet_ntoa($iaddr);
	# #say "connection accepted";
	# if (my $r = $c->get_request) {
	
		# if ($addr_str ne '127.0.0.1') {
			# say "from $addr_str : $port path:" . $r->url->path;
		# }
		
		# $c->force_last_request();
		# #say "got request " . $r->method;
		# if (($r->method eq 'GET')) {
			# #say "handling get";
			# if ($r->url->path =~ m#^/favicon.ico$#) {
				# $c->send_response(HTTP::Response->new( 404, 'File Not Found'));
			# }
			# elsif ($r->url->path =~ m#^/(?<plugin>\w+)/?(?<args>.*?)/?$#i) {
				# my @args = split('/',$+{args});
				# my $plugin = "ServerPlugin::$+{plugin}";
				
				# if ($plugin =~ m/strips$/i) {
					# my $comic = $args[0];
					# my $strip = $args[1];
					# if ((not defined $comic) or (not defined $strip)) {
						# $c->send_response(HTTP::Response->new( 500, 'Comic or Strip undefined'));
					# } 
					# elsif ($strip =~ /^\d+$/) {
						# $strip = dbstrps($comic,'id'=>$strip,'file');
						# unless ($strip) {
							# $c->send_response(HTTP::Response->new( 500, 'strip id had no file'));
						# } else {
							# $c->send_redirect("http://127.0.0.1/strips/$comic/$strip" );
						# }
					# }
					# else {
						# $c->send_file_response("./strips/$comic/$strip");
					# }
				# }
				# elsif (eval("require $plugin")) {
					# #say "success $plugin";
					# restore_parameters($r->url->query);
					# my $res = $plugin->get_response();
					# $res->content($plugin->get_content(@args));
					# $c->send_response($res);
				# }
				# else {
					# say "err message: $! - plugin: $plugin";
				# }		
			# }
		# }
	# }
	# else {
		# say "could not get request: " . $c->reason;
	# }
# }




1;