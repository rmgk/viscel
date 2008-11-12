#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:31 06.10.2008
package Comic;

use strict;
use feature qw(say switch);
use Config::IniHash;
use Page;
use dbutil;
use dlutil;
use threads;
use Thread::Queue;
use Thread::Semaphore;

my $got_md5 = eval { require Digest::MD5; };
#use Data::Dumper;
 
use URI;
use DBI;

use vars qw($VERSION);
$VERSION = '21';

sub get_comic {
	my $s = Comic::new(@_);
	if ($s) {
		$s->get_all;
		$s->release_pages;
		$s->dbh->commit unless $s->{autocommit};
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
		$s->{dbh} = DBI->connect("dbi:SQLite:dbname=".$s->{DB},"","",{AutoCommit => $s->{autocommit},PrintError => 1});
	}
	
	my $worker_count = $s->cfg("worker") // 3;
	$s->{queue_dl} = Thread::Queue->new();
	$s->{queue_dl_finish} = Thread::Queue->new();
	$s->{semaphore} = Thread::Semaphore->new(1);
	for my $w (0..$worker_count) {
		$s->create_worker;
	}

	
	$s->status("-" x (10) . $s->name . "-" x (25 - length($s->name)),'UINFO');

	unless (-e "./strips/".$s->name) {
		mkdir("./strips/".$s->name);
		$s->status("WRITE: ".$s->{path_strips}.$s->name ,'OUT');
	}
	
	dbutil::check_table($s->dbh,"_".$s->name);
	
	return $s;
}

sub create_worker {
	my $s = shift;
	push (@{$s->{worker}}, threads->create(\&thread_save,$s->name,$s->{queue_dl},$s->{queue_dl_finish},$s->{semaphore}));
}

sub thread_save {
	my $name = shift;
	my $dl = shift;
	my $finished = shift;
	my $semaphore = shift;
	dequeue: while(my $strip = $dl->dequeue()) {
		$semaphore->up();
		my $res = 0;
		$res = dlutil::getstore($strip->[0],"./strips/".$name."/".$strip->[1],$strip->[2]);
		if (($res >= 200) and  ($res < 300)) {
			$finished->enqueue([$strip->[1],$res]);
			next dequeue;
		}
		else {
			for (0..2) {
				sleep 10;
				$res = dlutil::getstore($strip->[0],"./strips/".$name."/".$strip->[1],$strip->[2]);
				if (($res >= 200) and  ($res < 300)) {
					$finished->enqueue([$strip->[1],$res]);
					next dequeue;
				}
			}
			$finished->enqueue([$strip->[1],$res]);
			next dequeue;
		}
	}
}

sub fin_dl_clean {
	my $s = shift;
	while (my $res = $s->{queue_dl_finish}->dequeue_nb()) {
		if (($res->[1] >= 200) and  ($res->[1] < 300)) {
			$s->status("SAVED: " . $res->[0],'UINFO');
			$s->md5($res->[0]);
			$s->usr('last_save',time);
		}
		else {
			$s->status("ERROR saving : " . $res->[0] . " code: ". $res->[1],'ERR');
		}
    }
}

sub thread_cleanup {
	my $s = shift;
	foreach my $thr (@{$s->{worker}}) {
		$s->{queue_dl}->enqueue(undef,undef,undef,undef,undef,undef);
	}
	foreach my $thr (@{$s->{worker}}) {
		$thr->join;
	}
	$s->fin_dl_clean;
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
	
	$s->class_change;
	
	$s->{config}->{$key} = $value if $value;
	return $s->{config}->{$key};
}

sub class_change {
	my $s = shift;
	
	given ($s->{config}->{url_start}) {
		when (m#^http://www.anymanga.com/#) {$s->{config}->{class} //= "anymanga"};
		when (m#^http://www.mangafox.com/#) {$s->{config}->{class} //= "mangafox"};
		when (m#^http://manga.animea.net/#) {$s->{config}->{class} //= "animea"};
		when (m#^http://www.onemanga.com/#) {$s->{config}->{class} //= "onemanga"};
		when (m#^http://(www.)?cartooniverse.\w+.co.uk/#) {$s->{config}->{class} //= "cartooniverse"};
	}
	
	if ($s->{config}->{class} and !$s->{class_change}) {
		if ($s->{config}->{class} eq "onemanga") {
			$s->{config}->{regex_next} //= q#if \(keycode == 39\) {\s+window.location = '([^']+)'#;
			$s->{config}->{regex_prev} //= q#if \(keycode == 37\) {\s+window.location = '([^']+)'#;
			$s->{config}->{regex_strip_url} //= q#<img class="manga-page" src="([^"]+)"#;
			$s->{config}->{rename} //= q"strip_url#(\d+)/([^/]+)\.#01";
			$s->{config}->{worker} //= 0;
		}
		if ($s->{config}->{class} eq "animea") {
			$s->{config}->{regex_next} //= q#value="Next"\s*onClick="javascript:window.location='([^']+)'" />#;
			$s->{config}->{regex_prev} //= q#value="Previous"\s*onClick="javascript:window.location='([^']+)'" />#;
			$s->{config}->{heur_strip_url} //= "/tobemoved/|/data/";
		}
		if ($s->{config}->{class} eq "cartooniverse") {
			$s->{config}->{regex_next} //= q#<input value="next" onclick="javascript:window.location='([^']+)';" type="button">#;
			$s->{config}->{regex_prev} //= q#<input value="back" onclick="javascript:window.location='([^']+)';" type="button">#;
			$s->{config}->{rename} //= q"strip_url#(\d+)/([^/]+)\.#01";
			$s->{config}->{referer} //= q#http://www.cartooniverse.co.uk/#;
			$s->{config}->{worker} //= 0;
		}
		if ($s->{config}->{class} eq "mangafox") {
			my $url_insert = $s->{config}->{url_start};
			my $str = q"/chapter.{{chap}}/page.{{page}}/";
			$url_insert =~ s#/chapter\..+$#$str#egi;
			$s->{config}->{list_url_regex} //= q#/chapter.(?<chap>\d+)/page.(?<page>\d+)/#;
			$s->{config}->{list_url_insert} //= $url_insert;
			$s->{config}->{list_chap_regex} //= q#<option value="(\d+)"\s*(?:selected="?selected"?)?>\s*[^<]+(?:vol|ch)[^<]+</option>#;
			$s->{config}->{list_page_regex} //= q#<option value="(\d+)"\s*(?:selected="?selected"?)?>\d+</option>#;
			$s->{config}->{heur_strip_url} //= q#compressed#;
			$s->{config}->{worker} //= 0;
			$s->{config}->{referer} //= '';
			$s->{config}->{rename} //= q'url_only#^\D+$#/chapter\.(\d+)/page\.(\d+)/#';
			
		}
		if ($s->{config}->{class} eq "anymanga") {
			$s->{config}->{heur_strip_url} //= '/manga/[^/]+/\d+/\d+';
			$s->{config}->{rename} //= q"strip_url#manga/([\w-]+)/(\d+)/(\d+)/([^\.]+)\.\w{3,4}#0123";
			$s->{config}->{regex_prev} //= q"var url_back = '([^']+)';";
			$s->{config}->{regex_next} //= q"var url_next = '([^']+)';";
		}
		
		$s->{class_change} = 1; #lol
	}
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
	my $addinfo = shift;
	
	open(LOG,">>".$s->{path_log});
	print LOG $status ." -- >". $addinfo. " -->". $type . "\n";
	close LOG;
	
	if ($type =~ m/ERR|WARN|DEF|UINFO/i) {
		print $status . "\n";
	}
	
	if ($type  =~ m/ERR/i) {
		open(ERR,">>".$s->{path_err});
		print ERR $s->name() .">$type: " . $status ." -- >". $addinfo. "\n";
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
	$s->thread_cleanup;
	$s->usr('last_update',time) unless $::TERM;
	$s->status("DONE: get_all",'DEBUG');
}

sub get_next {
	my $s = shift;
	
	$s->fin_dl_clean;
	$s->curr->all_strips;
	$s->fin_dl_clean;
	
	$s->index_prev if $s->prev;
	if ($s->next and $s->next->body) {
		$s->goto_next();
		return 1;
	}
	elsif($s->cfg("archive_url")) {
		my $next_archive = $s->get_next_archive();
		return 0 unless $next_archive;
		$s->status("NEXT ARCHIVE: " . $next_archive , 'UINFO');
		$s->usr('archive_current',$next_archive);
		$next_archive =~ s!([^&])&amp;|&#038;!$1&!gs;
		my $url_arch = URI->new($next_archive)->abs($s->cfg("archive_url"))->as_string;
		my $reg_deeper = $s->cfg('archive_regex_deeper');
		unless ($reg_deeper) {
			$s->goto_next($url_arch);
			return 2;
		}
		$url_arch .= '/' unless $url_arch =~ m#/$|\.\w{3,4}$#; #ugly fix | workaround warning
		$s->status("NEXT ARCHIVE deeper, get body: ". $url_arch, 'UINFO');
		my $body = dlutil::get($url_arch);
		if ($body =~ m#$reg_deeper#is) {
			my $deep_url = URI->new($+{url} // $1)->abs($url_arch)->as_string;
			$s->status("NEXT ARCHIVE deeper: " .$deep_url, 'UINFO');
			$s->goto_next($deep_url);
			return 3;
		}
	}
	elsif ($s->cfg("all_next_additional_url") and !$::TERM) {
		$s->goto_next($s->cfg("all_next_additional_url"));
		$s->curr->all_strips;
		$s->index_prev if $s->prev;
		return 0;
	}
	return 0;
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
		$s->usr('first',$s->curr->file(0)) unless $s->curr->dummy;
	}
	return $url_curr;
}

sub curr {
	my $s = shift;
	$s->{curr} = shift if @_;
	return $s->{curr} if $s->{curr};
	$s->{curr} = Page::new({"cmc" => $s, "url" => URI->new($s->url_current)->abs($s->url_home)->as_string });
	return $s->{curr};
}

sub prev {
	my $s = shift;
	return $s->{prev} if $s->{prev};
	my @sides = $s->curr->side_urls();
	my $url = shift || $sides[0];
	my $not_goto = $s->cfg("not_goto");
	my $never_goto = $s->cfg("never_goto");
	return if 	(
					($s->curr->url eq $s->cfg("url_start")) or #nicht zur start url
					($not_goto and ($url =~ m#$not_goto#i)) or
					($never_goto and ($url =~ m#$never_goto#i))
				);
	if ($url) {
		$s->{prev} = Page::new({"cmc" => $s,'url' => $url});
	}
	else {
		$s->status("FEHLER kein prev: " . $s->curr->url,'ERR');
	}
	return $s->{prev};
}

sub next {
	my $s = shift;
	return $s->{next} if $s->{next};
	my @sides = $s->curr->side_urls();
	my $url = shift || $sides[1];
	
	if ($s->{visited_urls}->{$url}) {
		my @flags = split("",$s->usr('flags'));
		$flags[4] = 1; # loop flag
		my $fstr;
		for (0..$#flags) {
			$fstr .= $flags[$_] or 0;
		}
		$s->usr('flags',$fstr);
	}

	my $never_goto = $s->cfg("never_goto");
	return if 	(	!($url and ($url ne $s->curr->url)) or	#nicht die eigene url
					($url eq $s->cfg("url_start")) or	#nicht die start url
					($never_goto and ($url =~ m#$never_goto#i)) or
					($s->{visited_urls}->{$url})
				);
	$s->{visited_urls}->{$url} = 1;
	$s->{next} = Page::new({"cmc" => $s,'url' => $url});

	return $s->{next};
}

sub index_prev {
	my $s = shift;
	if ($s->curr->file(0) eq $s->prev->file(-1)) {
		return;
	}
	$s->dat($s->curr->file(0),'prev',$s->prev->file(-1));
	$s->prev->dat($s->prev->file(-1),'next',$s->curr->file(0));
	$s->status("VERBUNDEN: ". $s->prev->file(-1). "<->".$s->curr->file(0),'DEBUG')
}

sub index_next {
	my $s = shift;
	if ($s->curr->file(-1) eq $s->next->file(0)) {
		return;
	}
	$s->dat($s->curr->file(-1),'next',$s->next->file(0));
	$s->next->dat($s->next->file(0),'next',$s->curr->file(-1));
	$s->status("VERBUNDEN: ".$s->curr->file(-1) . "<->".$s->next->file(0),'DEBUG')
}

sub get_archives {
	my $s = shift;
	return $s->{archives} if $s->{archives};
	my $body = dlutil::get($s->cfg('archive_url'));
	my $regex = $s->cfg('archive_regex');
	my @archives;
	while ($body =~ m#$regex#gis) {
		push(@archives,$+{url} // $1) unless ($s->cfg('archive_reverse'));
		unshift(@archives,$+{url} // $1) if ($s->cfg('archive_reverse'));
	}
	$s->{archives} = \@archives;
	return $s->{archives};
}

sub get_next_archive {
	my $s = shift;
	my @archives = @{$s->get_archives()};
	$s->status("ARCHIVE count: " . scalar(@archives),"UINFO") unless $s->{info_arch_count};
	$s->{info_arch_count} = 1;
	return 0 unless @archives;
	my $arch_curr = $s->usr('archive_current');
	return $archives[1] unless ($arch_curr);
	for (my $i = 0;$i <= $#archives;$i++) {
		if ($archives[$i] eq $arch_curr) {
			return $archives[$i + 1];
		}
	}	
	return 0;
}

sub goto_next {
	my $s = shift;
	$s->{prev} = $s->curr;
	$s->curr($s->next(@_));	#die nächste seite wird die aktuelle seite
	delete $s->{next};
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
	delete $s->{prev};
	delete $s->{curr};
	delete $s->{next};
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
				my $res = lwpsc::getstore($s->{dat}->{$file}->{surl},'./strips/' . $s->name . '/' . $file,$s->cfg("referer"));
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

sub md5 {
	my $s = shift;
	my $file_name = shift;
	if($got_md5) {
		if (open(FILE, "./strips/".$s->name."/$file_name")) {
			binmode(FILE);
			$s->dat($file_name,'md5',Digest::MD5->new->addfile(*FILE)->hexdigest);
			close FILE;
		}
		else {
			$s->status("DATEIFEHLER " . "./strips/".$s->name."/$file_name" . "konnte nicht geöfnet werden" ,'ERR')
		}
	}
	else {
		$s->status("md5 modul (Digest::MD5) nicht gefunden",'DEBUG') unless ($s->{_md5debug});
		$s->{_md5debug} = 1;
	}
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->name,'DEBUG');
}

1;
