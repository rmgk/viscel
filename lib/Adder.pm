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
use Data::Dumper;

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
		Server::register_handler(handler(url_main()),\&main);
		Server::register_handler('css',\&Handler::Misc::css);
		Server::register_handler('die',sub { die });
		Server::register_handler(handler(url_add()),\&add);
		return 1;
	}
	$l->error('failed to initialise modules');
	return undef;
}

sub url_add { '/add' }

#starts the program, never returns
sub start {
	$l->trace('run main loop');
	while (1) {
		Server::accept(600,0.5);
	}
}

sub main { 
	my ($c,$r) = @_;
	$l->trace('handle main');
	my $html = html_header('main','main');

	$html .= cgi->start_fieldset({-class=>'info'});
	$html .= cgi->legend('Add');
	
	$html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_add(),-enctype=>&CGI::URL_ENCODED);
		$html .= cgi->textfield(-name=>'name',-size=>35,-value=>'name');
		$html .= cgi->textfield(-name=>'url',-size=>50,-value=>'url start');
		$html .= cgi->submit(-name=>'submit', -value => 'step1');
	$html .= cgi->end_form();
	
	$html .= cgi->end_fieldset();

	
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'main';
}

sub add {
	my ($c,$r) = @_;
	$l->trace('handle add');
	my $html = html_header('add','add');
	
	my $cgi = cgi($r->content());
	if ($r->method eq 'POST') {
		# if ($cgi->param('submit') eq 'step3') {
			# open(my $FH,'>>','uclist.txt') or die('could not open uclist.txt');
			# print $FH "$id => [\n\t'$name',\n\t'$url',\n\t" .
			# (join ', ', map {my %t = %$_ ; "[" . (join ', ', map {$t{$_}? "$_ => '" . $t{$_} ."'" : ''} keys %t) .']'} @tags) . 
			# "\n],\n";
			# close $FH;
			# die "complete";
		# }
		# elsif ($cgi->param('submit') eq 'step2') {
			# $image = $images[$cgi->param('image')];
			# my $e = $image;
			# while ($e->parent) {
				# unshift @tags , {_tag => $e->tag(), id => $e->attr('id'), class => $e->attr('class')};
				# $e = $e->parent();
			# }
			# 
			# $id = $name;
			# $id =~ s/[^a-zA-Z]//g;
			# my $id = 'Universal_' . $id;
			# my $clist = {$id => { name => $name,
								  # url_start => $url,
								  # criteria => [map {[%$_]} @tags]
				# }};
			# die "id already used" if keys %{Core::Universal->clist($id)};
			# Core::Universal->clist($clist);
			# my $remote = Core::Universal->new($id);
			# my $spot = $remote->first();
			# unless ($spot->mount() and ($spot->{src} eq $image->attr('src'))) {
				# die "does not match: " . $spot->{src} . "  " . $image->attr('src') ;
			# }
			# my $tree2 = DlUtil::get_tree($spot->{next});
			# my @images2 = $tree2->look_down(_tag => 'img');
			# $_->attr('src',URI->new_abs($_->attr('src'),$url)->as_string()) for @images2;
			# 
			# my @notsame = @images;
			# for my $i (@images2) {
				# @notsame = grep {$_->attr('src') ne $i->attr('src')} @notsame;
			# }
			# $l->info(join "\n", map {$_->attr('src') } @notsame);
			# 
			# $spot = $spot->next();
			# if ($spot->mount() and $spot->{src}) {
				# $html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_main(),-enctype=>&CGI::URL_ENCODED);
					# $html .= "is this the second image?";
					# $html .= $cgi->img(src => $spot->{src});
					# $html .= cgi->br();
					# $html .= cgi->submit(-name=>'submit', -value => 'step3');
					# 
				# $html .= cgi->end_form();
			# }
			# die "could not find second page";
		# }
		# else {
			my $name = $cgi->param('name');
			my $url = $cgi->param('url');
			my $tree = DlUtil::get_tree($url);
			
			my @images = $tree->look_down(_tag => 'img');
			
			my $norm = $name;
			$norm =~ s/[^a-zA-Z]//g;
			my $id = 'Universal_' . $norm;
			my $clist = {$id => { name => $name,
								  url_start => $url,
								  criteria => []
				}};
			die "id already used" if keys %{Core::Universal->clist($id)};
			Core::Universal->clist($clist);
			my $remote = Core::Universal->new($id);
			my $spot = $remote->first();
			unless ($spot->mount() and $spot->{next}) {
				die "could not mount" ;
			}
			my $tree2 = DlUtil::get_tree($spot->{next});
			my @images2 = $tree2->look_down(_tag => 'img');
			
			$spot = $spot->next();
			unless ($spot->mount() and $spot->{next}) {
				die "could not mount second" ;
			}
			
			my $tree3 = DlUtil::get_tree($spot->{next});
			my @images3 = $tree3->look_down(_tag => 'img');
			
			my %images;
			for my $i (@images) {
				push @{$images{$i->attr('src')}} , $i;
			}
			for my $i (@images2) {
				push @{$images{$i->attr('src')}} , $i;
			}
			for my $i (@images3) {
				push @{$images{$i->attr('src')}} , $i;
			}
			
			my @finalists;
			for (values %images) {
				if (@$_ == 1 ) {
					push @finalists , $_->[0];
				}
			}
			unless (@finalists == 3) {
				$l->info(join ' ', map {$_->attr('src')} @finalists);
				die 'could not single out image';
			}
			my @tags;
			my $i = 0;
			for my $e (@finalists) {
				while ($e->parent) {
					unshift @{$tags[$i]} , {_tag => $e->tag(), $e->all_external_attr()};
					$e = $e->parent();
				}
				$i++;
			}
			
			#$l->info(Dumper(@tags));
			
			my @t0 = @{$tags[0]};
			my @t1 = @{$tags[1]};
			my @t2 = @{$tags[2]};
			my @criteria;
			while (@t0) {
				my $a = pop @t0;
				my $b = pop @t1;
				my $c = pop @t2;
				if ($a->{_tag} ne $b->{_tag} or $a->{_tag} ne $c->{_tag}) {
					last;
				}
				unshift @criteria , [map {$_ , $a->{$_}} grep {$a->{$_} eq $b->{$_} and $a->{$_} eq $c->{$_}} keys %$a];
			}
			my @criteria2;
			while (@t0) {
				my $a = shift @t0;
				my $b = shift @t1;
				my $c = shift @t2;
				if ($a->{_tag} ne $b->{_tag} or $a->{_tag} ne $c->{_tag}) {
					last;
				}
				push @criteria2 , [map {$_ , $a->{$_}} grep {$a->{$_} eq $b->{$_} and $a->{$_} eq $c->{$_}} keys %$a];
			}
			push @criteria , @criteria2;
			
			#$l->info(Dumper(\@criteria));
			
			open(my $FH,'>>','uclist.txt') or die('could not open uclist.txt');
			print $FH "$norm => [\n\tq{$name},\n\tq{$url},\n\t" .
			(join ', ', map {'[' . join(',',map {"q{". $_ . "}"} @$_) . ']'} @criteria) . 
			"\n],\n";
			close $FH;
			$html .= 'done?';

			# $_->attr('src',URI->new_abs($_->attr('src'),$url)->as_string()) for @images;
			
			# $html .= join cgi->hr() , map { $_ . ' : ' . $images[$_]->as_HTML } 0..$#images;
			# $html .= cgi->hr();
			# 
			# $html .= cgi->start_form(-class=>'add', -method=>'POST',-action=>url_main(),-enctype=>&CGI::URL_ENCODED);
				# $html .= cgi->radio_group(-name=>'image',
									 # -values=>[0..$#images],
									 # -linebreak=>'true');
				# $html .= cgi->br();
				# $html .= cgi->submit(-name=>'submit', -value => 'step2');
				# 
			# $html .= cgi->end_form();
        # }
	}
	$html .= cgi->end_html();
	Server::send_response($c,$html);
	return 'add';
}
	

1;
