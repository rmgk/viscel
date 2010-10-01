#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package RequestHandler;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;
use HTTP::Daemon;
use CGI;

use Cores;
use Collection::Ordered;
use Cache;
use UserPrefs;

my $l = Log->new();
my $cgi;
our $ADDR = '127.0.0.1';

#$request -> $cgi
#returns the cgi object and possibly initialises its parameters
sub cgi {
	if (ref $_[0]) {
		$l->trace('parse query parameters');
		$cgi = CGI->new($_[0]->url->query);
	}
	$cgi = CGI->new() unless $cgi;
	return $cgi;
}

#link generating functions
sub link_main {		return cgi->a({-href=>url_main(@_)}, $_[0] // 'index') } #/ padre dispaly bug
sub url_main { return '/index' }
sub link_front {	return cgi->a({-href=>url_front(@_)}, $_[1] // $_[0] ) } #/ padre display bug
sub url_front { return "/f/$_[0]" };
sub link_view {		return cgi->a({-href=>url_view(@_)}, $_[2] // $_[1])} #/ padre dispaly bug
sub url_view {"/v/$_[0]/$_[1]"}

#initialises the request handlers
sub init {
	Server::req_handler('index',\&index);
	Server::req_handler('v',\&col);
	Server::req_handler('b',\&blob);
}

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
	return @args;
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
	my $html = cgi->start_html(-title => 'index');
	my $bm = UserPrefs->block('bookmark');
	foreach my $core (Cores::list()) {
		my $collections = $core->list();
		$html .= cgi->start_div({-style=>'border: solid black; padding: 1em; margin: 1em;'});
		$html .= join '', map {link_view($_,1,$collections->{$_}->{name}).
			($bm->get($_) ? link_view($_,$bm->get($_),' Bookmark') . cgi->br() : cgi->br())
			} sort {lc($collections->{$a}->{name}) cmp lc($collections->{$b}->{name})} keys %$collections;
		$html .= cgi->end_div();
	}
	$html .= cgi->end_html();
	send_response($c,$html);
	return 'index';
}

#$connection, $request
#handles col requests
sub col {
	my ($c,$r) = @_;
	$l->trace('handling collection');
	my ($id,$pos) = parse_request($r);
	if ($r->method eq 'POST') {
		$l->debug("set bookmark of $id to $pos");
		UserPrefs->block('bookmark')->set($id,$pos);
	}
	my $html = cgi->start_html(-title => $pos, -bgcolor=>'black');
	my $col = Collection::Ordered->get($id);
	my $ent = $col->fetch($pos);
	unless ($ent) {
		if ($pos == 1) { #requesting first
			my $spot;
			$spot = $col->core->first($id);
			
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
			$c->send_redirect( "http://$ADDR".url_view($id,$last), 303 );
			return "read redirect";
		}
	} 
	$col->clean();
	$l->trace('creating response content');
	$html .= cgi->start_div({-style => 'text-align: center;'});
	$html .= $ent->html();
	$html .= cgi->end_div();
	$html .= cgi->start_div({-style => 'text-align: center;'});
	$html .= link_view($id,($pos - 1),'prev') if ($pos - 1 > 0);
	$html .= ' ';
	$html .= cgi->start_form(-method=>'POST',-action=>url_view($id,$pos),-enctype=>&CGI::URL_ENCODED, -style => 'display:inline');
	$html .= cgi->submit(-value => 'pause', -style => 'border-style:none; background:transparent; color:grey; padding:0; cursor:pointer;');
	$html .= cgi->end_form();
	$html .= ' ';
	$html .= link_main();
	$html .= ' ';
	$html .= link_view($id,($pos + 1),'next');
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	send_response($c,$html);
	return [$id,$pos];
}



#$connection, $request
#handles blob requests
sub blob {
	my ($c,$r) = @_;
	$l->trace('handling blob');
	my ($sha) = parse_request($r);
	my $res = HTTP::Response->new( 200, 'OK');
	$res->content(${Cache::get($sha)});
	$c->send_response($res);
	return 'blob';
}

1;
