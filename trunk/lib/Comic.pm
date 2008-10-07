#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:31 06.10.2008
package Comic;

use strict;
use Config::IniHash;
use Page;
use Data::Dumper;

use URI;
use DBI;

use vars qw($VERSION);
$VERSION = '8';

sub get_comic {
	my $s = Comic::new(@_);
	if ($s) {
		$s->get_all;
		$s->release_pages;
		$s->dbh->commit unless $s->{dbh_no_disconnect};
		$s->dbh->disconnect unless $s->{dbh_no_disconnect};
	}
	else {
		return 0;
	}
}

sub new {
	my $s = shift || {};
	bless $s;
	$s->{DB} //= 'comics.db';
	$s->{path_strips} //= "./strips/";
	$s->{path_log} //= "log.txt";
	$s->{path_err} //= "err.txt";
	$s->{_CONF} //= 'comic.ini';
	
	if ($s->{dbh}) {
		$s->{dbh_no_disconnect} = 1;
	}
	else {
		$s->{dbh} = DBI->connect("dbi:SQLite:dbname=".$s->{DB},"","",{AutoCommit => 0,PrintError => 1});
	}
	
	
	$s->status("-" x (10) . $s->name . "-" x (25 - length($s->name)),'UINFO');

	unless (-e "./strips/".$s->name) {
		mkdir("./strips/".$s->name);
		$s->status("WRITE: ".$s->{path_strips}.$s->name ,'OUT');
	}
	
	unless($s->dbh->selectrow_array(qq(select name from sqlite_master where type='table' and name='_) . $s->name ."'")) {
		$s->dbh->do("create table _" .  $s->name . " (
			strip text,
			c_version text,
			md5 text,
			prev text,
			next text,
			surl text,
			time integer,
			title text,
			url text
		)");
	};
	
	return $s;
}

sub dbh {
	my $s = shift;
	return $s->{dbh};
}

sub cfg { #gibt die cfg des aktuellen comics aus # hier sollten nur nicht veränderliche informationen die zum download der comics benötigt werden drinstehen
	my $s = shift;
	my ($key,$value) = @_;
	
	unless ($s->{config}) {
		die "no config file ". $s->{_CONF} unless(-e $s->{_CONF});
		my $config = ReadINI($s->{_CONF},{'case'=>'preserve', 'sectionorder' => 1});
		die "no config for ". $s->name ." in ". $s->{_CONF} unless $config->{$s->name};
		$s->status("LOAD: config: ".$s->{_CONF} ,'IN');
		$s->{config} = $config->{$s->name};
	}
	$s->{config}->{$key} = $value if $value;
	return $s->{config}->{$key};
}

sub usr { #gibt die aktuellen einstellungen des comics aus # hier gehören die veränderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
	my $s = shift;
	my ($key,$value,$null) = @_;
	my $c = $s->name;
	if ($null) {
			$s->dbh->do(qq(update USER set $key = NULL where comic="$c"));
			$s->status("DELETE: $key",'WARN');
	}
	elsif (defined $value) {
		if ($s->dbh->do(qq(update USER set $key = "$value" where comic="$c")) < 1) { #try to update
			$s->dbh->do(qq(insert into USER (comic,$key) VALUES ("$c","$value"))); #insert if update fails
		}
	}
	return $s->dbh->selectrow_array(qq(select $key from USER where comic="$c"));
}

sub dat { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu dem comic und seinen srips gespeichert
	my $s = shift;
	my ($strip,$key,$value,$null) = @_;
	my $c = $s->name;
	if ($null) {
		$s->dbh->do(qq(update _$c set $key = NULL where strip="$strip"));
		$s->status("DELETE: $key from $strip",'WARN');
	}
	elsif (defined $value) {
		if ($s->dbh->do(qq(update _$c set $key = "$value" where strip="$strip")) < 1) { #try to update
			$s->dbh->do(qq(insert into _$c  (strip,$key) values ("$strip","$value"))); #insert if update fails
		}
	}
	return $s->dbh->selectrow_array(qq(select $key from _$c where strip="$strip"));
}

sub name {
	my $s = shift;
	return $s->{name};
}

sub status {
	my $s = shift;
	my $status = shift;
	my $type = shift;
	
	open(LOG,">>".$s->{path_log});
	print LOG $status ." -->". $type . "\n";
	close LOG;
	
	if ($type =~ m/ERR|WARN|DEF|UINFO/i) {
		print $status . "\n";
	}
	
	if ($type  =~ m/ERR/i) {
		open(ERR,">>".$s->{path_err});
		print ERR $s->name() .">$type: " . $status . "\n";
		close ERR;
	}
	return 1;
}

sub get_all {
	my $s = shift;
	$s->status("START: get_all",'DEBUG');
	my $b = 1;
	do {
		$b = $s->get_next();
	} while ($b and !$::TERM);
	$s->usr('last_update',time) unless $::TERM;
	$s->status("DONE: get_all",'DEBUG');
}

sub get_next {
	my $s = shift;
	my $strip_found = $s->curr->all_strips;
	# if (!$strip_found and !$s->{vorheriges_nichtmehr_versuchen}) { #wenn kein strip gefunden wurde und wir es nicht schonmal probiert haben
		# my @urls = $s->split_url($s->curr->url);
		# if ($urls[1] eq $s->usr("url_current")) { # und dies die erste seite ist bei der gesucht wurde (url_current wird erst später gesetzt)
			# $s->status("kein strip gefunden - url current koennte beschaedigt sein - versuche vorherige url zu finden",'WARN');
			# $s->{prev} = undef;
			# $s->{next} = undef;
			# my $try_url = $s->dat->{$s->dat->{__SECTIONS__}->[- (rand(5) + 2)]}->{url}; #wir einen zufälligen vorherigen eintrag aus der dat
			# if ($try_url) {
				# $s->status("versuche: " . $try_url,'WARN');
				# $s->curr(Page::new({"cmc" => $s},$try_url)); #und ändern current auf diesen wert
			# }
			# else {
				# $s->status("keine vorherige url gefunden",'WARN');
			# }
			# $s->{vorheriges_nichtmehr_versuchen} = 1;
		# }
	# }
	#$s->{vorheriges_nichtmehr_versuchen} = 1;
	$s->curr->index_prev if $s->prev;
	if ($s->next) {
		$s->goto_next();
		return 1;
	}
	else {
		if ($s->cfg("all_next_additional_url") and !$::TERM) {
			$s->goto_next($s->cfg("all_next_additional_url"));
			$s->curr->all_strips;
			$s->curr->index_prev if $s->prev;
		}
		return 0;
	}
}

sub url_home {
	my $s = shift;
	unless($s->cfg('url_home')) {
		my $uri = URI->new($s->cfg('url_start'));
		my $p = $uri->path_query;
		my $u = $uri->as_string;
		$u =~ m#(.+)\Q$p\E#; #removes path from url
		$s->cfg('url_home',$1."/");
		$s->status("DEF: url_home: " . $1 . "/", 'DEBUG');
	}
	return $s->cfg('url_home');
}

sub url_current {
	my $s = shift;
	my $url_curr = $s->usr('url_current');
	unless($url_curr) {
		my $uri = URI->new($s->cfg('url_start'));
		$url_curr = $uri->path_query;
		$s->usr('url_current',$url_curr);
		$s->status("WRITE: url_current: " . $uri->path_query, 'DEF');
		$s->usr('first',$s->curr->file(0));
	}
	return $url_curr;
}


sub curr {
	my $s = shift;
	$s->{curr} = shift if @_;
	return $s->{curr} if $s->{curr};
	$s->{curr} = Page::new({"cmc" => $s},URI->new($s->url_current)->abs($s->url_home)->as_string);
	return $s->{curr};
}

sub prev {
	my $s = shift;
	return $s->curr->prev(@_);
}

sub next {
	my $s = shift;
	return $s->curr->next(@_);
}

sub goto_next {
	my $s = shift;
	$s->curr($s->next(@_));	#die nächste seite wird die aktuelle seite
	my @urls = $s->split_url($s->curr->url);
	my $url = $urls[0] . $urls[1];
	if ($urls[1]) {
		my $not_goto = $s->cfg("not_goto");
		my $add_url = $s->cfg("all_next_additional_url");
		$s->{not_goto} = 1 if ( 
			($not_goto and ($urls[1] =~ m#$not_goto#i)) or 
			($urls[1] =~ m#(index|main)\.(php|html?)$#i) or 
			($urls[1] =~ m:#$:) or
			($urls[1] =~ m:^/$:) or
			($add_url and ($url =~ m#$add_url#))
		);
		unless ($s->{not_goto} or $s->curr->dummy) {
			$s->usr("url_current",$urls[1]);
			$s->status("URL_CURRENT: ".$s->usr("url_current") ,'DEBUG');
		}
		
	}
	# open (TMP, ">blah.txt");
	# print TMP Dumper($s);
	# print TMP "\n\n-----\n\n";
	if ($s->prev) {
		$s->prev->{prev}->{next} = undef if $s->prev->{prev};
		$s->prev->{prev} = undef;
	}
	# print TMP Dumper($s);
	# print TMP "\n";
	# die;
}

sub split_url {
	my $s = shift;
	my $url = shift;
	return 0 if (!defined $url);
	$url =~ m#(https?://[^/]*/?)(.*)#i;
	my $urlhome = $1;
	my $urlcur = "/" . $2;
	return ($urlhome,$urlcur);
}

sub release_pages {
	my $s = shift;
	$s->curr->{prev} = undef;
	$s->curr->{next} = undef;
	$s->{curr} = undef;
}

sub chk_strips { # not in use at the moment
	my $s = shift;
	my $b = 1;
	my $file = $s->{dat}->{__CFG__}->{first};
	unless ($file) {
		$s->status("KEIN FIRST",'WARN');
		return;
	}		
	$s->status("UEBERPRUEFE STRIPS",'UINFO');
	while( $b and !$::TERM ) {
		unless ($file =~ m/^dummy\|/i) {
			unless (-e './strips/' . $s->name . '/' . $file) {
				$s->status("NICHT VORHANDEN: " . $s->name . '/' . $file,'UINFO');
				my $res = lwpsc::getstore($s->{dat}->{$file}->{surl},'./strips/' . $s->name . '/' . $file);
				if (($res >= 200) and  ($res < 300)) {
					$s->status("GESPEICHERT: " . $file,'UINFO');
				}
				else {
					$s->status("FEHLER BEIM SPEICHERN: " . $s->{dat}->{$file}->{surl} . '->' . $file,'ERR');
				}
			}
			else {
				$s->status("VORHANDEN: " . $file , 'UINFO');
			}
		}
		if ($s->{dat}->{$file}->{next} and ($s->{dat}->{$file}->{next} ne $file)) {
			$file = $s->{dat}->{$file}->{next};
		}
		else {
			my @url = $s->split_url($s->{dat}->{$file}->{url});
			$s->usr('url_current',$url[1]) if $url[1];
			$b = 0;
		}
	}
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->name,'DEBUG');
}

1;
