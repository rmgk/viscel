#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:31 06.10.2008
package Comic;

use 5.010;
use strict;
use warnings;

use Page;
use dbutil;
use dlutil;

#use Data::Dumper;
 
use URI;
use DBI;

our $VERSION;
$VERSION = '27';

sub get_comic {
	my $s = Comic->new(@_);
	if ($s) {
		$s->get_all;
		$s->release_pages;
		$s->dbh->commit unless $s->{autocommit};
		$s->dbh->disconnect unless $s->{dbh_no_disconnect};
		#flush out the log
		open(LOG,">>".$s->{path_log});
		print LOG join("\n",@{$s->{LOG}}) . "\n"; 
		close LOG;
		return $s;
	}
	else {
		return 0;
	}
}

sub new {
	my $class = shift;
	my $s = shift || {};
	bless $s,$class;
	
	$s->{DB} //= 'comics.db';
	$s->{path_strips} //= "./strips/";
	$s->{path_log} //= "log.txt";
	$s->{path_err} //= "err.txt";
	$s->{_CONF} //= 'comic.ini';
	
	$s->{LOG} = [];
	
	if ($s->{dbh}) {
		$s->{dbh_no_disconnect} = 1;
	}
	else {
		$s->{dbh} = DBI->connect("dbi:SQLite:dbname=".$s->{DB},"","",{AutoCommit => $s->{autocommit},PrintError => 1});
	}
	
	$s->status("-" x (10) . $s->name . "-" x (25 - length($s->name)),'UINFO');

	unless (-e $s->{path_strips} . $s->name) {
		mkdir $s->{path_strips} unless (-e $s->{path_strips} );
		mkdir($s->{path_strips} . $s->name);
		$s->status("WRITE: " . $s->{path_strips} . $s->name ,'OUT');
	}
	dbutil::check_table($s->dbh,"_".$s->name);
	
	return $s;
}

sub get_all {
	my $s = shift;
	$s->status("START: get_all",'DEBUG');
	while (!$::TERM) {
		last unless $s->curr->all_strips();
		last if $::TERM;
		last unless $s->get_next();
	};
	$s->usr('last_update',time) unless $::TERM;
	$s->status("DONE: get_all",'DEBUG');
}

sub get_next {
	my $s = shift;
	if ($s->goto_next()) {
		return 1;
	}
	elsif($s->cfg("archive_url")) {
		my $url_archive = $s->url_next_archive();
		if ($url_archive) {
			return 2 if $s->goto_next($url_archive);
		}
	}
	return 0;
}

sub goto_next {
	my $s = shift;
	$s->{prev} = $s->curr;
	return 0 unless ($s->next(@_) and $s->next->body());
	$s->curr($s->next());	#next page becomes current
	delete $s->{next};		#we delete original after copying
	unless ($s->curr->file(0) eq $s->prev->file(-1)) { #connecting last strip of previous page with first strip of current page
		$s->dat($s->curr->file(0),'prev',$s->prev->file(-1));
		$s->prev->dat($s->prev->file(-1),'next',$s->curr->file(0));
	}
	return ($s->url_current($s->curr->url()) or 1); #we return the url if it was set as current or true
}

{ #page accessors

sub curr {
	my ($s,$ref) = @_;
	if ($ref) {
		if (ref($ref) eq "Page") {
			$s->{curr} = $ref;
		}
		else {
			$s->{curr} = Page->new({"cmc" => $s,'url' => $ref});
		}
	}
	$s->{curr} //= Page->new({"cmc" => $s, "url" => $s->url_current});
	return $s->{curr};
}

sub prev { 
	my ($s,$ref) = @_;
	if ($ref) {
		(ref($ref) eq "Page") ? 
			$s->{prev} = $ref :
			$s->{prev} = Page->new({"cmc" => $s,'url' => $ref});
	}
	return $s->{prev} if $s->{prev};

	my $url =  $s->curr->url_prev();
	($url) ?
		$s->{prev} = Page->new({"cmc" => $s,'url' => $url}) :
		$s->status("FEHLER kein prev: " . $s->curr->url,'ERR');
	return $s->{prev};
}

sub next {
	my ($s,$ref) = @_;
	if ($ref) {
		(ref($ref) eq "Page") ? 
			$s->{next} = $ref :
			$s->{next} = Page->new({"cmc" => $s,'url' => $ref});
	}
	return $s->{next} if $s->{next};
	
	my $url = $s->curr->url_next();
	return unless $url;
	if ($s->{visited_urls}->{$url}) {
		my $flags = $s->usr('flags') // '';
		$flags .= 'l' unless $flags =~ /l/;
		$s->usr('flags',$flags);
		return;
	}
	$s->{visited_urls}->{$url} = 1;
	
	$s->{next} = Page->new({"cmc" => $s,'url' => $url});
	return $s->{next};
}

}

{ #archive wrapper

sub url_next_archive {
	my ($s) = @_;
	my $next_archive = $s->u_get_next_archive();
	return 0 unless $next_archive;
	$s->status("NEXT ARCHIVE: " . $next_archive , 'UINFO');
	$s->usr('archive_current',$next_archive);
	$next_archive =~ s!([^&])&amp;|&#038;!$1&!gs;
	my $url_arch = URI->new($next_archive)->abs($s->cfg("archive_url"))->as_string;
	my $reg_deeper = $s->cfg('archive_regex_deeper');
	unless ($reg_deeper) {
		return $url_arch;
	}
	$url_arch .= '/' unless $url_arch =~ m#/$|\.\w{3,4}$#; #ugly fix | workaround warning
	$s->status("NEXT ARCHIVE deeper, get body: ". $url_arch, 'UINFO');
	my $body = dlutil::get($url_arch);
	if ($body =~ m#$reg_deeper#is) {
		my $deep_url = URI->new($+{url} // $1)->abs($url_arch)->as_string;
		$s->status("NEXT ARCHIVE deeper: " .$deep_url, 'UINFO');
		return $deep_url;
	}
	return 0;
}

sub u_get_next_archive {
	my $s = shift;
	my @archives = @{$s->ar_get_archives()};
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

sub ar_get_archives {
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

}

{ #accessors and utilitys

sub dbh {
	my $s = shift;
	return $s->{dbh};
}

sub cfg { #gibt die cfg des aktuellen comics aus # hier sollten nur nicht veränderliche informationen die zum download der comics benötigt werden drinstehen
	my $s = shift;
	my ($key,$value) = @_;
	
	unless ($s->{config}) {
		die "no config file ". $s->{_CONF} unless(-e $s->{_CONF});
		my $config = dbutil::readINI($s->{_CONF});
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
	return if $s->{class_change};
	$s->{class_change} = 1;
	
	given ($s->{config}->{url_start}) {
		when (m#^http://www.anymanga.com/#) {$s->{config}->{class} //= "anymanga"};
		when (m#^http://www.mangafox.com/#) {$s->{config}->{class} //= "mangafox"};
		when (m#^http://manga.animea.net/#) {$s->{config}->{class} //= "animea"};
		when (m#^http://www.onemanga.com/#) {$s->{config}->{class} //= "onemanga"};
		when (m#^http://(www.)?cartooniverse.\w+.co.uk/#) {$s->{config}->{class} //= "cartooniverse"};
		when (m#^http://\w+.comicgenesis.com/#) {$s->{config}->{class} //= "comicgenesis"};
	}
	
	if ($s->{config}->{class}) {
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
			$s->{config}->{heur_strip_url} //= q#img.cartooniverse#;
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
			$s->{config}->{rename} //= q'url_only#^\D+$|^(\d\d_)?\d\d\.\w{3,4}$#/chapter\.(\d+)/page\.(\d+)/#';
			$s->{config}->{never_goto} //= q#/end/#;
		}
		if ($s->{config}->{class} eq "anymanga") {
			$s->{config}->{heur_strip_url} //= '/manga/[^/]+/\d+/\d+';
			$s->{config}->{rename} //= q"strip_url#manga/([\w-]+)/(\d+)/(\d+)/([^\.]+)\.\w{3,4}#0123";
			$s->{config}->{regex_prev} //= q"var url_back = '([^']+)';";
			$s->{config}->{regex_next} //= q"var url_next = '([^']+)';";
		}
		if ($s->{config}->{class} eq "comicgenesis") {
			$s->{config}->{worker} //= 0;
			$s->{config}->{url_start} =~ m#(^http://\w+.comicgenesis.com/)#;
			$s->{config}->{referer} //= $1;
		}
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

sub dat { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu dem comic und seinen strips gespeichert
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
	my ($s,$url) = @_;
	if ($url) {
		my ($curl) = ($url =~ m#https?://[^/]+(/.*)$#i);
		if ($curl) {
			my $reNot_goto = $s->cfg("not_goto");
			$s->{not_goto} = 1 if ( 
				($reNot_goto and ($curl =~ m#$reNot_goto#i)) or 
				($curl =~ m#(index|main)\.(php|html?)$#i) or 
				($curl =~ m:#$:) or
				($curl =~ m:^/$:)
			);
			unless ($s->{not_goto} or $s->curr->dummy) {
				$s->usr("url_current",$url);
				$s->status("URL_CURRENT: ". $url ,'DEBUG');
			}
		}
	}
	$url //= $s->usr('url_current');
	unless($url) {
		$url = $s->cfg('url_start');
		$s->usr('url_current',$url);
		$s->status("WRITE: url_current: " . $url, 'DEF');
		$s->usr('first',$s->curr->file(0)) unless $s->curr->dummy;
	}
	$url = URI->new($url)->abs($s->url_home)->as_string unless $url =~ m#^https?://#i;
	return $url;
}

sub status {
	my $s = shift;
	my $status = shift;
	my $type = shift;
	my $addinfo = shift;
	
	push (@{$s->{LOG}}, $status . ($addinfo ? " -- >". $addinfo : "") . " -->". $type);
	
	if ($type =~ m/ERR|WARN|DEF|UINFO/i) {
		print $status . "\n";
	}
	
	if ($type  =~ m/ERR/i) {
		open(ERR,">>".$s->{path_err});
		print ERR $s->name() .">$type: " . $status ." -- >". ($addinfo // ""). "\n";
		close ERR;
	}
	return 1;
}

sub release_pages {
	my $s = shift;
	delete $s->{prev};
	delete $s->{curr};
	delete $s->{next};
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->name,'DEBUG');
}

}

1;
