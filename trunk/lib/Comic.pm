package Comic;

use strict;
use Config::IniHash;
use Page;

use vars qw($VERSION);
$VERSION = '2';

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
	
	
	unless (defined $self->cfg->{url_home}) {
		$self->cfg->{url_home} = ($self->split_url($self->cfg->{url_start}))[0];
		$self->status("url_home nicht gesetzt benutze: ". $self->cfg->{url_home},'DEBUG');
	}
	
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
	} while ($b and !$::TERM);
	$self->usr->{last_update} = time unless $::TERM;
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
		if ($self->cfg->{all_next_additional_url} and !$::TERM) {
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
	
	$self->prev->{prev} = undef if $self->prev;
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
	{	#$self->dat->{_CFG_} wird gelöscht, vorsicht, dass man es danach nichtmehr braucht!
		my $tmp = $self->dat->{_CFG_};
		undef $self->dat->{_CFG_};
		delete $self->dat->{_CFG_};
		my $datfile = $self->{_DAT_FOL} . $self->name . $self->{_DAT_END};
		WriteINI($datfile,$self->dat); #data wird mit "__SECTIONS__" geladen u.u. umgehen
		$self->status("GESPEICHERT: " .$datfile,'OUT');
		$self->dat->{_CFG_} = $tmp;
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
	while( $b and !$::TERM ) {
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
