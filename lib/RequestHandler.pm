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
use URI;

my $l = Log->new();
my $cgi;

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
sub handler {
	$_[0] =~ m'^/([^/]+)'; return $1;
}
sub absolute {
	return URI->new_abs($_[0],$_[1] || 'http://127.0.0.1');
}

#initialises the request handlers
sub init {
	Server::req_handler(handler(url_main()),\&index);
	Server::req_handler(handler(url_view('','')),\&view);
	Server::req_handler(handler(url_front('')),\&front);
	Server::req_handler('b',\&blob);
	Server::req_handler('css',\&css);
}

#sends the default css file
sub css {
	my ($c,$r) = @_;
	$c->send_file_response('default.css');
}

#$connection, $request
#handles index requests
sub index {
	my ($c,$r) = @_;
	$l->trace('handling index');
	my $html = cgi->start_html(-title => 'index',-style=>'/css');
	foreach my $core (Cores::list()) {
		my %collections = $core->list();
		$html .= cgi->start_div({-class=>'group'});
		$html .= join '', map {link_front($_,$collections{$_}).cgi->br()
			} sort {lc($collections{$a}) cmp lc($collections{$b})} keys %collections;
		$html .= cgi->end_div();
	}
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'index';
}

#$connection, $request
#handles view requests
sub view {
	my ($c,$r,$id,$pos) = @_;
	$l->trace('handling collection');
	if ($r->method eq 'POST') {
		$l->debug("set bookmark of $id to $pos");
		UserPrefs->block('bookmark')->set($id,$pos);
	}
	my $html = cgi->start_html(-title => $pos,-style=>'/css');
	my $col = Collection::Ordered->get($id);
	my $ent = $col->fetch($pos);
	unless ($ent) {
		my $last = $col->last();
		if ($last) {
			$l->debug("$pos not found redirecting to last $last");
			$c->send_redirect( absolute(url_view($id,$last)), 303 );
		}
		else {
			$l->debug("$pos not found redirecting to front");
			$c->send_redirect( absolute(url_front($id)), 303 );
		}
		return "view redirect";
	} 
	$l->trace('creating response content');
	$html .= cgi->start_div({-class=>'content'});
	$html .= $ent->html();
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
	$html .= link_view($id,($pos - 1),'prev') if ($pos - 1 > 0);
	$html .= ' ';
	$html .= link_front($id,'front');
	$html .= ' ';
	$html .= cgi->start_form(-method=>'POST',-action=>url_view($id,$pos),-enctype=>&CGI::URL_ENCODED);
	$html .= cgi->submit(-value => 'pause');
	$html .= cgi->end_form();
	$html .= ' ';
	$html .= cgi->a({href=>$ent->page_url(),-class=>'extern'},'site');
	$html .= ' ';
	$html .= link_view($id,($pos + 1),'next');
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return ['view',$id,$pos];
}

#$connection, $request
#handles front requests
sub front {
	my ($c,$r,$id) = @_;
	$l->trace('handling front request');
	my $html = cgi->start_html(-title => $id,-style=>'/css');
	$html .= cgi->start_div({-class=>'info'});
	my @info = Cores::about($id);
	while (my ($k,$v) = (splice(@info,0,2))) {
		$v //= ''; #/ padre display bug
		$html .= "$k: $v" . cgi->br();
	}
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
	$html .= link_main();
	$html .= ' - ';
	$html .= link_view($id,1,'first');
	my $bm = UserPrefs->block('bookmark');
	$html .= ' ';
	$html.= link_view($id,$bm->get($id),'Bookmark') if $bm->get($id); 
	$html .= ' ';
	$html .= link_view($id,-1,'last');
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return ['front',$id];
}

#$connection, $request
#handles blob requests
sub blob {
	my ($c,$r,$sha) = @_;
	$l->trace('handling blob');
	my $res = HTTP::Response->new( 200, 'OK');
	my $blob = Cache::get($sha);
	if ($blob) {
		$res->content(${$blob});
		$c->send_response($res);
	}
	else {
		Server::send_404($c);
	}
	return 'blob';
}

1;
