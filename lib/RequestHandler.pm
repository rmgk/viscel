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
use Core::AnyManga;
use Collection::Ordered;
use Cache;

my $l = Log->new();
our $ADDR = '127.0.0.1';

#$request -> \@args,$cgi
#extracts the arguments and the parameters from a request object
sub parse_request {
	my $r = shift;
	$l->trace('parse request');
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
	$l->trace('sending response');
	my $res = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html; charset=utf-8']);
	$res->content($html);
	$c->send_response($res);
}

sub send_404 {
	my ($c) = @_;
	$l->trace('sending 404');
	my $res = HTTP::Response->new( 404, 'File Not Found');
	$c->send_response($res);
}

#$connection, $request
#handles index requests
sub index {
	my ($c,$r) = @_;
	$l->trace('handling index');
	my ($args,$cgi) = parse_request($r);
	my $html = $cgi->start_html(-title => 'index');
	my $collections = Core::AnyManga::list();
	$html .= join '', map {$cgi->a({href=>"/col/$_/1"},$collections->{$_}->{name}).$cgi->br()} keys %$collections;
	#$html .= join '', map {$cgi->a({href=>"/col/$_/1"},$_).$cgi->br()} @{Core::Comcol::list_ids()};
	$html .= $cgi->end_html();
	send_response($c,$html);
	return 'index';
}

#$connection, $request
#handles col requests
sub col {
	my ($c,$r) = @_;
	$l->trace('handling collection');
	my ($args,$cgi) = parse_request($r);
	my $id = $args->[0];
	my $pos = $args->[1];
	my $html = $cgi->start_html(-title => $pos);
	my $col = Collection::Ordered->get($id);
	my $ent = $col->fetch($pos);
	unless ($ent) {
		if ($pos == 1) { #requesting first
			my $spot;
			$spot = Core::AnyManga->first($id);
			
			unless ($spot) {
				$l->warn('could not fetch spot');
				$col->clean();
				return send_404($c);
			}
			$spot->mount();
			$ent = $spot->fetch();
			unless ($ent) {
				$l->error('could not fetch entity');
				$col->clean();
				return send_404($c); 
			}
			$col->store($ent);
		}
		else { #redirecting to last
			my $last = $col->last();
			$last ||= 1;
			$l->debug("$pos not found redirecting to last $last");
			$c->send_redirect( "http://$ADDR/col/$id/".$last, 303 );
			return [$id,$last];
		}
	} 
	$col->clean();
	$l->trace('creating response content');
	$html .= $ent->html();
	$html .= $cgi->a({href=>"/col/$id/" .($pos + 1)},'next');
	$html .= $cgi->end_html();
	send_response($c,$html);
	return [$id,$pos];
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
	return 'blob';
}

1;
