#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package DlUtil;

use 5.012;
use warnings;

our $VERSION = v14;

use Log;

my $l = Log->new();

our($ua,@EXPORT,@EXPORT_OK);

require Exporter;

@EXPORT = qw();
@EXPORT_OK = qw($ua);


#initialises the user agent
sub _init_ua {
	$l->trace('initialise user agent');
	require LWP;
	require LWP::UserAgent;
	require LWP::ConnCache;
	#require HTTP::Status;
	#require HTTP::Date;
	$ua = new LWP::UserAgent;  # we create a global UserAgent object
	$ua->agent("vdlu/$VERSION");
	$ua->timeout(15);
	$ua->env_proxy;
	$ua->conn_cache(LWP::ConnCache->new());
	$ua->cookie_jar( {} );
}


#$url,$referer -> $response
#gets $url with referr $referer and returns the response object
sub get {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	$l->debug("get $url" .( $referer ? " (referer $referer)" : ''));
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	#$request->accept_decodable();
	my $res = $ua->request($request);
	#if ($res->header("Content-Encoding") and ($res->header("Content-Encoding") =~ m/none/i)) { #none eq identity - but HTTP::Message doesnt know!
	#	$res->header("Content-Encoding" => 'identity'); 
	#}
	$l->trace('response code: '. $res->code);
	return $res;
}

#$url,$referer -> $response
#gets head of $url with referr $referer and returns the response object
sub gethead {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	$l->debug("head $url" .( $referer ? " (referer $referer)" : ''));
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	my $res = $ua->head($url);
	$l->trace('response code: '. $res->code);
	return $res;
}

1;
