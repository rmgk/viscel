#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:17 11.01.2008

use strict;
use warnings;

use vars qw($VERSION);

$VERSION = '3.58';


my $TERM = 0;
$SIG{'INT'} = sub { 
		print "Terminating (expect errors)\n" ;
		$TERM = 1;
		};

print "remember: images must not be redistributed without the authors approval\n";
print "comic3.pl version $VERSION\n";

{	#need to export each package to an own file ...just dont really know how O.o
	use Config::IniHash;
	my $comics = ReadINI('comic.ini',{'case'=>'preserve', 'sectionorder' => 1});
	my $user = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});

	unless (defined $user->{_CFG_}->{update_interval}) {
		$user->{_CFG_}->{update_interval} = 25000;
		print "no update interval specified using default = 25000 seconds\n";
	}
	
	my @comics;
	@comics = @{$comics->{__SECTIONS__}};
	foreach my $comic (@comics) {
		next if (
			((time - $user->{_CFG_}->{update_interval}) < ($user->{$comic}->{last_update}||0)) or
			($user->{$comic}->{hiatus}) or ($comics->{$comic}->{broken})
			);
		last if $TERM;
		Comic::get_comic({"name" => $comic});
		last if $TERM;
	}
}



{	package Comic;
	
	use strict;
	use Config::IniHash;

	sub get_comic {
		my $self = Comic::new(@_);
		
		$self->get_all;
		$self->save_cfg_usr_dat;
		$self->release_pages;
	}
	
	sub new {
		my $self = shift || {};
		bless $self;
		
		$self->initialize;

		return $self;
	}
	
	sub initialize {
		my $self = shift;
		
		$self->{_CONF} = 'comic.ini';
		$self->{_USER} = 'user.ini';
		$self->{_DAT_FOL} = './data/';
		$self->{_DAT_END} = '.dat';
		$self->{_DAT_CFG} = $self->{_DAT_FOL} . '_CFG_';
		
		$self->status("-" x (10) . $self->name . "-" x (25 - length($self->name)),'UINFO');
		$self->chk_dir;
		
		unless ((defined $self->usr->{url_current}) or ($self->usr->{url_current} ne '')) {
			$self->usr->{url_current} = ($self->split_url($self->cfg->{url_start}))[1];
			$self->status("url_current auf ". $self->usr->{url_current} . " gesetzt",'UINFO');
			$self->dat->{_CFG_}->{first} = $self->curr->file(0);
			$self->status("first auf " . $self->dat->{_CFG_}->{first}  . " gesetzt",'UINFO');
		}
		
	}
	
	sub chk_dir {
		my $self = shift;
		unless (-e "./strips/") {
			mkdir("./strips/");
			$self->status("ERSTELLT: ". "./strips/" ,'OUT');
		}
		unless (-e "./strips/".$self->name) {
			mkdir("./strips/".$self->name);
			$self->status("ERSTELLT: "."./strips/".$self->name ,'OUT');
		}
		unless (-e "./data/") {
			mkdir("./data/");
			$self->status("ERSTELLT: "."./data/" ,'OUT');
		}
		$self->status("ordner ueberprueft" ,'DEBUG');
	}
	
	sub cfg { #gibt die cfg des aktuellen comics aus # hier sollten nur nicht veränderliche informationen die zum download der comics benötigt werden drinstehen
		my $self = shift;
		
		unless ($self->{config}) {
			die "no config file ". $self->{_CONF} unless(-e $self->{_CONF});
			my $config = ReadINI($self->{_CONF},{'case'=>'preserve', 'sectionorder' => 1});
			die "no config for ". $self->name ." in ". $self->{_CONF} unless $config->{$self->name};
			$self->status("config geladen: ".$self->{_CONF} ,'IN');
			$self->{config} = $config->{$self->name};
		}
		return $self->{config};
	}
	
	sub usr { #gibt die aktuellen einstellungen des comics aus # hier gehören die veränderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
		my $self = shift;
		unless ($self->{user}) {
			my $usr;
			if (-e $self->{_USER}) {
				$usr = ReadINI($self->{_USER},{'case'=>'preserve', 'sectionorder' => 1});
				if ($usr->{$self->name}) {
					$self->status("user geladen: ". $self->{_USER} ,'IN');
					$self->{user} = $usr->{$self->name}
				}
				else {
					$self->{user} = {};
					$self->status("standardwert fuer usr benutzt","DEF");
				}
			}
			else {
				$self->{user} = {};
				#$self->{user}->{_CFG_}->{update_interval} = 25000;
				$self->status("standardwert fuer usr benutzt","DEF");
			}
		}
		return $self->{user}
	}
	
	sub dat { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu dem comic und seinen srips gespeichert
		my $self = shift;
		my $datfile = $self->{_DAT_FOL} . $self->name . $self->{_DAT_END};
		unless ($self->{data}) {
			my $dat;
			if (-e $datfile) {
				$self->{data} = ReadINI($datfile,{'case'=>'preserve', 'sectionorder' => 1}); #data wird mit "__SECTIONS__" geladen u.u. umgehen
				$self->status("dat geladen: $datfile","IN");
			}
			else {
				$self->{data} = {};
				$self->{data}->{__SECTIONS__} = [];
				$self->status("standardwert fuer dat benutzt","DEF");
			}
			if (-e $self->{_DAT_CFG}) {
				$dat = ReadINI($self->{_DAT_CFG},{'case'=>'preserve', 'sectionorder' => 1});
				if ($dat->{$self->name}) {
					$self->status("datcfg geladen: ".$self->{_DAT_CFG},"IN");
					$self->{data}->{_CFG_} = $dat->{$self->name};
				}
				else {
					$self->{data}->{_CFG_}  = {};
					$self->status("standardwert fuer dat_cfg benutzt","DEF");
				}
			}
			else {
				$self->{data}->{_CFG_}  = {};
				$self->status("standardwert fuer dat_cfg benutzt","DEF");
			}
		}
		return $self->{data};
	}
	
	sub name {
		my $self = shift;
		return $self->{name};
	}
	
	sub status {
		my $self = shift;
		my $status = shift;
		my $type = shift;
		
		open(LOG,">>log.txt");
		print LOG $status ." -->". $type . "\n";
		close LOG;
		
		if ($type =~ m/ERR|WARN|DEF|UINFO/i) {
			print $status . "\n";
		}
		
		if ($type  =~ m/ERR/i) {
			open(ERR,">>err.txt");
			print ERR $self->name() .">$type: " . $status . "\n";
			close ERR;
		}
		return 1;
	}
	
	sub get_all {
		my $self = shift;
		$self->status("BEGONNEN: get_all",'DEBUG');
		my $b = 1;
		do {
			$b = $self->get_next();
		} while ($b and !$TERM);
		$self->usr->{last_update} = time unless $TERM;
		$self->status("BEENDET: get_all",'DEBUG');
	}
	
	sub get_next {
		my $self = shift;
		my $strip_found = $self->curr->all_strips;
		if (!$strip_found and !$self->{vorheriges_nichtmehr_versuchen}) { #wenn kein strip gefunden wurde und wir es nciht schonmal probiert haben
			my @urls = $self->split_url($self->curr->url);
			if ($urls[1] eq $self->usr->{url_current}) { # und dies die erste seite ist bei der gesucht wurde (url_current wird erst später gesetzt)
				$self->status("kein strip gefunden - url current könnte beschädigt sein - versuche vorherige url zu finden",'WARN');
				$self->{prev} = undef;
				$self->{next} = undef;
				my $try_url = $self->dat->{$self->dat->{__SECTIONS__}->[- (rand(5) + 2)]}->{url}; #wir einen zufälligen vorherigen eintrag aus der dat
				if ($try_url) {
					$self->status("versuche: " . $try_url,'WARN');
					$self->curr(Page::new({"cmc" => $self},$try_url)); #und ändern current auf diesen wert
				}
				else {
					$self->status("keine vorherige url gefunden",'WARN');
				}
				$self->{vorheriges_nichtmehr_versuchen} = 1;
			}
		}
		$self->{vorheriges_nichtmehr_versuchen} = 1;
		$self->curr->index_prev if $self->prev;
		if ($self->next) {
			$self->goto_next();
			return 1;
		}
		else {
			if ($self->cfg->{all_next_additional_url} and !$TERM) {
				$self->goto_next($self->cfg->{all_next_additional_url});
				$self->curr->all_strips;
				$self->curr->index_prev if $self->prev;
			}
			return 0;
		}
	}

	sub curr {
		my $self = shift;
		$self->{curr} = shift if @_;
		return $self->{curr} if $self->{curr};
		
		$self->{curr} = Page::new({"cmc" => $self},$self->cfg->{url_home} . $self->usr->{url_current});
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
	
	sub goto_next {
		my $self = shift;
		$self->curr($self->next(@_));	#die nächste seite wird die aktuelle seite
		my @urls = $self->split_url($self->curr->url);
		my $url = $urls[0] . $urls[1];
		if ($urls[1]) {
			my $not_goto = $self->cfg->{not_goto};
			my $add_url = $self->cfg->{all_next_additional_url};
			$self->{not_goto} = 1 if ( 
				($not_goto and ($urls[1] =~ m#$not_goto#i)) or 
				($urls[1] =~ m#(index|main)\.(php|html?)$#i) or 
				($urls[1] =~ m:#$:) or
				($add_url and ($url =~ m#$add_url#))
			);
			unless ($self->{not_goto} or $self->curr->dummy) {
				$self->usr->{url_current}  = $urls[1];
				$self->status("URL_CURRENT: ".$self->usr->{url_current} ,'DEBUG');
			}
			
		}
		
		$self->prev->{prev} = undef;
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
	
	sub save_cfg_usr_dat {
		my $self = shift;
		#{	#save config # config should not be saved ... 
		#	my $config = ReadINI($self->{_CONF},{'case'=>'preserve', 'sectionorder' => 1});
		#	$config->{$self->name} = $self->cfg;
		#	WriteINI($self->{_CONF},$config);
		#	$self->status("GESPEICHERT: " .$self->{_CONF},'OUT');
		#}
		{	#save usr
			my $user = ReadINI($self->{_USER},{'case'=>'preserve', 'sectionorder' => 1});
			$user->{$self->name} = $self->usr;
			WriteINI($self->{_USER},$user);
			$self->status("GESPEICHERT: " .$self->{_USER},'OUT');
		}
		{	#save datcfg
			my $datcfg = ReadINI($self->{_DAT_CFG},{'case'=>'preserve', 'sectionorder' => 1});
			$datcfg->{$self->name} = $self->dat->{_CFG_};
			WriteINI($self->{_DAT_CFG},$datcfg);
			$self->status("GESPEICHERT: " .$self->{_DAT_CFG},'OUT');
		}
		{
			undef $self->dat->{_CFG_};
			delete $self->dat->{_CFG_};
			my $datfile = $self->{_DAT_FOL} . $self->name . $self->{_DAT_END};
			WriteINI($datfile,$self->dat); #data wird mit "__SECTIONS__" geladen u.u. umgehen
			$self->status("GESPEICHERT: " .$datfile,'OUT');
		}
	}
	
	sub release_pages {
		my $self = shift;
		$self->curr->{prev} = undef;
		$self->curr->{next} = undef;
		$self->{curr} = undef;
	}
	
	sub chk_strips { # not in use at the moment
		my $self = shift;
		my $b = 1;
		my $file = $self->{dat}->{__CFG__}->{first};
		unless ($file) {
			$self->status("KEIN FIRST",'WARN');
			return;
		}		
		$self->status("UEBERPRUEFE STRIPS",'UINFO');
		while( $b and !$TERM ) {
			unless ($file =~ m/^dummy\|/i) {
				unless (-e './strips/' . $self->name . '/' . $file) {
					$self->status("NICHT VORHANDEN: " . $self->name . '/' . $file,'UINFO');
					my $res = lwpsc::getstore($self->{dat}->{$file}->{surl},'./strips/' . $self->name . '/' . $file);
					if (($res >= 200) and  ($res < 300)) {
						$self->status("GESPEICHERT: " . $file,'UINFO');
					}
					else {
						$self->status("FEHLER BEIM SPEICHERN: " . $self->{dat}->{$file}->{surl} . '->' . $file,'ERR');
					}
				}
				else {
					$self->status("VORHANDEN: " . $file , 'UINFO');
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
		$self->status('DESTROYED: '. $self->name,'DEBUG');
	}
	
	1;
}

{	package Page;
	
	use strict;
	
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
			$self->{'body'} = lwpsc::get($self->url);
			$self->status("BODY angefordert: " . $self->url,'DEBUG');
			unless ($self->{body}) {
				sleep(1);
				$self->{'body'} = lwpsc::get($self->url);
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
			$as =~ s#([^&])&amp;#$1&#;
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
							(($tmp_url =~ m#http://#) and !(($tmp_url =~ m#$self->{cfg}->{url_home}#) or ($$tmp_url =~ m#$self->{cfg}->{add_url_home}#))));
					$prev = $tmp_url;
				}
			}
			if (($fil =~ m#next|forward|ensuing#i) and (!$next)) {
				if ($fil =~ m#href=["']?(.*?)["' >]#i) {
					my $tmp_url = $1;
					next if (($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#) or
							(($tmp_url =~ m#http://#) and !(($tmp_url =~ m#$self->{cfg}->{url_home}#) or ($tmp_url =~ m#$self->{cfg}->{add_url_home}#))));
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
			$self->{strips}->[0] =~ s#/#+#g;
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
		my @urls = ($body =~ m/<ima?ge?.*?src\s*=\s*["']?(.*?)["' >].*?>/gis);
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
			$url_part =~ s/[\s]//gs;
			next unless $url_part;
			if ($url_part eq '#') {
				push(@return,'');
				next;
			}	
			if ($url_part =~ m#^https?://#) {
				push(@return,$url_part);
				next;
			}
			if ($self->{cfg}->{use_home_only}) {
				$url_part =~ s#^[./]+##;
				push(@return,$self->{cfg}->{'url_home'} . $url_part);
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
			my $res = lwpsc::getstore($strip,"./strips/".$self->name."/$file_name");
			if (($res >= 200) and  ($res < 300)) {
				$self->status("GESPEICHERT: " . $file_name,'UINFO');
				return 200;
			}
			else {
				$self->status("FEHLER beim herunterladen: " . $res . " url: ". $self->url ." => " . $strip . " -> " . $file_name ,'WARN');
				$self->status("ERNEUT speichern: " . $strip . " -> " . $file_name ,'WARN');
				$res = lwpsc::getstore($strip,"./strips/".$self->name."/$file_name");
				if (($res >= 200) and  ($res < 300)) {
					$self->status("GESPEICHERT: " . $file_name,'UINFO');
					return 200;
				}
				else {
					$self->status("ERNEUTER FEHLER datei wird nicht gespeichert: " . $res . " url: ". $self->url ." => " . $strip . " -> " . $file_name ,'ERR');
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
		if ($urlpart) {
			if ($body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
				$img = $1;
			}
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
			next unless $one;
			$one =~ s/\s+/ /gs;
			push(@allout,$one) if ($one);
		}
		push(@{$self->{dat}->{__SECTIONS__}},$file) unless defined $self->{dat}->{$file};
		$self->{dat}->{$file}->{'title'} = join(' || ',@allout);
		$self->{dat}->{$file}->{url} = $self->url;
		$self->{dat}->{$file}->{surl} = $surl;
		$self->{dat}->{$file}->{c_version} = $main::VERSION;
		
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
		$ua->timeout(15);
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