#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#23:21 01.12.2007

use strict;
use Config::IniHash;
use vars qw($VERSION);

$VERSION = '3.43';

my $TERM = 0;
$SIG{'INT'} = sub { 
		print "Terminating (expect errors)\n" ;
		$TERM = 1;
		};


print "remember: images must not be redistributed without the authors approval\n";


die 'no comic.ini' unless(-e 'comic.ini');
my $comics = ReadINI('comic.ini',{'case'=>'preserve', 'sectionorder' => 1});
my $user;
my $newuser = 0;
if (-e 'user.ini') {
	$user = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});
}
else {
	$newuser = 1;
	$user = {};
	$user->{_CFG_}->{update_interval} = 25000;
	print "keine user.ini gefunden, es wird versucht strips auf den aktuellen stand der dat zu bringen\n";
}
my $datcfg = {};
my $datcfg = ReadINI('data/_CFG_',{'case'=>'preserve', 'sectionorder' => 1}) if (-e 'data/_CFG_');

my @comics;
@comics = @{$comics->{'__SECTIONS__'}};

foreach my $comic (@comics) {
	next if (
			((time - $user->{_CFG_}->{update_interval}) < $user->{$comic}->{last_update}) or
			($user->{$comic}->{hiatus})
			);
	my $c = comic::new($comics->{$comic},$user->{$comic},\%{$datcfg->{$comic}});
	
	if ($newuser) {
		$c->chk_strips();
	}
	else {
		$c->get_all();
	}
	$c->save_dat;
	WriteINI("user.ini",$user);
	WriteINI('data/_CFG_',$datcfg);
	$c->release_pages();
	last if $TERM;
}

print "Enter zum Beenden";<>;

exit;

{	package comic;
	
	use strict;
	use Config::IniHash;


	sub new {
		my $self = {};
		$self->{cfg} = shift;
		$self->{usr} = shift;
		my $datcfg = shift;
		bless $self;
		
		my $l = length($self->name);
		$self->status("-" x (10) . $self->name . "-" x (25 - $l),3);
		
		$self->chk_dir();
		
		my $dat = ReadINI('./data/'.$self->name.'.dat',{'case'=>'preserve', 'sectionorder' => 1});
		$self->{dat} = $dat || {};
		$self->{dat}->{__CFG__} = $datcfg;
		
		unless (defined $self->{usr}->{url_current}) {
			$self->{usr}->{url_current} = ($self->split_url($self->{cfg}->{url_start}))[1];
			$self->{dat}->{__CFG__}->{first} = $self->curr->file(0);
		}
		return $self;
	}
	
	sub save_dat {
		my $self = shift;
		undef $self->{dat}->{__CFG__};
		delete $self->{dat}->{__CFG__};
		WriteINI('./data/'.$self->name.'.dat',$self->{dat});
		$self->status("GESPEICHERT: " .$self->name.".dat",0)
	}
	
	sub release_pages {
		my $self = shift;
		$self->curr->{prev} = undef;
		$self->curr->{next} = undef;
		$self->{curr} = undef;
	}
	
	sub name {
		my $self = shift;
		return $self->{cfg}->{name};
	}
	
	sub split_url {
		my $self = shift;
		my $url = shift;
		return 0 if (!defined $url);
		$url =~ m#(https?://[^/]*/?)(.*)#i;
		my $urlhome = $1;
		my $urlcur = $2;
		return ($urlhome,$urlcur);
	}
	
	sub get_all {
		my $self = shift;
		$self->status("BEGONNEN: get_all",0);
		my $b = 1;
		do {
			$b = $self->get_next();
		} while ($b and !$TERM);
		$self->{usr}->{last_update} = time unless $TERM;
		$self->status("BEENDET: get_all",0);
	}
	
	sub get_next {
		my $self = shift;
		$self->curr->all_strips;
		$self->curr->index_prev if $self->prev;
		if ($self->next) {
			$self->goto_next();
			return 1;
		}
		else {
			if ($self->{cfg}->{all_next_additional_url} and !$TERM) {
				$self->goto_next($self->{cfg}->{all_next_additional_url});
				$self->curr->all_strips;
				$self->curr->index_prev if $self->prev;
			}
			return 0;
		}
	}
	
	sub goto_next() {
		my $self = shift;
		$self->curr($self->next(@_));
		my @urls = $self->split_url($self->curr->url);
		my $url = $urls[0] . $urls[1];
		if ($urls[1]) {
			$self->{not_goto} = 1 if (
				(($self->{cfg}->{'not_goto'}) and ($urls[1] =~ m#$self->{cfg}->{'not_goto'}#i)) or 
				($urls[1] =~ m#(index|main)\.(php|html?)$#i) or 
				($urls[1] =~ m:#$:) or
				($self->{cfg}->{all_next_additional_url} and ($url =~ m#$self->{cfg}->{all_next_additional_url}#))
			);
			unless ($self->{not_goto} or $self->curr->dummy) {
				$self->{usr}->{url_current}  = $urls[1];
				$self->status("URL_CURRENT: ".$self->{usr}->{url_current} ,0);
			}
			
		}
		
		$self->prev->{prev} = undef;
	}
	
	sub curr {
		my $self = shift;
		$self->{curr} = shift if @_;
		return $self->{curr} if $self->{curr};
		
		$self->{curr} = page::new($self->{cfg}, $self->{dat}, $self, $self->{cfg}->{url_home} . $self->{usr}->{url_current});
		return $self->{curr};
	}
	
	sub prev {
		my $self = shift;
		return $self->curr->prev(@_);
	}
	
	sub next {
		my $self = shift;
		return $self->curr->next(@_);
	}

	sub chk_dir {
		my $self = shift;
		unless (-e "./strips/") {
			mkdir("./strips/");
			$self->status("ERSTELLT: ". "./strips/" ,1);
		}
		unless (-e "./strips/".$self->name) {
			mkdir("./strips/".$self->name);
			$self->status("ERSTELLT: "."./strips/".$self->name ,1);
		}
		unless (-e "./data/") {
			mkdir("./data/");
			$self->status("ERSTELLT: "."./data/" ,1);
		}
		$self->status("ordner ueberprueft" ,0);
	}
	
	sub status {
		my $self = shift;
		my $status = shift;
		my $level = shift;
		
		open(LOG,">>log.txt");
		print LOG $status . "\n";
		close LOG;
		return 1 unless $level > 1;
		print $status . "\n";
		if ($level == 5) {
			open(ERR,">>err.txt");
			print ERR $self->name() ." ERROR: " . $status . "\n";
			close ERR;
		}
		return 1;
	}

	sub chk_strips {
		my $self = shift;
		my $b = 1;
		my $file = $self->{dat}->{__CFG__}->{first};
		unless ($file) {
			$self->status("KEIN FIRST",3);
			return;
		}		
		$self->status("UEBERPRUEFE STRIPS",3);
		while( $b and !$TERM ) {
			unless ($file =~ m/^dummy\|/i) {
				unless (-e './strips/' . $self->name . '/' . $file) {
						$self->status("NICHT VORHANDEN: " . $self->name . '/' . $file,3);
						my $res = lwpsc::getstore($self->{dat}->{$file}->{surl},'./strips/' . $self->name . '/' . $file);
						if (($res >= 200) and  ($res < 300)) {
							$self->status("GESPEICHERT: " . $file,3);
						}
						else {
							$self->status("FEHLER BEIM SPEICHERN: " . $self->{dat}->{$file}->{surl} . '->' . $file,3);
						}
				}
				else {
					$self->status("VORHANDEN: " . $file , 3);
				}
			}
			if ($self->{dat}->{$file}->{next} and ($self->{dat}->{$file}->{next} ne $file)) {
				$file = $self->{dat}->{$file}->{next};
			}
			else {
				my @url = $self->split_url($self->{dat}->{$file}->{url});
				$self->{usr}->{url_current} = $url[1] if $url[1];
				$b = 0;
			}
		}
	}
	
	sub DESTROY {
		my $self = shift;
		$self->status('DESTROYED: '. $self->name,1);
	}
}


{	package page;
	
	use strict;
	
	sub new {
		my $self = {};
		$self->{cfg} = shift;
		$self->{dat} = shift;
		$self->{cmc} = shift;
		bless $self;
		$self->url(shift);
		$self->status("neue SEITE: ".$self->url,1);
		return $self;
	}
	
	sub body {
		my $self = shift;
		unless ($self->{body}) {
			$self->{'body'} = lwpsc::get($self->url);
			$self->status("BODY angefordert: " . $self->url,1);
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
						($self->url eq $self->{cfg}->{url_start}) or
						($self->{cfg}->{not_goto} and ($url =~ m#$self->{cfg}->{not_goto}#i)) or
						($self->{cfg}->{never_goto} and ($url =~ m#$self->{cfg}->{never_goto}#i))
					);
		if ($url) {
			$self->{prev} = page::new($self->{cfg}, $self->{dat},$self->{cmc},$url);
			$self->{prev}->{next} = $self;
		}
		else {
			$self->status("FEHLER kein prev: " . $self->url,5);
		}
		return $self->{prev};
	}
	
	sub next {
		my $self = shift;
		return $self->{next} if $self->{next};
		my @sides = $self->side_urls();
		my $url = shift || $sides[1];
		return if 	(
						($url eq $self->{cfg}->{url_start}) or
						(($self->{cfg}->{never_goto}) and ($url =~ m#$self->{cfg}->{never_goto}#i))
					);
		if ($url and ($url ne $self->url)) {
			$self->{next} = page::new($self->{cfg}, $self->{dat},$self->{cmc},$url);
			$self->{next}->{prev} = $self;
			
		}
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
		$self->status("SIDE_URLS ".$self->url.": " . $purl . ", " . $nurl,0);
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
		my @aref = ($body =~ m#(<a .*?>.*?</a>)#gis); 
		my @filter;
		foreach my $as (@aref) {
			$as =~ s#([^&])&amp;#\1&#;
			push(@filter,$as) if ($as =~ m#(prev(ious)?[^iews]|next|[^be\s*]back[^ground]|forward|prior|ensuing)#gi);
		}
		my $prev;
		my $next;
		foreach my $fil (@filter) {
			if (($fil =~ m#prev|back|prior#i) and (!$prev)) {
				if ($fil =~ m#href=["']?(.*?)["' >]#i) {
					$prev = $prev || $1;
					next if (($prev =~ m#\.jpe?g$|\.png$|\.gif$#) or
							(($prev =~ m#http://#) and !(($prev =~ m#$self->{cfg}->{url_home}#) or ($prev =~ m#$self->{cfg}->{add_url_home}#))));
				}
			}
			if (($fil =~ m#next|forward|ensuing#i) and (!$next)) {
				if ($fil =~ m#href=["']?(.*?)["' >]#i) {
					$next = $next || $1;
					next if (($next =~ m#\.jpe?g$|\.png$|\.gif$#) or
							(($next =~ m#http://#) and !(($next =~ m#$self->{cfg}->{url_home}#) or ($next =~ m#$self->{cfg}->{add_url_home}#))));
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
			$self->{strips}->[0] =~ s#/#+#g;
			$self->{dummy} = 1;
			$self->status("KEINE STRIPS: ".$self->url,4)
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
		$self->status("STRIP_URLS ".$self->url.": ". join(", ",@{$surl}),0);
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
		my @urls = ($body =~ m/<ima?ge?.*?src=["']?(.*?)["' >].*?>/gis);
		my @return;
		my @bad_return;
		foreach my $url (@urls) {
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
			
			if ($url =~ m#comics?/|/pages?/|/strips?/#) {
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
		return \@return;
	}
	
	sub concat_url {
		my $self = shift;
		my $url_parts = shift;
		my @return;
		unless (ref $url_parts) {
			my $tmp = $url_parts;
			$url_parts = [];
			$url_parts->[0] = $tmp;
		}
		foreach my $url_part (@{$url_parts}) {
			if ($url_part eq '#') {
				push(@return,'');
				next;
			}	
			if ($url_part =~ m#^https?://#) {
				push(@return,$url_part);
				next;
			}
			if ($url_part =~ m#^\.\./\.\./(.*)#) {
				my $url_add = $1;
				my $url_s =  $self->url();
				$url_s =~ s#^(.*/).*?/.*?/.*$#$1#e;
				push(@return,$url_s.$url_add);
				next;
			}
			if ($url_part =~ m#^\.\./(.*)#) {
				my $url_add = $1;
				my $url_s =  $self->url();
				$url_s =~ s#^(.*/).*?/.*$#$1#e;
				push(@return,$url_s.$url_add);
				next;
			}
			if ($url_part =~ m#^\?#) {
				my $url_s =  $self->url();
				$url_s =~ s#^(.*)\?.*?$#$1#e;
				push(@return,$url_s . $url_part);
				next;
			}
			if ($url_part =~ m#^[^/]#) {
				my $url_s =  $self->url();
				$url_s =~ s#^(.*/).*?$#$1#e;
				$url_part =~ s#^\./##;
				push(@return,$url_s . $url_part);
				next;
			}
			if ($url_part =~ m#^/#) { 
				$url_part =~ s#^/##;
				push(@return,$self->{cfg}->{'url_home'} . $url_part);
				next;
			}
		}
		return wantarray ? @return : $return[0];
	}
	
	sub all_strips {
		my $self = shift;
		foreach my $strip (@{$self->strips}) {
			$self->title($strip);
			$self->save($strip);
		}
		$self->index_all();
	}
	
	sub index_all {
		my $self = shift;
		my $n = $#{$self->strips};
		for (0..($n-1)) {
			$self->{dat}->{$self->file($_)}->{next} = $self->file($_+1);
			$self->{dat}->{$self->file($_+1)}->{prev} = $self->file($_);
			$self->status("VERBUNDEN: ".$self->file($_) . "<->".$self->file($_+1),1)
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
		$self->status("VERBUNDEN: ". $self->prev->file(-1). "<->".$self->file(0),1)
	}
	
	sub index_next {
		my $self = shift;
		if ($self->file(-1) eq $self->next->file(0)) {
			return;
		}
		$self->{dat}->{$self->file(-1)}->{next} = $self->next->file(0);
		$self->next->{dat}->{$self->next->file(0)}->{next} = $self->file(-1);
		$self->status("VERBUNDEN: ".$self->file(-1) . "<->".$self->next->file(0),1)
	}
	
	sub save {
		my $self = shift;
		my $strip = shift;
		return 0 if ($self->dummy);
		my $file_name = $self->get_file_name($strip);
		if (open(TMP,, "./strips/".$self->name."/$file_name")) {
			close(TMP);
			$self->status("VORHANDEN: ".$file_name,2);
			return 200;
		}
		else {
			$self->status("SPEICHERE: " . $strip . " -> " . $file_name,3);
			my $res = lwpsc::getstore($strip,"./strips/".$self->name."/$file_name");
			if (($res >= 200) and  ($res < 300)) {
				$self->status("GESPEICHERT: " . $file_name,3);
				return 200;
			}
			else {
				$self->status("FEHLER beim herunterladen: " . $res . " url: ". $self->url ." => " . $strip . " -> " . $file_name ,5);
				$self->status("ERNEUT speichern: " . $strip . " -> " . $file_name ,3);
				$res = lwpsc::getstore($strip,"./strips/".$self->name."/$file_name");
				if (($res >= 200) and  ($res < 300)) {
					$self->status("GESPEICHERT: " . $file_name,3);
					return 200;
				}
				else {
					$self->status("ERNEUTER FEHLER datei wird nicht gespeichert: " . $res . " bei " . $strip . " -> " . $file_name ,5);
					return 0;
				}
			}
			
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
		if ($body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
			$img = $1;
		}
		my $it;
		my $ia;
		if ($img) {
			if ($img =~ m#title=["']?([^"']*?)(?:[^\w]'[^\w]|"|>)#is) {
				$it = $1;
			}
			if ($img =~ m#alt=["']?([^"']*?)(?:[^\w]'[^\w]|"|>)#is) {
				$ia = $1;
			}
		}
		
		my @h1 = ($body =~ m#<h\d>([^<]*?)</h\d>#gis);
		my @dt = ($body =~ m#<div[^>]+id="comic[^"]*?title"[^>]*>([^<]+?)</div>#gis);
		my $sl;
		if ($body =~ m#<option[^>]+?selected[^>]*>([^<]+)</option>#is) {
			 $sl = $1;
		}
		
		my @all = (@ut,$st,$it,$ia,@h1,@dt,$sl);
		my @allout;
		foreach my $one (@all) {
			$one =~ s/\s+/ /gs;
			push(@allout,$one) if ($one);
		}
		push(@{$self->{dat}->{__SECTIONS__}},$file) unless defined $self->{dat}->{$file};
		$self->{dat}->{$file}->{'title'} = join(' || ',@allout);
		$self->{dat}->{$file}->{url} = $self->url;
		$self->{dat}->{$file}->{surl} = $surl;
		$self->{dat}->{$file}->{c_version} = $main::VERSION;
		
		$self->status("TITEL $file: " . $self->{dat}->{$file}->{'title'},1);
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
		$self->status('DESTROYED: '. $self->url,1);
	}
}

{	package lwpsc;
	use strict;
	use vars qw($ua @EXPORT @EXPORT_OK);

	require Exporter;

	@EXPORT = qw(get);
	@EXPORT_OK = qw($ua);
	
	sub _init_ua {
	    require LWP;
	    require LWP::UserAgent;
		require LWP::ConnCache;
	    #require HTTP::Status;
	    #require HTTP::Date;
	    $ua = new LWP::UserAgent;  # we create a global UserAgent object
	    $ua->agent("Comic");
	    $ua->env_proxy;
		$ua->conn_cache(LWP::ConnCache->new());
	}	
	
	sub get {
	    my $url = shift;
	    _init_ua() unless $ua;
		
		my $request = HTTP::Request->new(GET => $url);
		my $response = $ua->request($request);
		return $response->is_success ? $response->content : undef;
	}
	
	sub getstore {
	    my($url, $file) = @_;
	    _init_ua() unless $ua;
		
		(my $referer = $url) =~ s/[\?\&]//;
		$referer =~ s#/[^/]*$#/#;
		my $request = HTTP::Request->new(GET => $url);
		$request->referer($referer);
		
	    my $response = $ua->request($request, $file);
	    return $response->code;
	}

	1;
}

