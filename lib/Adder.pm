#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Adder v1.2.0;

use 5.012;
use warnings;

use Log;
use Stats;
use Server;
use Cores;
use Cache;
use Collection;
use Core::Universal;
use Handler;
use Handler::Misc;
use DlUtil;

my $l = Log->new();

#initialises the needed modules
sub init {
	$l->trace('initialise modules');
	if (
		Stats::init() &&
		Cache::init() &&
		Collection::init() &&
		Server::init() &&
		Handler::init() &&
		Core::Universal->init()
	) {
		Server::register_handler(handler(url_main()),\&add);
		Server::register_handler('css',\&Handler::Misc::css);
		Server::register_handler('die',sub { die });
		return 1;
	}
	$l->error('failed to initialise modules');
	return undef;
}

#starts the program, never returns
sub start {
	$l->trace('run main loop');
	while (1) {
		Server::accept(600,0.5);
	}
}

my @images;
my $name;
my $url;
my $id;
my $tree;
my $image;
my @tags;

sub add { 
	my ($c,$r) = @_;
	$l->trace('handle add');
	my $html = html_header('add','add');
	my $cgi = cgi($r->content());
	if ($r->method eq 'POST') {
		if ($cgi->param('submit') eq 'step3') {
			open(my $FH,'>>','uclist.txt') or die('could not open uclist.txt');
			print $FH "$id => [\n\t'$name',\n\t'$url',\n\t" .
			(join ', ', map {my %t = %$_ ; "[" . (join ', ', map {$t{$_}? "$_ => '" . $t{$_} ."'" : ''} keys %t) .']'} @tags) . 
			"\n],\n";
			close $FH;
			die "complete";
		}
		elsif ($cgi->param('submit') eq 'step2') {
			$image = $images[$cgi->param('image')];
			my $e = $image;
			while ($e->parent) {
				unshift @tags , {_tag => $e->tag(), id => $e->attr('id'), class => $e->attr('class')};
				$e = $e->parent();
			}
			
			$id = $name;
			$id =~ s/[^a-zA-Z]//g;
			my $id = 'Universal_' . $id;
			my $clist = {$id => { name => $name,
								  url_start => $url,
								  criteria => [map {[%$_]} @tags]
				}};
			die "id already used" if keys %{Core::Universal->clist($id)};
			Core::Universal->clist($clist);
			my $remote = Core::Universal->new($id);
			my $spot = $remote->first();
			unless ($spot->mount() and ($spot->{src} eq $image->attr('src'))) {
				die "does not match: " . $spot->{src} . "  " . $image->attr('src') ;
			}
			my $tree2 = DlUtil::get_tree($spot->{next});
			my @images2 = $tree2->look_down(_tag => 'img');
			$_->attr('src',URI->new_abs($_->attr('src'),$url)->as_string()) for @images2;
			
			my @notsame = @images;
			for my $i (@images2) {
				@notsame = grep {$_->attr('src') ne $i->attr('src')} @notsame;
			}
			$l->info(join "\n", map {$_->attr('src') } @notsame);
			
			$spot = $spot->next();
			if ($spot->mount() and $spot->{src}) {
				$html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_main(),-enctype=>&CGI::URL_ENCODED);
					$html .= "is this the second image?";
					$html .= $cgi->img(src => $spot->{src});
					$html .= cgi->br();
					$html .= cgi->submit(-name=>'submit', -value => 'step3');
					
				$html .= cgi->end_form();
			}
			die "could not find second page";
		}
		else {
			$name = $cgi->param('name');
			$url = $cgi->param('url');
			$tree = DlUtil::get_tree($url);
			
			@images = $tree->look_down(_tag => 'img');
			
			$_->attr('src',URI->new_abs($_->attr('src'),$url)->as_string()) for @images;
			
			$html .= join cgi->hr() , map { $_ . ' : ' . $images[$_]->as_HTML } 0..$#images;
			$html .= cgi->hr();
			
			$html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_main(),-enctype=>&CGI::URL_ENCODED);
				$html .= cgi->radio_group(-name=>'image',
									 -values=>[0..$#images],
									 -linebreak=>'true');
				$html .= cgi->br();
				$html .= cgi->submit(-name=>'submit', -value => 'step2');
				
			$html .= cgi->end_form();
        }
	}
	else {
		$html .= cgi->start_fieldset({-class=>'info'});
		$html .= cgi->legend('Add');
		
		$html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_main(),-enctype=>&CGI::URL_ENCODED);
			$html .= cgi->textfield(-name=>'name',-size=>35,-value=>'name');
			$html .= cgi->textfield(-name=>'url',-size=>50,-value=>'url start');
			$html .= cgi->submit(-name=>'submit', -value => 'step1');
		$html .= cgi->end_form();
		
		$html .= cgi->end_fieldset();
	}
	
	
	$html .= $cgi->end_html();
	Server::send_response($c,$html);
	return 'index';
}

1;
