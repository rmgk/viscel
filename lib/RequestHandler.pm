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
use Collection;
use Cache;
use UserPrefs;
use URI;
use Time::HiRes qw(tv_interval gettimeofday);

my $l = Log->new();
my $cgi;

#$request -> $cgi
#returns the cgi object and possibly initialises its parameters
sub cgi {
	if ($_[0]) {
		$l->trace('parse parameters');
		return CGI->new($_[0]);
	}
	return $cgi;
}

#link and html generating helper functions

sub url_main { '/index' }
sub url_front { return "/f/$_[0]" };
sub url_view {"/v/$_[0]/$_[1]"}
sub url_config {"/c/$_[0]"}
sub url_action {join('/','/a',@_)};
sub url_search {"/s"};

sub link_main { cgi->a({-href=>url_main()}, $_[0] || 'index') }
sub link_front { cgi->a({-href=>url_front($_[0])}, $_[1]) }
sub link_view { cgi->a({-href=>url_view(@_)}, $_[2])}
sub link_config { cgi->a({-href=>url_config(@_)}, $_[1] || $_[0])}

sub handler { $_[0] =~ m'^/([^/]+)'; return $1; }
sub absolute { URI->new_abs($_[0],$_[1] || 'http://127.0.0.1') }


#$name,$url -> POST form
sub form_action {
	my $name = shift;
	#if the argument looks like a url, take it. else create a new action url
	my $url = ($_[0] =~ m'^/') ? $_[0] : url_action(@_);
	my $html .= cgi->start_form(-method=>'POST',-action=>$url,-enctype=>&CGI::URL_ENCODED);
	$html .= cgi->submit(-class=>'submit', -value => $name);
	$html .= cgi->end_form();
	return $html;
}

# -> search field
sub form_search {
	my $html .= cgi->start_form(-class=>'search', -method=>'GET',-action=>url_search());
	$html .= cgi->textfield(-name=>'q',-size=>35,-value=>$_[0]);
	$html .= cgi->end_form();
	return $html;
}

# -> notification div
sub html_notification { cgi->div({-class=>'notification'},$_[0]) }

#$title,%link_rel -> common html header and body start
sub html_header {
	my ($id,$title,%links) = @_; 
	$links{'index'} ||= url_main();
	my $html = cgi->start_html(	-style	=>	'/css',
						-title => $title,
						-id => $id,
						-head => [ map {cgi->Link({-rel=>$_,-href=>$links{$_}})} keys %links ]
					);
}

# -> info div containing status (and config links) of the cores
sub html_core_status {
	my $html .= cgi->start_div({-class=>'info'});
	for (Cores::list()) {
		$html .= link_config($_);
		if (!Cores::initialised($_)) {
			$html .= ' (not  ' . form_action('initialise','initialise',$_) .'d)';
		}
		$html .= cgi->br();
	}
	$html .= cgi->end_div();
	return $html;
}

#\%list_of_collections -> div containing the group ordered by name
sub html_group {
	my @collections = @_;
	my $html .= cgi->start_div({-class=>'group'});
	$html .= join '', map {link_front($_->[0],$_->[1]).cgi->br() } 
					sort {(($a->[2].$b->[2]) ~~ /^\d+$/)?  $a->[2] <=> $b->[2] : lc($a->[2]) cmp lc($b->[2])} @collections;
	$html .= cgi->end_div();
}

#@someinfo -> info div 
sub html_info {
	my $html .= cgi->start_div({-class=>'info'});
	$html .= cgi->start_table() . cgi->start_tbody();
	$html .= join '', map {cgi->Tr(cgi->td($_))} @_;
	$html .= cgi->end_tbody(). cgi->end_table();
	$html .= cgi->end_div();
	return $html;
}

#$core,$cfg -> html config list
sub html_config {
	my ($core,$cfg) = @_;
	my $html .= cgi->start_div({-class=>'info'});
		$html .= cgi->start_form(-method=>'POST',-action=>url_config($core),-enctype=>&CGI::URL_ENCODED);
		$html .= cgi->start_table();
		$html .= cgi->thead(cgi->Tr(cgi->td([undef,undef,cgi->strong('Description'),cgi->strong(' Default'),cgi->strong(' Expected')])));
		$html .= cgi->start_tbody();
		$html .= join cgi->br(), map { 
				cgi->Tr(cgi->td([ $_. ': ',
				cgi->textfield(-name=>$_,-value=>($cfg->{$_}->{current} // $cfg->{$_}->{default}),-size=>20), #/ padre display bug
				 $cfg->{$_}->{description} ,
				(defined $cfg->{$_}->{default} ? $cfg->{$_}->{default} : '' ) , 
				(defined $cfg->{$_}->{expected} ? $cfg->{$_}->{expected} : '') ]))
			} grep {!$cfg->{$_}->{action}} keys %$cfg;
		$html .= cgi->end_tbody(). cgi->end_table();
		$html .= cgi->submit(-class=>'submit',-value=>'update');
		$html .= cgi->end_form();
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'info'});
		$html .= join '', map {
				form_action($cfg->{$_}->{name},$cfg->{$_}->{action},$core).
				': '. $cfg->{$_}->{description}
			} grep {$cfg->{$_}->{action}} keys %$cfg;
	$html .= cgi->end_div();
	return $html;
}	

#initialises the request handlers
sub init {
	Server::req_handler(handler(url_main()),\&index);
	Server::req_handler(handler(url_view('','')),\&view);
	Server::req_handler(handler(url_front('')),\&front);
	Server::req_handler(handler(url_config('')),\&config);
	Server::req_handler(handler(url_action('')),\&action);
	Server::req_handler(handler(url_search('')),\&search);
	Server::req_handler('b',\&blob);
	Server::req_handler('css',\&css);
	$cgi = CGI->new() unless $cgi;
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
	$l->trace('handle index');
	my $html = html_header('index','index');
	$html .= html_core_status();
	$html .= cgi->start_div({-class=>'info'});
	$html .= form_search();
	$html .= cgi->end_div();
	my $bm = UserPrefs->section('bookmark');
	$html .= html_group( map {[$_ , (Cores::name($_)) x 2]} grep {$bm->get($_)} $bm->list() );
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'index';
}

#$connection, $request, $id, 4pos
#handles view requests
sub view {
	my ($c,$r,$id,$pos) = @_;
	$l->trace('handle collection');
	my $col = Collection->get($id);
	my $ent = $col->fetch($pos);
	unless ($ent) {
		my $last = $col->last();
		if ($last) {
			$l->debug("$pos not found redirect to last $last");
			$c->send_redirect( absolute(url_view($id,$last)), 303 );
		}
		else {
			$l->debug("$pos not found redirect to front");
			$c->send_redirect( absolute(url_front($id)), 303 );
		}
		return "view redirect";
	} 
	
	my $html = html_header('view',$pos);
	if ($r->method eq 'POST') {
		$l->debug("set bookmark of $id to $pos");
		UserPrefs->section('bookmark')->set($id,$pos);
		$html .= html_notification('bookmark updated');
	}
	$html .= cgi->start_div({-class=>'content'});
	$html .= $ent->html();
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_view($id,($pos - 1),'prev') if ($pos - 1 > 0);
		$html .= ' ';
		$html .= link_front($id,'front');
		$html .= ' ';
		$html .= form_action('pause',url_view($id,$pos));
		$html .= ' ';
		$html .= cgi->a({href=>$ent->page_url(),-class=>'extern'},'site');
		$html .= ' ';
		$html .= link_view($id,($pos + 1),'next');
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return ['view',$id,$pos];
}

#$connection, $request, $id
#handles front requests
sub front {
	my ($c,$r,$id) = @_;
	$l->trace('handle front request');
	my $bm = UserPrefs->section('bookmark')->get($id);
	my $html = html_header('front',$id);
	$html .= html_info(Cores::about($id));
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
		$html .= ' - ';
		$html .= link_view($id,1,'first');
		$html .= ' ';
		$html.= link_view($id,$bm,'Bookmark') if $bm; 
		$html .= ' ';
		$html .= link_view($id,-1,'last');
		$html .= ' - ';
		$html .= link_config($id,'config');
	$html .= cgi->end_div();
	my $col = Collection->get($id);
	my $ent = $col->fetch($bm) if $bm;
	if ($bm and $ent) {
		$html .= cgi->start_div({-class=>'content'});
			my $p = $col->fetch($bm-2);
			$html .= $p->html() if $p;
			$p = $col->fetch($bm-1);
			$html .= $p->html() if $p;
			$html .= $ent->html();
		$html .= cgi->end_div();
	}
	
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return ['front',$id];
}

#$connection, $request, $id
#handles front requests
sub config {
	my ($c,$r,$core) = @_;
	$l->trace('handle config request');
	my $cfg = Cores::config($core);
	my $html = html_header('config',"$core - config");
	if ($r->method eq 'POST') {
		$l->debug("change config " . $r->content());
		my $cgi = cgi($r->content());
		my %c;
		for (keys %$cfg) {
			if (ref $cfg->{$_}->{expected} and $cgi->param($_) ~~ $cfg->{$_}->{expected} ) {
				$c{$_} = $cgi->param($_)
			}
		}
		$cfg = Cores::config($core,%c);
		$html .= html_notification('update successful');
	}
	$html .= html_config($core,$cfg);
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return "config $core";
	#return ['config',$id];
}

#$connection, $request, @args
#handles action requests
sub action {
	my ($c,$r,@args) = @_;
	my $ret = 'action';
	$l->trace('handle action request');
	
	my $html = html_header('action','action');
	$html .= cgi->start_div({-class=>'info'});
	if ($r->method eq 'POST') {
		$l->debug("call action ". join ' ' , @args);
		my $cgi = cgi($r->content());
		$html .=  join '', map {$_ .': '.$cgi->param($_).cgi->br()} $cgi->param();
		given($args[0]) {
			when ('initialise') {
				my $core = $args[1];
				$l->debug("send action to initialise $core");
				$ret = sub {Cores::init($core)};
				$html .= 'initialising a core may take a while';
			}
			when ('halt') {
				$l->debug("give signal to exit");
				$html .= "bye";
				$ret = sub { exit(1) };
			}
			when ('getall') {
				$ret = ['getall',$args[1]];
				$html .= "this may take some time";
			}
			default {
				$l->warn('unknown action' . $_ ); 
				$html .= 'unknown action'; 
			}
		}
	}
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return $ret;
}

#$connection, $request, @args
#handles action requests
sub search {
	my ($c,$r,@args) = @_;
	$l->trace('handle search request');
	my $cgi = cgi($r->url->query());
	my $query = $cgi->param('q');
	my $time = [gettimeofday];
	my @result = Cores::search(split /\s+/ , $query);
	my $html = html_header('search','search');
	$html .= cgi->start_div({-class=>'info'});
		$html .= "search for " . cgi->strong($query).cgi->br() . @result . ' results';
		$html .= cgi->br() . tv_interval($time) . ' seconds' . cgi->br();
		$html .= form_search($query);
	$html .= cgi->end_div();
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= html_group(@result);
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return "search $query";
}

#$connection, $request
#handles blob requests
sub blob {
	my ($c,$r,$sha) = @_;
	$l->trace('handle blob');
	my $mtime =  HTTP::Date::str2time($r->header('If-Modified-Since'));
	my %stat = Cache::stat($sha);
	if (!$stat{size}) {
		Server::send_404($c);
	}
	elsif ($mtime and $stat{modified} <= $mtime) {
		$c->send_response(HTTP::Response->new( 304, 'Not Modified'));
	}
	else {
		my $res = HTTP::Response->new( 200, 'OK');
		my $blob = Cache::get($sha);
		if ($blob) {
			$res->content(${$blob});
			$res->header('Content-Type'=>$stat{type});
			$res->header('Content-Length'=>$stat{size});
			$res->header('Last-Modified'=>HTTP::Date::time2str($stat{modified}));
			$c->send_response($res);
		}
		else {
			Server::send_404($c);
		}
	}
	return 'blob';
}

1;
