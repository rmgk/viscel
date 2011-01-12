#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package DlUtil v1.16.0;

use 5.012;
use warnings;

use Log;
use LWP;
use LWP::UserAgent;
use LWP::ConnCache;
use HTML::TreeBuilder;

our $ua;

my $l = Log->new();

#initialises the user agent
sub _init_ua {
	$l->trace('initialise user agent');
	$ua = new LWP::UserAgent;  # we create a global UserAgent object
	$ua->agent("vdlu/".$DlUtil::VERSION->normal());
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
	Stats::add('get',$url);
	my $request = HTTP::Request->new(GET => $url);
	#$request->header(
		#Host: unixmanga.com
		#'Accept' => 'text/html;q=0.9, image/png, image/jpeg, image/gif, image/x-xbitmap, */*;q=0.1',
		#'Accept-Language' => 'en;q=0.9',
		#'Accept-Charset' => 'iso-8859-1, utf-8, *;q=0.1'
		#Accept-Encoding: deflate, gzip, x-gzip, identity, *;q=0
		#Cache-Control => 'no-cache',
		#Connection: Keep-Alive, TE
		#TE: deflate, gzip, chunked, identity, trailers
	#	);
	$request->referer($referer);
	$request->accept_decodable();
	my $res = $ua->request($request);
	given ($res->header("Content-Encoding")) { #do some encoding translations
		when (undef) {};
		when (/none/i) { $res->header("Content-Encoding" => 'identity'); }
		when (/bzip2/i) { $res->header("Content-Encoding" => 'x-bzip2'); }
	}
	defined($res->header('Content-Length')) or $res->header('Content-Length',length $res->content);
	Stats::add('got',$res->code(),$res->header('Content-Length'));
	$l->trace('response code: '. $res->code() .' ' . $res->message());
	return $res;

}

#$url -> $tree
#fetches the url and returns the tree
sub get_tree {
	my ($url) = @_;
	my $page = get($url);
	if (!$page->is_success() or !$page->header('Content-Length')) {
		$l->error("error get: ", $url);
		return wantarray ? (undef, $page): undef;
	}
	$l->trace('parse HTML into tree');
	my $tree = HTML::TreeBuilder->new();
	my $content = $page->decoded_content();
	return undef unless $content;
	return undef unless $tree->parse_content($content);
	return wantarray ? (TreeKeeper->new($tree), $page): TreeKeeper->new($tree);
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


#this autodeletes the tree when going out of scope
package TreeKeeper v1.0.0;

sub new {
	my ($pkg,$tree) = @_;
	return bless \$tree, $pkg;  
}
sub DESTROY {
	my $self = shift;
	$$self->delete(); 
}


1;
