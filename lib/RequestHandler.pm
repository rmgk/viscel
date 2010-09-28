#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package RequestHandler;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;
use HTTP::Daemon;
use CGI;

use Core::Comcol;
use Collection::Ordered;
use Cache;

my $l = Log->new();

#$request -> \@args,$cgi
#extracts the arguments and the parameters from a request object
sub parse_request {
	my $r = shift;
	my @args;
	if ($r->url->path =~ m#^/(?<handler>\w+)/?(?<args>.*?)/?$#i) {
		@args = split('/',$+{args});
	}
	else {
		$l->warn('could not parse request');
	}
	return \@args,CGI->new($r->url->query);
}

#$c
#sends a default html response
sub send_response {
	my ($c,$html) = @_;
	my $res = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html; charset=utf-8']);
	$res->content($html);
	$c->send_response($res);
}

#$connection, $request
#handles index requests
sub index {
	my ($c,$r) = @_;
	$l->trace('handling index');
	my ($args,$cgi) = parse_request($r);
	my $html = $cgi->start_html(-title => 'index');
	$html .= join '', map {$cgi->a({href=>"/col/$_/1"},$_).$cgi->br()} @{Core::Comcol::list_ids()};
	$html .= $cgi->end_html();
	send_response($c,$html);
}

#$connection, $request
#handles col requests
sub col {
	my ($c,$r) = @_;
	$l->trace('handling collection');
	my ($args,$cgi) = parse_request($r);
	my $html = $cgi->start_html(-title => $args->[1]);
	my $col = Collection::Ordered->new({id=> $args->[0]});
	my $ent = $col->get($args->[1]);
	unless ($ent) {
		my $spot = Core::Comcol->create($args->[0],$args->[1]);
		return undef unless $spot;
		$spot->mount();
		$ent = $spot->fetch();
		$col->store($ent);
	} 
	$col->clean();
	$html .= $ent->html();
	$html .= $cgi->a({href=>"/col/". $args->[0] . "/" .($args->[1] + 1)},'next');
	$html .= $cgi->end_html();
	send_response($c,$html);
}



#$connection, $request
#handles blob requests
sub blob {
	my ($c,$r) = @_;
	$l->trace('handling blob');
	my ($args,$cgi) = parse_request($r);
	my $res = HTTP::Response->new( 200, 'OK');
	$res->content(${Cache::get($args->[0])});#
	$c->send_response($res);
}

1;
