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

@EXPORT = qw(get gethead);
@EXPORT_OK = qw($ua);

our($VERSION);
$VERSION = '8';

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

	dlutil::get($url,$referer);
	
gets C<$url> with referer set to C<$referer> and returns contents. if C<$referer> is omitted it uses C<$url> as referer

returns: $response object

=cut

sub get {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	unless (defined $referer) {
		(my $referer = $url) =~ s/[\?\&]//;
		$referer =~ s#/[^/]*$#/#;
	}
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	my $response = $ua->request($request);
	return $response;
}

=head2 gethead

	dlutil::gethead($url,$referer);
	
gets header of C<$url> with referer set to C<$referer> and returns response object. if C<$referer> is omitted it uses C<$url> as referer

returns: response object

=cut

sub gethead {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	unless (defined $referer) {
		(my $referer = $url) =~ s/[\?\&]//;
		$referer =~ s#/[^/]*$#/#;
	}
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	my $response = $ua->head($url);
	return $response;
}

1;
