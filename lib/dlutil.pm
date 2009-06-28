#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:11 11.04.2009
package dlutil;

use 5.010;
use strict;
use warnings;

=head1 NAME

dlutil - download utility

=head1 DESCRIPTION

provides download utility functions

=cut

our($ua,@EXPORT,@EXPORT_OK);

require Exporter;

@EXPORT = qw(get);
@EXPORT_OK = qw($ua);

our($VERSION);
$VERSION = '6';

=head1 functions

=cut

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
	$ua->cookie_jar( {} );
}	

=head2 get

	dlutil::get($url);
	
gets C<$url> and returns contents.

returns: fetched content on success, errorcode otherwise.

=cut

sub get {
	my $url = shift;
	_init_ua() unless $ua;
	my $request = HTTP::Request->new(GET => $url);
	my $response = $ua->request($request);
	return $response->is_success ? $response->content : $response->code;
}

=head2 getref

	dlutil::getref($url,$referer);
	
gets C<$url> with referer set to C<$referer> and returns contents. if C<$referer> is omitted it uses C<$url> as referer

returns: fetched content on success, errorcode otherwise.

=cut

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