#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#14:23 13.07.2008

package Page;

use strict;
use dlutil;

use URI;
$URI::ABS_REMOTE_LEADING_DOTS = 1;

use vars qw($VERSION);
$VERSION = '4';

sub new {
	my $self = shift;
	bless $self;
	$self->url(shift);
	$self->{cfg} = $self->{cfg} || $self->{cmc}->cfg;
	$self->{dat} = $self->{dat} || $self->{cmc}->dat;
	$self->{cfg}->{name} = $self->{cmc}->name;
	$self->status("neue SEITE: ".$self->url,'DEBUG');
	return $self;
}

sub body {
	my $self = shift;
	unless ($self->{body}) {
		$self->{'body'} = dlutil::get($self->url);
		$self->status("BODY angefordert: " . $self->url,'DEBUG');
		unless ($self->{body}) {
			sleep(1);
			$self->{'body'} = dlutil::get($self->url);
			unless ($self->{body}) {
				$self->status("FEHLER: body nicht vorhanden : " . $self->url,'ERR');
				return 0;
			}
		}
	}
	return $self->{'body'};
}

sub url {
	my $self = shift;
	$self->{url} = shift if @_;
	return $self->{url};
}

sub name {
	my $self = shift;
	return $self->{cfg}->{name};
}

sub prev {
	my $self = shift;
	return $self->{prev} if $self->{prev};
	my @sides = $self->side_urls();
	my $url = shift || $sides[0];
	return if 	(
					($self->url eq $self->{cfg}->{url_start}) or #nicht zur start url
					($self->{cfg}->{not_goto} and ($url =~ m#$self->{cfg}->{not_goto}#i)) or
					($self->{cfg}->{never_goto} and ($url =~ m#$self->{cfg}->{never_goto}#i))
				);
	if ($url) {
		$self->{prev} = Page::new({"cmc" => $self->{cmc}},$url);
		$self->{prev}->{next} = $self;
	}
	else {
		$self->status("FEHLER kein prev: " . $self->url,'ERR');
	}
	return $self->{prev};
}

sub next {
	my $self = shift;
	return $self->{next} if $self->{next};
	my @sides = $self->side_urls();
	my $url = shift || $sides[1];
	return if 	(	!($url and ($url ne $self->url)) or	#nicht die eigene url
					($url eq $self->{cfg}->{url_start}) or	#nicht die start url
					(($self->{cfg}->{never_goto}) and ($url =~ m#$self->{cfg}->{never_goto}#i))
				);
				
	$self->{next} = Page::new({"cmc" => $self->{cmc}},$url);
	$self->{next}->{prev} = $self;

	return $self->{next};
}

sub side_urls {
	my $self = shift;
	my $purl;
	my $nurl;
	my $body = $self->body();
	if ($self->{cfg}->{'regex_next'} or $self->{cfg}->{'regex_prev'}) {
		my $regex;
		$regex = $self->{cfg}->{'regex_prev'};
		if ($body =~ m#$regex#is) {
			$purl = $self->concat_url($1);
		}
		else {
			$purl = 0;
		}
		$regex = $self->{cfg}->{'regex_next'};
		if ($body =~ m#$regex#is) {
			$nurl = $self->concat_url($1);
		}
		else {
			$nurl = 0;
		}
	}
	
	else {
		($purl,$nurl) = $self->try_get_side_urls();
	}
	$self->status("SIDE_URLS ".$self->url.": " . ($purl?$purl:'kein prev') . ", " . ($nurl?$nurl:'kein next'),'DEBUG');
	return ($purl,$nurl);
}

sub try_get_side_urls {
	my $self = shift;
	my ($prev,$next) = $self->try_get_side_url_parts();
	my $prev_url = $self->concat_url($prev);
	my $next_url = $self->concat_url($next);
	return ($prev_url,$next_url);
}

sub try_get_side_url_parts {
	my $self = shift;
	my $body = $self->body();
	my @aref = ($body =~ m#(<a\s+.*?>.*?</a>)#gis); 
	my @filter;
	foreach my $as (@aref) {
		push(@filter,$as) if ($as =~ m#(prev(ious)?[^iews]|next|[^be\s*]back[^ground]|forward|prior|ensuing)#gi);
	}
	my $prev;
	my $next;
	foreach my $fil (@filter) {
		next unless $fil;
		next if ($self->{cfg}->{never_goto}) and ($fil =~ m#$self->{cfg}->{never_goto}#i);
		if (($fil =~ m#prev|back|prior#i) and (!$prev)) {
			if ($fil =~ m#href=["']?(.*?)["' >]#i) {
				my $tmp_url = $1;
				next if (($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#) or
						(($tmp_url =~ m#http://#) and !(
							($tmp_url =~ m#$self->{cfg}->{url_home}#) or 
							($self->{cfg}->{add_url_home} and $tmp_url =~ m#$self->{cfg}->{add_url_home}#))));
				$prev = $tmp_url;
			}
		}
		if (($fil =~ m#next|forward|ensuing#i) and (!$next)) {
			if ($fil =~ m#href=["']?(.*?)["' >]#i) {
				my $tmp_url = $1;
				next if (($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#) or
						(($tmp_url =~ m#http://#) and !(
							($tmp_url =~ m#$self->{cfg}->{url_home}#) or 
							($self->{cfg}->{add_url_home} and $tmp_url =~ m#$self->{cfg}->{add_url_home}#))));
				$next = $tmp_url;
			}
		}
	}
	return ($prev,$next);
}

sub strips {
	my $self = shift;
	$self->{strips} = $self->strip_urls() unless $self->{strips};
	unless ($self->{strips}->[0]) {
		$self->{strips}->[0] =  "dummy|" . $self->url;
		$self->{strips}->[0] =~ s#[/?&=]#-#g;
		$self->{dummy} = 1;
		$self->status("KEINE STRIPS: ".$self->url,'WARN')
	}
	return $self->{strips};
}

sub dummy {
	my $self = shift;
	$self->strips;
	return $self->{dummy};
}

sub strip_urls {
	my $self = shift;
	my $surl;
	if ($self->{cfg}->{'regex_strip_url'}) {
		my $body = $self->body();
		my @surl = ($body =~ m#$self->{cfg}->{'regex_strip_url'}#gsi);
		if ($self->{cfg}->{'regex_strip_url2'}) {
			@surl = ($surl[0] =~ m#$self->{cfg}->{'regex_strip_url2'}#gsi);
		}
		@surl = $self->concat_url(\@surl);
		$surl = \@surl;
	}
	else {
		$surl = $self->try_get_strip_urls();
	}
	$self->status("STRIP_URLS ".$self->url.": ". join(", ",@{$surl}),'DEBUG');
	return $surl;
}

sub try_get_strip_urls {
	my $self = shift;
	my @urls = $self->concat_url($self->try_get_strip_urls_part());
	return \@urls;
}

sub try_get_strip_urls_part {
	my $self = shift;
	my $body = $self->body();
	my @tags = ($body =~ m/(<ima?ge?.*?src\s*=\s*["']?.*?["' >].*?>)/gis);
	#my @urls = ($body =~ m/<ima?ge?.*?src\s*=\s*["']?(.*?)["' >].*?>/gis);
	my (@urls, @width, @height);
	
	foreach my $tag (@tags) {
		$tag =~ m/src\s*=\s*["']?(.*?)["' >]/is;
		push (@urls, $1);
		if ($tag =~ m/width\s*=\s*["']?(\d+)["' >]/is) {
			push (@width, $1);
		}
		else {
			push (@width, undef);
		}
		if ($tag =~ m/height\s*=\s*["']?(\d+)["' >]/is) {
			push (@height, $1);
		}
		else {
			push (@height, undef);
		}
	}
	
	my @return;
	my @bad_return;
	my $i = -1;
	foreach my $url (@urls) {
		$i++;
		if ((defined $width[$i] and $width[$i] < 11) or (defined $height[$i] and $height[$i] < 11)) {
			next;
		}
		if (defined $self->{cfg}->{'heur_strip_url'}) {
			my $regex = $self->{cfg}->{'heur_strip_url'};
			if ($url =~ m#$regex#i) {
				push(@return,$url);
			}
			next;
		}
		if ($url =~ m#([\?;=&]|nav|logo|header|template|resource|banner|thumb|file://|theme)#i) {
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
			next unless (($url =~ m#$self->{cfg}->{'url_home'}#) or 
						((defined $self->{cfg}->{'add_url_home'}) and $url =~ m#$self->{cfg}->{'add_url_home'}#));
		}
		if (($url =~ m#(^|\D)(\d{8}|\d{14})\D[^/]*$#) or ($url =~ m#(^|\D)\d{4}-\d\d-\d\d\D[^/]*$#)) {
			push(@return,$url);
			next;
		}		
		if ($url =~ m#comics?/|(?:^|/)pages?/|(?:^|/)strips?/#i) {
			push(@return,$url);
			next;
		}		
		my ($name) = ($self->url() =~ m#^.*/.*?(\w\w\w+)[^/]*$#) if ($self->url() !~ m/\.php\?/);
		if ($name) {
			if ($url =~ m#(.*/|[^/]*)$name#) {
				push(@bad_return,$url);
				next;
			}
		}
		my ($num) = ($self->url() =~ m#.*/.*?(\d+)[^/]*$#);
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
	my $self = shift;
	my $url_part = shift;
	my @url;
	
	if (ref $url_part) {
		foreach my $part (@{$url_part}) {
			push(@url,$self->_concat_url($part));
		}
	}
	else {
		$url[0] = $self->_concat_url($url_part);
	}

	return wantarray ? @url : $url[0];
}

sub _concat_url {
	my $self = shift;
	my $url_part = shift;
	return unless $url_part;
	$url_part =~ s#([^&])&amp;#$1&#gs;
	return if ($url_part eq '#');
	$url_part =~ s#^[./]+#/# if ($self->{cfg}->{use_home_only});
	return URI->new($url_part)->abs($self->url())->as_string;
}


sub all_strips {
	my $self = shift;
	return 0 unless $self->body;
	my $b = 0;
	foreach my $strip (@{$self->strips}) {
		$self->title($strip);
		$b += $self->save($strip); #rückgabe von 1 wenn erfolgreich
	}
	$self->index_all();
	return $b; #wir geben einen wahren wert zurück wenn mindestens ein strip erfolgreich geladen wurde.
}

sub index_all {
	my $self = shift;
	my $n = $#{$self->strips};
	for (0..($n-1)) {
		$self->{dat}->{$self->file($_)}->{next} = $self->file($_+1);
		$self->{dat}->{$self->file($_+1)}->{prev} = $self->file($_);
		$self->status("VERBUNDEN: ".$self->file($_) . "<->".$self->file($_+1),'DEBUG')
	}
}

sub file {
	my $self = shift;
	my $n = shift;
	return $self->get_file_name($self->strips->[$n]);
}

sub index_prev {
	my $self = shift;
	if ($self->file(0) eq $self->prev->file(-1)) {
		return;
	}
	$self->{dat}->{$self->file(0)}->{prev} = $self->prev->file(-1);
	$self->prev->{dat}->{$self->prev->file(-1)}->{next} = $self->file(0);
	$self->status("VERBUNDEN: ". $self->prev->file(-1). "<->".$self->file(0),'DEBUG')
}

sub index_next {
	my $self = shift;
	if ($self->file(-1) eq $self->next->file(0)) {
		return;
	}
	$self->{dat}->{$self->file(-1)}->{next} = $self->next->file(0);
	$self->next->{dat}->{$self->next->file(0)}->{next} = $self->file(-1);
	$self->status("VERBUNDEN: ".$self->file(-1) . "<->".$self->next->file(0),'DEBUG')
}

sub save {
	my $self = shift;
	my $strip = shift;
	return 0 if ($self->dummy);
	my $file_name = $self->get_file_name($strip);
	if (open(TMP,, "./strips/".$self->name."/$file_name")) {
		close(TMP);
		$self->status("VORHANDEN: ".$file_name,'UINFO');
		return 200;
	}
	else {
		$self->status("SPEICHERE: " . $strip . " -> " . $file_name,'UINFO');
		my $res = dlutil::getstore($strip,"./strips/".$self->name."/$file_name");
		if (($res >= 200) and  ($res < 300)) {
			$self->status("GESPEICHERT: " . $file_name,'UINFO');
			$self->md5($file_name);
			return 200;
		}
		else {
			$self->status("FEHLER beim herunterladen: " . $res . " url: ". $self->url ." => " . $strip . " -> " . $file_name ,'WARN');
			$self->status("ERNEUT speichern: " . $strip . " -> " . $file_name ,'WARN');
			$res = dlutil::getstore($strip,"./strips/".$self->name."/$file_name");
			if (($res >= 200) and  ($res < 300)) {
				$self->status("GESPEICHERT: " . $file_name,'UINFO');
				$self->md5($file_name);
				return 200;
			}
			else {
				$self->status("ERNEUTER FEHLER datei wird nicht gespeichert: " . $res . " url: ". $self->url ." => " . $strip . " -> " . $file_name ,'ERR');
				return 0;
			}
		}
	}
}

sub md5 {
	my $self = shift;
	my $file_name = shift;
	if(  my $got_md5 = eval { require Digest::MD5; }) {
		if (open(FILE, "./strips/".$self->name."/$file_name")) {
			binmode(FILE);
			$self->{dat}->{$file_name}->{'md5'} = Digest::MD5->new->addfile(*FILE)->hexdigest;
			close FILE;
		}
		else {
			$self->status("DATEIFEHLER " . "./strips/".$self->name."/$file_name" . "konnte nicht geöfnet werden" ,'ERR')
		}
	}
	else {
		$self->status("md5 modul (Digest::MD5) nicht gefunden",'DEBUG') unless ($self->{_md5debug});
		$self->{_md5debug} = 1;
	}
}

sub title {
	my $self = shift;
	my $surl = shift;
	my $file = $self->get_file_name($surl);
	my $url = $self->url();
	my $body = $self->body();
	my ($urlpart) = ($surl =~ m#.*/(.*)#);
	
	my @ut = ($body =~ m#$self->{cfg}->{'regex_title'}#gis) if ($self->{cfg}->{'regex_title'});
	$body =~ m#<title>([^<]*?)</title>#is;
	my $st = $1;
	
	my $img;
	if ($urlpart) {
		if ($body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
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
	
	my @h1 = ($body =~ m#<h\d>([^<]*?)</h\d>#gis);
	my @dt = ($body =~ m#<div[^>]+id="comic[^"]*?title"[^>]*>([^<]+?)</div>#gis);
	my $sl;
	if ($body =~ m#<option[^>]+?selected[^>]*>([^<]+)</option>#is) {
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
		}
		else {
			$one = "-§-";
		}
		push(@allout,$one);
	}
	push(@{$self->{dat}->{__SECTIONS__}},$file) unless defined $self->{dat}->{$file};
	$self->{dat}->{$file}->{'title'} = join(' !§! ',@allout);
	$self->{dat}->{$file}->{url} = $self->url;
	$self->{dat}->{$file}->{surl} = $surl;
	$self->{dat}->{$file}->{c_version} = $main::VERSION;
	$self->{dat}->{$file}->{time} = time;
	
	$self->status("TITEL $file: " . $self->{dat}->{$file}->{'title'},'DEBUG');
	return $self->{'dat'}->{$file}->{'title'}; 
}

sub get_file_name {
	my $self = shift;
	my $surl = shift;
	my $url = $self->url();
	return $surl if ($self->dummy);
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
	if ($self->{cfg}->{'rename'}) {
		if ($self->{cfg}->{'rename'} =~ m/^strip_url#(.*?)#(\d+)/) {
			my $regex = $1;
			my @wnum = split('',$2);
			my @num = ($surl =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
		if ($self->{cfg}->{'rename'} =~ m/^url#(.*?)#(\d+)/) {
			my $regex = $1;
			my @wnum = split('',$2);
			
			my @num = ($url =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
	}
	return $filename;
}

sub status {
	my $self = shift;
	$self->{cmc}->status(@_);
}

sub DESTROY {
	my $self = shift;
	$self->status('DESTROYED: '. $self->url,'DEBUG');
}

1;
