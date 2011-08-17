#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Handler v1.3.0;

use 5.012;
use warnings;
use utf8;

use parent qw( Exporter );
use Log;
use HTTP::Daemon;
use CGI;
use URI;

#export basically everything (except init)
our @EXPORT = qw(absolute cgi form_action form_action_input form_search
handler html_core_status html_group html_header html_info html_notification 
link_config link_front link_main link_search link_view 
url_action url_config url_front url_main url_search url_tools url_view);

my $cgi;

#$request -> $cgi
#returns the cgi object and possibly initialises its parameters
sub cgi {
	if ($_[0]) {
		Log->trace('parse parameters');
		return CGI->new($_[0]);
	}
	return $cgi;
}

sub init {
	$cgi = CGI->new() unless $cgi;
	return 1;
}

#link and html generating helper functions

sub url_main { '/main' }
sub url_front { return "/f/$_[0]" }
sub url_view {"/v/$_[0]/$_[1]"}
sub url_config {"/c/$_[0]"}
sub url_action {join('/','/a',@_)}
sub url_search {"/s"};
sub url_tools {"/tools"};

sub link_main { cgi->a({-href=>url_main()}, $_[0] || 'index') }
sub link_front { cgi->a({-href=>url_front($_[0]),UserPrefs::get('bookmark',$_[0])?(-class=>'bookmark'):()}, $_[1]) }
sub link_view { cgi->a({-href=>url_view(@_)}, $_[2])}
sub link_config { cgi->a({-href=>url_config(@_)}, $_[1] || $_[0])}
sub link_search { cgi->a({-href=>url_search().'?q='.$_[1]},$_[0]) }

sub handler { $_[0] =~ m'^/([^/]+)'; return $1; }
sub absolute { URI->new_abs($_[0],$_[1] || 'http://127.0.0.1:' . Globals::port() ) }


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

#$name,@actionparam -> POST form
sub form_action_input {
	my $name = shift;
	my $html .= cgi->start_form(-class=>'search', -method=>'POST',-action=>url_action(@_),-enctype=>&CGI::URL_ENCODED);
	$html .= cgi->textfield(-name=>$name,-size=>35);
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
						-encoding => 'UTF-8',
						-head => [ map {cgi->Link({-rel=>$_,-href=>$links{$_}})} keys %links ]
					);
}

# -> info div containing status (and config links) of the cores
sub html_core_status {
	my $html .= cgi->start_fieldset({-class=>'info'});
	$html .= cgi->legend('Core Status');
	for (Cores::list()) {
		$html .= link_search($_,$_.':');
		if (!Cores::initialised($_)) {
			$html .= ' (not  ' . form_action('initialise','initialise',$_) .'d)';
		}
		$html .= cgi->br();
	}
	$html .= cgi->end_fieldset();
	return $html;
}

#\%list_of_collections -> div containing the group ordered by name
sub html_group {
	my ($name,@collections) = @_;
	my $html .= cgi->start_fieldset({-class=>'group'});
	$html .= cgi->legend($name);
	$html .= join '', map {link_front($_->[0],$_->[1]).cgi->br() } 
					sort {(($a->[2].$b->[2]) ~~ /^\d+$/)?  $a->[2] <=> $b->[2] : lc($a->[2]) cmp lc($b->[2])} @collections;
	$html .= cgi->end_fieldset();
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

1;
