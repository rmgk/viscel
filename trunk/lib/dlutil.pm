package dlutil;
use strict;

our($ua,@EXPORT,@EXPORT_OK);

require Exporter;

@EXPORT = qw(get);
@EXPORT_OK = qw($ua);

our($VERSION);
$VERSION = '4';

sub _init_ua {
	require LWP;
	require LWP::UserAgent;
	require LWP::ConnCache;
	#require HTTP::Status;
	#require HTTP::Date;
	$ua = new LWP::UserAgent;  # we create a global UserAgent object
	$ua->agent("comcol/$::VERSION");
	$ua->timeout(15);
	$ua->env_proxy;
	$ua->conn_cache(LWP::ConnCache->new());
}	

sub get {
	my $url = shift;
	_init_ua() unless $ua;
	
	my $request = HTTP::Request->new(GET => $url);
	my $response = $ua->request($request);
	return $response->is_success ? $response->content : $response->code;
}

sub getref {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	unless (defined $referer) {
		(my $referer = $url) =~ s/[\?\&]//;
		$referer =~ s#/[^/]*$#/#;
	}
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	my $response = $ua->request($request);
	return $response->is_success ? $response->content : $response->code;
}

1;