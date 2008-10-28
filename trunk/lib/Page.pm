#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#18:47 03.10.2008

package Page;

use strict;
use dlutil;

use HTML::Tree;
use DBI;
use URI;
$URI::ABS_REMOTE_LEADING_DOTS = 1;

use vars qw($VERSION);
$VERSION = '17';

sub new {
	my $s = shift;
	bless $s;
	$s->status("NEW PAGE: ".$s->url,'DEBUG');
	return $s;
}

sub cmc {
	my $s = shift;
	return $s->{cmc};
}

sub name {
	my $s = shift;
	return $s->cmc->name;
}

sub cfg {
	my $s = shift;
	return $s->cmc->cfg(@_);
}

sub dat {
	my $s = shift;
	return $s->cmc->dat(@_);
}

sub usr {
	my $s = shift;
	return $s->cmc->usr(@_);
}


sub body {
	my $s = shift;
	unless ($s->{body}) {
		return 0 if $s->{no_body};
		$s->{'body'} = dlutil::get($s->url);
		$s->status("BODY requestet: " . $s->url,'DEBUG');
		unless ($s->{body}) {
			sleep(1);
			$s->{'body'} = dlutil::get($s->url);
			unless ($s->{body}) {
				$s->status("ERROR: no body found: " . $s->url,'ERR');
				$s->{no_body} = 1;
				return 0;
			}
		}
	}
	return \$s->{'body'};
}

sub url {
	my $s = shift;
	$s->{url} = "$_[0]" if @_;
	return $s->{url};
}

sub side_urls {
	my $s = shift;
	my $purl;
	my $nurl;
	my $body = $s->body();
	return unless $body;
	if ($s->cfg('regex_next') or $s->cfg('regex_prev')) {
		my $regex;
		$regex = $s->cfg('regex_prev');
		if ($$body =~ m#$regex#is) {
			$purl = $s->concat_url($1);
		}
		else {
			$purl = 0;
		}
		$regex = $s->cfg('regex_next');
		if ($$body =~ m#$regex#is) {
			$nurl = $s->concat_url($1);
		}
		else {
			$nurl = 0;
		}
	}
	elsif ($s->cfg('list_url_regex')) {
		($purl,$nurl) = $s->list_side_urls();
	}
	else {
		($purl,$nurl) = $s->try_get_side_urls();
	}
	$s->status("SIDE_URLS ".$s->url.": " . ($purl?$purl:'kein prev') . ", " . ($nurl?$nurl:'kein next'),'DEBUG');
	return ($purl,$nurl);
}

sub list_side_urls {
	my $s = shift;
	my $url = $s->url;
	my $body = $s->body;
	my $url_regex = $s->cfg('list_url_regex');
	my $insert_into = $s->cfg('list_url_insert');
	my $chap_regex = $s->cfg('list_chap_regex');
	my $page_regex = $s->cfg('list_page_regex');
	$url =~ m#$url_regex#i;
	my $chap = $+{chap};
	my $page = $+{page};
	my @chaps = ($$body =~ m#$chap_regex#gis);
	my @pages = ($$body =~ m#$page_regex#gis);
	my $chap_i = undef;
	for (my $i = 0;$i <= $#chaps; $i++) {
		if ($chaps[$i] eq $chap) {
			$chap_i = $i;
			last;
		}
	}	
	my $page_i;
	for (my $i = 0;$i <= $#pages; $i++) {
		if ($pages[$i] eq $page) {
			$page_i = $i;
			last;
		}
	}	
	if ($page_i > 0 and $page_i < $#pages) {
		my $prev = $insert_into;
		my $next = $insert_into;
		$prev =~ s/{{chap}}/$chap/egi;
		$next =~ s/{{chap}}/$chap/egi;
		my $pp = $pages[$page_i - 1];
		my $np = $pages[$page_i + 1];
		$prev =~ s/{{page}}/$pp/egi;
		$next =~ s/{{page}}/$np/egi;
		return ($prev,$next);
	}
	if ($page_i == 0) {
		my $next = $insert_into;
		$next =~ s/{{chap}}/$chap/egi;
		my $np = $pages[$page_i + 1];
		$next =~ s/{{page}}/$np/egi;
		return (undef,$next);
	}
	if ($page_i == $#pages) {
		my $next = $insert_into;
		my $prev = $insert_into;
		$prev =~ s/{{chap}}/$chap/egi;
		my $pp = $pages[$page_i - 1];
		$prev =~ s/{{page}}/$pp/egi;
		my $nc = $chaps[$chap_i + 1];
		if ($nc) {
			$next =~ s/{{chap}}/$nc/egi;
			$next =~ s/{{page}}/$pages[0]/egi;
		}
		else {
			$next = undef;
		}
		return ($prev,$next);
	}

}

sub try_get_side_urls {
	my $s = shift;
	my ($prev,$next) = $s->try_get_side_url_parts();
	my $prev_url = $s->concat_url($prev);
	my $next_url = $s->concat_url($next);
	return ($prev_url,$next_url);
}

sub try_get_side_url_parts {
	my $s = shift;
	my $body = $s->body();
	return unless $body;
	my @aref = ($$body =~ m#(<a\s+.*?>.*?</a>)#gis); 
	my @filter;
	foreach my $as (@aref) {
		push(@filter,$as) if ($as =~ m#(prev(ious)?([^i][^e][^w])|next|[^be\s*]back[^ground]|forward|prior|ensuing)#gi);
	}
	my $prev = undef;
	my $next = undef;
	my $url_home = $s->cfg('url_home');
	my $add_url_home = $s->cfg('add_url_home');
	my $never_goto = $s->cfg('never_goto');
	foreach my $fil (@filter) {
		next unless $fil;
		next if ($never_goto) and ($fil =~ m#$never_goto#i);
		if (($fil =~ m#prev|back|prior#i) and (!$prev)) {
			if ($fil =~ m#href=["']?(.*?)["' >]#i) {
				my $tmp_url = $1;
				next if (($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#) or
						(($tmp_url =~ m#http://#) and !(
							($tmp_url =~ m#$url_home#) or 
							($add_url_home and $tmp_url =~ m#$add_url_home#))));
				$prev = $tmp_url;
			}
		}
		if (($fil =~ m#next|forward|ensuing#i) and (!$next)) {
			if ($fil =~ m#href=["']?(.*?)["' >]#i) {
				my $tmp_url = $1;
				next if (($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#) or
						(($tmp_url =~ m#http://#) and !(
							($tmp_url =~ m#$url_home#) or 
							($add_url_home and $tmp_url =~ m#$add_url_home#))));
				$next = $tmp_url;
			}
		}
	}
	return ($prev,$next);
}

sub strips {
	my $s = shift;
	$s->{strips} = $s->strip_urls() unless $s->{strips};
	unless ($s->{strips}->[0]) {
		$s->{strips}->[0] =  "dummy|" . $s->url;
		$s->{strips}->[0] =~ s#[/?&=]#-#g;
		$s->{dummy} = 1;
		$s->status("KEINE STRIPS: ".$s->url,'WARN')
	}
	return $s->{strips};
}

sub dummy {
	my $s = shift;
	$s->strips;
	return $s->{dummy};
}

sub strip_urls {
	my $s = shift;
	my $surl;
	if ($s->cfg('regex_strip_url')) {
		my $body = $s->body();
		return unless $body;
		my $regex = $s->cfg('regex_strip_url');
		my @surl = ($$body =~ m#$regex#gsi);
		if ($s->cfg('regex_strip_url2')) {
			$regex = $s->cfg('regex_strip_url2');
			@surl = ($surl[0] =~ m#$regex#gsi);
		}
		@surl = $s->concat_url(\@surl);
		$surl = \@surl;
	}
	else {
		$surl = $s->try_get_strip_urls();
	}
	$s->status("STRIP_URLS ".$s->url.": ". join(", ",@{$surl}),'DEBUG');
	return $surl;
}

sub try_get_strip_urls {
	my $s = shift;
	my @urls = $s->concat_url($s->try_get_strip_urls_part());
	return \@urls;
}

sub try_get_strip_urls_part {
	my $s = shift;
	my $body = $s->body;
	return unless $body;
	
	my $imgs = [];
	my @tags = ($$body =~ m#(<\s*ima?ge?[^>]+>)#gis);

	my $i = 0;
	foreach my $tag (@tags) {
		if ($tag =~ m#src\s*=\s*(?<o>['"])(?<src>.+?)\k<o>|src\s*=\s*(?<src>[^\s]+)#is) {
			$imgs->[$i]->{src} = $+{src};
		}
		if ($tag =~ m#width\s*=\s*(?<o>['"])(?<width>\d+)\k<o>|width\s*=\s*(?<width>\d+)#is) {
			$imgs->[$i]->{width} = $+{width};
		}
		if ($tag =~ m#height\s*=\s*(?<o>['"])(?<height>\d+)\k<o>|height\s*=\s*(?<height>\d+)#is) {
			$imgs->[$i]->{height} = $+{height};
		} 
		$i++;
	}

	
	my @return;
	my @bad_return;
	foreach my $img (@{$imgs}) {
		my $url = $img->{src};
		next unless defined $url;
		if ((defined $img->{width} and $img->{width} < 21) or (defined $img->{height} and $img->{height} < 21)) {
			next;
		}
		if (defined $s->cfg('heur_strip_url')) {
			my $regex = $s->cfg('heur_strip_url');
			if ($url =~ m#$regex#i) {
				push(@return,$url);
			}
			next;
		}
		if ($url =~ m#([\?;=&]|nav|logo|header|template|resource|banner|thumb|file://|theme|icon|smiley|%3e)#i) {
			next;
		}
		if ($url =~ m#drunkduck.com/.*?/pages/#) {
			push(@return,$url);
			next;
		}
		if ($url =~ m#drunkduck.com/#) {
			next;
		}
		if ($url =~ m#^http://#) {
			my $url_home = $s->cfg('url_home');
			my $add_url_home = $s->cfg('add_url_home');
			next unless (($url =~ m#$url_home#) or 
						((defined $add_url_home) and $url =~ m#$add_url_home#));
		}
		if (($url =~ m#(^|\D)(\d{8}|\d{14})\D[^/]*$#) or ($url =~ m#(^|\D)\d{4}-\d\d-\d\d\D[^/]*$#)) {
			push(@return,$url);
			next;
		}		
		if ($url =~ m#comics?/|(?:^|/)pages?/|(?:^|/)strips?/#i) {
			push(@return,$url);
			next;
		}		
		my ($name) = ($s->url() =~ m#^.*/.*?(\w\w\w+)[^/]*$#) if ($s->url() !~ m/\.php\?/);
		if ($name) {
			if ($url =~ m#(.*/|[^/]*)$name#) {
				push(@bad_return,$url);
				next;
			}
		}
		my ($num) = ($s->url() =~ m#.*/.*?(\d+)[^/]*$#);
		if ($num) {
			if ($url =~ m#(?:\D|\D0+|^)$num\D[^/]*$#) {
				push(@bad_return,$url);
			}
		}		
	}
	@return = @bad_return unless $return[0];
	
	#delete doublicates
	my (%seen);
	@return = grep { !$seen{$_}++ } @return;
	
	return \@return;
}

sub concat_url {
	my $s = shift;
	my $url_part = shift;
	my @url;
	
	if (ref $url_part) {
		foreach my $part (@{$url_part}) {
			push(@url,$s->_concat_url($part));
		}
	}
	else {
		$url[0] = $s->_concat_url($url_part);
	}

	return wantarray ? @url : $url[0];
}

sub _concat_url {
	my $s = shift;
	my $url_part = shift;
	return unless $url_part;
	$url_part =~ s!([^&])&amp;|&#038;!$1&!gs;
	return if ($url_part eq '#');
	if ($s->cfg('use_home_only')) {
		$url_part =~ s'^[\./]+'';
		$url_part = "/" . $url_part;
	}
	return URI->new($url_part)->abs($s->url())->as_string;
}


sub all_strips {
	my $s = shift;
	return 0 unless $s->body;
	my $b = 0;
	foreach my $strip (@{$s->strips}) {
		$s->title($strip);
		$b += $s->save($strip); #rückgabe von 1 wenn erfolgreich
	}
	$s->index_all();
	return $b; #wir geben einen wahren wert zurück wenn mindestens ein strip erfolgreich geladen wurde.
}

sub index_all {
	my $s = shift;
	my $n = $#{$s->strips};
	for (0..($n-1)) {
		$s->dat($s->file($_),'next',$s->file($_+1));
		$s->dat($s->file($_+1),'prev',$s->file($_));
		$s->status("VERBUNDEN: ".$s->file($_) . "<->".$s->file($_+1),'DEBUG')
	}
}

sub file {
	my $s = shift;
	my $n = shift;
	return $s->get_file_name($s->strips->[$n]);
}

sub enque {
	my $s = shift;
	$s->cmc->{queue_dl}->enqueue(@_)
}

sub save {
	my $s = shift;
	my $strip = shift;
	return 0 if ($s->dummy);
	my $file_name = $s->get_file_name($strip);
	 if  (-e "./strips/".$s->name."/$file_name") {
		$s->status("EXISTS: ".$file_name,'UINFO');
		$s->cmc->md5($file_name);
		$s->usr('last_save',time) unless $s->usr('last_save');
		return 200;
	}
	else {
		$s->cmc->{semaphore}->down();
		my $home = $s->cfg("url_home");
		$s->url =~ m#(?:$home)?(.+)#;
		my $se_url = $1;
		$strip =~ m#(?:$home)?(.+)#;
		my $se_strip = $1;
		$s->status("ENQ: " . $se_url . " => " . $file_name,'UINFO', " URL: " . $s->url ." SURL: " .$strip);
		$s->enque([$strip,$file_name]);
	}
	# else {
		# $s->status("SPEICHERE: " . $strip . " -> " . $file_name,'UINFO');
		# my $res;
		# if ($s->{prefetch}->{$file_name}->{thread}) {
			# $res = $s->{prefetch}->{$file_name}->{thread}->join ;
		# }
		# else {
			# $res = dlutil::getstore($strip,"./strips/".$s->name."/$file_name");
		# }
		# if (($res >= 200) and  ($res < 300)) {
			# $s->status("GESPEICHERT: " . $file_name,'UINFO');
			# $s->md5($file_name);
			# $s->usr('last_save',time);
			# return 200;
		# }
		# else {
			# $s->status("FEHLER beim herunterladen: " . $res . " url: ". $s->url ." => " . $strip . " -> " . $file_name ,'WARN');
			# $s->status("ERNEUT speichern: " . $strip . " -> " . $file_name ,'WARN');
			# $res = dlutil::getstore($strip,"./strips/".$s->name."/$file_name");
			# if (($res >= 200) and  ($res < 300)) {
				# $s->status("GESPEICHERT: " . $file_name,'UINFO');
				# $s->md5($file_name);
				# $s->usr('last_save',time);
				# return 200;
			# }
			# else {
				# $s->status("ERNEUTER FEHLER datei wird nicht gespeichert: " . $res . " url: ". $s->url ." => " . $strip . " -> " . $file_name ,'ERR');
				# return 0;
			# }
		# }
	# }
}


sub title {
	my $s = shift;
	my $surl = shift;
	my $file = $s->get_file_name($surl);
	my $url = $s->url();
	my $body = $s->body();
	return unless $body;
	my ($urlpart) = ($surl =~ m#.*/(.*)#);
	
	my $regex_title = $s->cfg('regex_title');
	my @ut = ($$body =~ m#$regex_title#gis) if ($regex_title);
	$$body =~ m#<title>([^<]*?)</title>#is;
	my $st = $1;
	
	my $img;
	if ($urlpart) {
		if ($$body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
			$img = $1;
		}
	}
	my $it;
	my $ia;
	if ($img) {
		if ($img =~ m#title=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
			$it = $1;
		}
		if ($img =~ m#alt=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
			$ia = $1;
		}
	}
	
	my @h1 = ($$body =~ m#<h\d>([^<]*?)</h\d>#gis);
	my @dt = ($$body =~ m#<div[^>]+id="comic[^"]*?title"[^>]*>([^<]+?)</div>#gis);
	my $sl;
	if ($$body =~ m#<option[^>]+?selected[^>]*>([^<]+)</option>#is) {
		 $sl = $1;
	}
	
	my $ut = join(" ~§~ ",@ut) if @ut;
	my $h1 = join(" ~§~ ",@h1) if @h1;
	my $dt = join(" ~§~ ",@dt) if @dt;
	my @all = ($ut,$st,$it,$ia,$h1,$dt,$sl);
	my @allout;
	foreach my $one (@all) {
		if (defined $one) {
			$one =~ s/\s+/ /gs ;
			$one =~ s/"/''/gs ;
		}
		else {
			$one = "-§-";
		}
		push(@allout,$one);
	}
	$s->dat($file,'title',join(' !§! ',@allout));
	$s->dat($file,'url',$s->url);
	$s->dat($file,'surl',$surl);
	$s->dat($file,'c_version',$main::VERSION);
	$s->dat($file,'time',time);
	
	$s->status("TITEL $file: " . $s->dat($file,'title'),'DEBUG');
	return $s->dat($file,'title'); 
}

sub get_file_name {
	my $s = shift;
	my $surl = shift;
	my $url = $s->url();
	return $surl if ($s->dummy);
	my $filename = $surl;
	$filename =~ s#^.*/##;
	
	if ($surl =~ /drunkduck/) {
		$url =~ m/p=(\d+)/;
		my $name = $1;
		my $ending;
		if ($surl =~ m#(\.(jpe?g|gif|png))#) {
			$ending = $1;
		}
		$ending = ".jpg" unless ($ending);
		$filename = $name . $ending;
	}
	if ($s->cfg('rename')) {
		if ($s->cfg('rename') =~ m/^strip_url#(.*)#(\d*)/) {
			my $regex = $1;
			my @wnum = split('',$2);
			my @num = ($surl =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			$name = join('',@num) unless @wnum;
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
		if ($s->cfg('rename') =~ m/^url#(.*)#(\d*)/) {
			my $regex = $1;
			my @wnum = split('',$2);
			
			my @num = ($url =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			$name = join('',@num) unless @wnum;
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
		if ($s->cfg('rename') =~ m/^url_only#(.*)#(.*)#(\d*)/) {
			my $only = $1;
			my $regex = $2;
			my @wnum = split('',$3);
			if ($filename =~ /$only/i) {
				my @num = ($url =~ m#$regex#g);
				
				my $name;
				foreach my $wnum (@wnum) {
					$name .= $num[$wnum];
				}
				$name = join('',@num) unless @wnum;
				$filename = $name . $filename;
			}
		}
		if ($s->cfg('rename') =~ m/^strip_url_only#(.*)#(.*)#(\d*)/) {
			my $only = $1;
			my $regex = $2;
			my @wnum = split('',$3);
			if ($filename =~ /$only/i) {
				$surl =~ m#^(.+)/([^/]+)$#i;
				my $burl = $1;
				my $strp = $2;
				my @num = ($surl =~ m#$regex#g);
				my $name;
				foreach my $wnum (@wnum) {
					$name .= $num[$wnum];
				}
				$name = join('',@num) unless @wnum;
				$filename = $name . $filename;
			}
		}
	}
	return $filename;
}

sub status {
	my $s = shift;
	$s->cmc->status(@_);
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->url,'DEBUG');
}

1;
