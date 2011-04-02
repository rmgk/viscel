#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package DlUtil v1.17.0;

use 5.012;
use warnings;

use Log;
use LWP;
use LWP::UserAgent;
use LWP::ConnCache;
use HTML::TreeBuilder;

our $ua;

#initialises the user agent
sub _init_ua {
	Log->trace('initialise user agent');
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
	Log->debug("get $url" .( $referer ? " (referer $referer)" : ''));
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	$request->accept_decodable();
	my $res = $ua->request($request);
	given ($res->header("Content-Encoding")) { #do some encoding translations
		when (undef) {};
		when (/none/i) { $res->header("Content-Encoding" => 'identity'); }
		when (/bzip2/i) { $res->header("Content-Encoding" => 'x-bzip2'); }
	}
	defined($res->header('Content-Length')) or $res->header('Content-Length',length $res->content);
	Log->trace('response code: '. $res->code() .' ' . $res->message(). ' ('. $res->header('Content-Length') . 'Byte)');
	return $res;

}

#$url -> $tree
#fetches the url and returns the tree
sub get_tree {
	my ($url) = @_;
	my $page = get($url);
	if (!$page->is_success() or !$page->header('Content-Length')) {
		Log->error("error get: ", $url);
		return wantarray ? (undef, $page): undef;
	}
	Log->trace('parse HTML into tree');
	my $tree = HTML::TreeBuilder->new();
	my $content = $page->decoded_content();
	return unless $content;
	return unless $tree->parse_content($content);
	return wantarray ? (TreeKeeper->new($tree), $page): TreeKeeper->new($tree);
}

#$url,$referer -> $response
#gets head of $url with referr $referer and returns the response object
sub gethead {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	Log->debug("head $url" .( $referer ? " (referer $referer)" : ''));
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	my $res = $ua->head($url);
	Log->trace('response code: '. $res->code);
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
