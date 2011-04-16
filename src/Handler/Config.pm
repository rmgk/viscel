#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Handler::Config v1.1.0;

use 5.012;
use warnings;
use utf8;

use Handler;

my $cgi;

#$core,$cfg -> html config list
sub html_config {
	my ($core,$cfg) = @_;
	my $html .= cgi->start_fieldset({-class=>'info'});
		$html .= cgi->legend('Settings');
		$html .= cgi->start_form(-method=>'POST',-action=>url_config($core),-enctype=>&CGI::URL_ENCODED);
		$html .= cgi->start_table();
		$html .= cgi->thead(cgi->Tr(cgi->td([undef,undef,cgi->strong(' Default'),cgi->strong('Description')])));
		$html .= cgi->start_tbody();
		$html .= join '', map { 
				cgi->Tr(cgi->td([ $_. ': ',
					$cfg->{$_}->{expected} eq 'bool' ? 
					cgi->checkbox($_,($cfg->{$_}->{current} // $cfg->{$_}->{default}),1,'') : #/ padre display bug
					cgi->textfield(-name=>$_,-value=>($cfg->{$_}->{current} // $cfg->{$_}->{default}),-size=>20), #/ padre display bug
				(defined $cfg->{$_}->{default} ? $cfg->{$_}->{default} : '' ) , 
				$cfg->{$_}->{description} ]))
			} grep {!$cfg->{$_}->{action}} keys %$cfg;
		$html .= cgi->end_tbody();
		$html .= cgi->tfoot(cgi->Tr(cgi->td(cgi->submit(-name=>'submit',-class=>'submit',-value=>'update'))));
		$html .= cgi->end_table();
		$html .= cgi->end_form();
	$html .= cgi->end_fieldset();

	$html .= cgi->start_fieldset({-class=>'info'});
		$html .= cgi->legend('Actions');
		$html .= join '', map {
				form_action($cfg->{$_}->{name},$cfg->{$_}->{action},$core).
				': '. $cfg->{$_}->{description} . cgi->br()
			} grep {$cfg->{$_}->{action}} keys %$cfg;
	$html .= cgi->end_fieldset();
	return $html;
}	

#initialises the request handlers
sub init {
	Server::register_handler(handler(url_config('')),\&config);
	Server::register_handler(handler(url_action('')),\&action);
}

#$connection, $request, $id
#handles front requests
sub config {
	my ($c,$r,$core) = @_;
	Log->trace('handle config request');
	my $cfg = Cores::config($core);
	my $html = html_header('config',"$core - config");
	my $ret = "config";
	if ($r->method eq 'POST') {
		Log->debug("change config " . $r->content());
		my $cgi = cgi($r->content());
		my %c;
		for (keys %$cfg) {
			next unless $cfg->{$_}->{expected};
			next unless $cgi->param('submit') eq 'update' or defined $cgi->param($_);
			if (ref $cfg->{$_}->{expected} and $cgi->param($_) ~~ $cfg->{$_}->{expected} ) {
				$c{$_} = $cgi->param($_);
			}
			elsif ($cfg->{$_}->{expected} eq 'bool') {
				$c{$_} = $cgi->param($_);
			}
		}
		$cfg = Cores::config($core,%c);
		UserPrefs::save();
		$html .= html_notification('update successful');
		$ret = ["config",$core];
	}
	$html .= html_config($core,$cfg);
	$html .= cgi->start_div({-class=>'navigation'});
		$html .= link_front($core,'front').' ' if $core !~ /:/;
		$html .= link_main();
	$html .= cgi->end_div();
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return $ret;
}

#$connection, $request, @args
#handles action requests
sub action {
	my ($c,$r,@args) = @_;
	my $ret = 'action';
	Log->trace('handle action request');
	
	my $html = html_header('action','action');
	$html .= cgi->start_div({-class=>'info'});
	if ($r->method eq 'POST') {
		Log->debug("call action ". join ' ' , @args);
		my $cgi = cgi($r->content());
		$html .=  join '', map {$_ .': '.$cgi->param($_).cgi->br()} $cgi->param();
		given($args[0]) {
			when ('initialise') {
				my $core = $args[1];
				Log->debug("send action to initialise $core");
				$ret = sub {Cores::init($core)};
				$html .= 'initialising a core may take a while';
			}
			when ('halt') {
				Log->debug("give signal to exit");
				$html .= "bye";
				$ret = sub { exit(1) };
			}
			when ('getall') {
				$ret = ['getall',$args[1]];
				$html .= "this may take some time";
			}
			when ('check') {
				$ret = ['check',$args[1]];
				$html .= "checking collection for inconsistencies";
			}
			when ('updatelist') {
				my $core = $args[1];
				$ret = sub {$core->update_list()};
				$html .= "this may take some time";
			}
			when ('export') {
				$ret = ['export',$args[1]];
				$html .= "please wait a moment";
			}
			default {
				Log->warn('unknown action' . $_ ); 
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

1;
