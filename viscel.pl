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

use HTTPServer;



Log::Log4perl->easy_init($TRACE);

my $l = get_logger();

my $toq = Thread::Queue->new();
my $fromq = Thread::Queue->new();
	 
#threads->create('factorizer', $toq, $fromq);

my %f;
sub factorizer {
	my ($inq , $outq) = @_;
	while (my $num = $inq->dequeue()) {
		$l->info("enqueued " , $num);
		$outq->enqueue([$num,get("http://inverloch.seraph-inn.com/pages/$num.jpg")->content()]);

	}
}

sub factors {
	my ($num) = @_;
	$l->info("query ",$num);
	if (!defined $f{$num}) {
		$toq->enqueue($num);
		$f{$num} = HTTP::Response->new( 200, 'OK', ['Content-Type','text/html; charset=iso-8859-1']);
		$f{$num}->content("please wait");
	}
	return $f{$num};
}


HTTPServer::init();
HTTPServer::req_handler('kekse',sub {
my ($c,$r) = @_;
	$c->send_response(HTTP::Response->new( 200, 'OK', ['Last-Modified' , time2str(time)],'<!DOCTYPE html><head><title>kekse für alle</title></head><body><a href="/kekse">kekse für alle</a></body>' )); 
});



while (1) {
	#$l->debug("checking for connections");
	HTTPServer::handle_connections();
	while (defined(my $item = $fromq->dequeue_nb())) {
		$f{$item->[0]} = HTTP::Response->new( 200, 'OK', ['Content-Type','image/jpg']);
		$f{$item->[0]}->content($item->[1]);

		$l->info("finished ", $item->[0]);
	}
}
