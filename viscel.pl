#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself

use 5.012;
use strict;
use warnings;
use lib "./lib";

use threads;
use Thread::Queue;
use Log::Log4perl qw(:easy);

use HTTP::Date qw(time2str);

use dlutil;

use Server;
use Cores;



Log::Log4perl->easy_init($TRACE);

my $l = get_logger();

my $toq = Thread::Queue->new();
my $fromq = Thread::Queue->new();
	 
threads->create('getter', $toq, $fromq);

sub getter {
	my ($inq , $outq) = @_;
	while (my $url = $inq->dequeue()) {
		$l->info("enqueued " , $url);
		$outq->enqueue([$url,dlutil::get($url)]);
	}
}

my %f;

sub adder {
	my ($url,$cb) = @_;
	$toq->enqueue($url);
	$f{$url} = $cb;
}


my $inv  = Cores::get('Inverloch');
$inv->fetch(\&adder);

Server::init();
Server::req_handler('inverloch',sub {
my ($c,$r) = @_;
	$c->send_response(HTTP::Response->new( 200, 'OK', ['Last-Modified' , time2str(time),'Content-Type','image/jpeg'],$inv->get())); 
});



while (1) {
	Server::handle_connections();
	while (defined(my $item = $fromq->dequeue_nb())) {
		$f{$item->[0]}($item->[1]);
		$l->info("finished ", $item->[0]);
	}
}
