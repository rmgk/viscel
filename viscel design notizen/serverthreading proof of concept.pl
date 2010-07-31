use 5.012;

use warnings;

use HTTP::Daemon;
use Data::Dumper;
use threads;
use Thread::Queue;

use Log::Log4perl qw(:easy);
Log::Log4perl->easy_init($DEBUG);



my $l = get_logger();

my $toq = Thread::Queue->new();
my $fromq = Thread::Queue->new();
	 
threads->create('factorizer', $toq, $fromq);

my $d = HTTP::Daemon->new(LocalPort => 80);
$d->timeout(0);
$l->logdie("could not listen on port 80 - someones listening there already?") unless $d;

$l->info("Please contact me at: <URL:", "http://127.0.0.1/" ,">");
my %f;
my $number;
my $ua;
&_init_ua();


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

sub handlereq {
	if (my ($c, $addr) = $d->accept) {
		my ($port, $iaddr) = sockaddr_in($addr);
		my $addr_str = inet_ntoa($iaddr);
		$l->debug("connection accepted from ",$addr_str ,":",$port);
		while (my $r = $c->get_request) {
			
			$l->debug("request on ", $r->url->path , " method ", $r->method);
			
			if (($r->method eq 'GET')) {
				if ($r->url->path =~ m#^/favicon.ico$#) {
					$l->debug("send not found response");
					$c->send_response(HTTP::Response->new( 404, 'File Not Found'));
				}
				elsif ($r->url->path =~ m#^/\D*(\d*)#) {
					my $num = int($1);
					$l->debug("send some response");
					$c->send_response(&factors($num));
				}
			}
		}
		$l->debug("could not get request: " . $c->reason);
	}
}

sub _init_ua {
	require LWP;
	require LWP::UserAgent;
	require LWP::ConnCache;
	#require HTTP::Status;
	#require HTTP::Date;
	$ua = new LWP::UserAgent;  # we create a global UserAgent object
	$ua->agent("cookiemonster");
	$ua->timeout(15);
	$ua->env_proxy;
	$ua->conn_cache(LWP::ConnCache->new());
	$ua->cookie_jar( {} );
}

sub get {
	my($url, $referer) = @_;
	_init_ua() unless $ua;
	my $request = HTTP::Request->new(GET => $url);
	$request->referer($referer);
	#$request->accept_decodable();
	my $res = $ua->request($request);
	#if ($res->header("Content-Encoding") and ($res->header("Content-Encoding") =~ m/none/i)) { #none eq identity - but HTTP::Message doesnt know!
	#	$res->header("Content-Encoding" => 'identity'); 
	#}
	return $res;
}



while (1) {
	#$l->debug("checking for connections");
	&handlereq();
	while (defined(my $item = $fromq->dequeue_nb())) {
		$f{$item->[0]} = HTTP::Response->new( 200, 'OK', ['Content-Type','image/jpg']);
		$f{$item->[0]}->content($item->[1]);

		$l->info("finished ", $item->[0]);
	}
}
