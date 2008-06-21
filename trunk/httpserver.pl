#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:16 11.01.2008

use HTTP::Daemon;
use HTTP::Status;
use Config::IniHash;

use strict;

use vars qw($VERSION);
$VERSION = '1.12';
my $dat = {};
my $d = HTTP::Daemon->new(LocalPort => 80);

my $usr;
my $usrfix;
my $cfg;

my $res = HTTP::Response->new( 200, 'erfolg', ['Content-Type','text/html; charset=iso-8859-1']);
my %index;
my @Kategorien = qw(gelesen beobachten andere single_strip_joke mekrwuerdig uninteressant);


print "Please contact me at: <URL:", $d->url, "_index>\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if ($r->method eq 'GET') {
			if ($r->url->path =~ m#^/strips/(.*?)/(.*)$#) {
				$c->send_file_response("./strips/$1/$2");
			}
			else {
				&load_dats();
				$res->content(cc($r->url->path));
				$c->send_response($res);
				WriteINI("server.ini",$usr);
				WriteINI('data/_CFG_',$cfg);
			}
		}
		$c->close;
	}
	undef($c);
}


sub cc {
	my $url_path = shift;
	#############################################################
	#index wird aufgerufen 
	#############################################################
	if ($url_path =~ m#/_index#) {
		my $index = '<body text="#009900" bgcolor="#000000"> <a href="_count_all">Load all strips (takes a while and needs a lot of memory)</a> <br>';
		my %kategorien;
		my %kat_count = undef;
		my %kat_counted = undef;
		foreach my $comic (sort { lc($a) cmp lc($b) } @{$usr->{__SECTIONS__}}) {
			next if $comic =~ m/_.*?_/;
			unless ($usr->{$comic}->{kategorie}) {
				$usr->{$comic}->{kategorie} = 'andere';
			}
			
			my $b = 0;
			foreach (@Kategorien) {
				$b = 1 if $_ eq  $usr->{$comic}->{kategorie};
			}
			unless ($b) {
				unshift(@Kategorien,$usr->{$comic}->{kategorie});
			}						 
			
			$kategorien{$usr->{$comic}->{kategorie}} = ("-"x 20) .$usr->{$comic}->{kategorie}. ("-"x 20)."<br><table>" unless $kategorien{$usr->{$comic}->{kategorie}} ;
			$kategorien{$usr->{$comic}->{kategorie}} .= qq(<tr><td><a href="/$comic.ndx">$comic</a></td>)
				. ($cfg->{$comic}->{'first'} ? qq(<td><a href="/$comic/$cfg->{$comic}->{'first'}.strp">Anfang<a></td>) : "<td></td>") 
				. ($usr->{$comic}->{'aktuell'} ? qq(<td><a href="/$comic/$usr->{$comic}->{'aktuell'}.strp">Aktuell</a></td>) : "<td></td>") 
				. ($usr->{$comic}->{bookmark} ? qq(<td><a href="/$comic/$usr->{$comic}->{bookmark}.strp">bookmark</a></td>) : "<td></td>") 
				. ($cfg->{$comic}->{'last'} ? qq(<td><a href="/$comic/$cfg->{$comic}->{'last'}.strp">Ende</a></td>) : "<td></td>") .
				qq(<td><a href="/$comic/_kategorie_">Kategorie</a></td><td>$cfg->{$comic}->{strip_count}</td><td>$cfg->{$comic}->{strips_counted}</td></tr>\n);
			$kat_count{$usr->{$comic}->{kategorie}} += $cfg->{$comic}->{strip_count};
			$kat_counted{$usr->{$comic}->{kategorie}} += $cfg->{$comic}->{strips_counted};
			
		}
		foreach my $key (@Kategorien) {
			next unless $kategorien{$key};
			$index .= $kategorien{$key} . "</table>ges: $kat_count{$key} $kat_counted{$key}<br>";
		}
		$index .= '</body>';
		return $index;
	}
	#############################################################
	#alle comics laden
	#############################################################
	if ($url_path =~ m#/_count_all#) {
		foreach my $comic (@{$usr->{__SECTIONS__}}) {
			next if ($comic eq "_CFG_");
			load($comic);
		}
	}
	
	#############################################################
	#comic wird kategorisiert
	#############################################################
	if ($url_path =~ m#^/(.*?)/_kategorie_$#) {
		my $comic = $1;
		my $kategorien = '<body bgcolor="#000000">';
		for my $kategorie (@Kategorien) {
			$kategorien .= qq(<a href="/$comic/_kategorie_/$kategorie">$kategorie<a><br>);
		}
		$kategorien .= '</body>';
		return $kategorien;
	}

	if ($url_path =~ m#^/(.*?)/_kategorie_/(.*)#) {
		my $comic = $1;
		my $kategorie = $2;
		$usr->{$comic}->{kategorie} = $kategorie;
		my $con = '<body bgcolor="#000000"><a href="/_index">kategorie erfolgreich geändert, zurück zum index.<a></body>';
		return $con;
	}
	#############################################################
	#index seite des einzelnen comics
	#############################################################
	elsif ($url_path =~ m#^/([^/]*?).ndx$#) {
		my $comic = $1;
		load($comic);
		unless ($index{$comic}) {
			print "erstelle index ...\n";
			my $strip = $cfg->{$comic}->{'first'};
			
			$index{$comic} .='<body bgcolor="#000000">';
			my $i;
			while ($dat->{$comic}->{$strip}) {
				$i++;
				my $title = $dat->{$comic}->{$strip}->{'title'};
				$title =~ s/-§-//g;
				$title =~ s/!§!/|/g;
				$title =~ s/~§~/~/g;
				$index{$comic} .= qq(<a href="./$comic/$strip.strp">$i : $strip : $title</a><br>);
				if ($strip eq $dat->{$comic}->{$strip}->{'next'}) {
					print "selbstreferenz in $comic bei $strip gefunden\n" ;
					last;
				}
				else {
					print "weiter: " .$strip." -> " .$dat->{$comic}->{$strip}->{'next'} . "\n";
					$strip = $dat->{$comic}->{$strip}->{'next'};
				}
			}
			$index{$comic} .= "</body>";
			print "beendet\n";
		}
		return$index{$comic};
	}
	#############################################################
	#einzelne seite des strips
	#############################################################
	elsif ($url_path =~ m#^/(.*?)/(.*)\.strp(=bookmark)?$#) {
		my $comic = $1;
		my $strip = $2;
		my $bookmark = $3;
		my $return;
		load($comic);
		if ($strip) {
			my $title = $dat->{$comic}->{$strip}->{'title'};
			$title =~ s/-§-//g;
			$title =~ s/!§!/|/g;
			$title =~ s/~§~/~/g;
			
			$strip =~ s/%7C/|/ig;
			$return =
				qq( <title>$title</title>
					<body bgcolor="#000000" text="#009900">
					<div align="center">
					<!-- $title<br> -->
					<img src="/strips/$comic/$strip"><br><br>
					<a href="$dat->{$comic}->{$strip}->{'prev'}.strp">zurück</a>
					<a href="$dat->{$comic}->{$strip}->{'next'}.strp">weiter</a>
					<br><a href="/_index">Index</a>
					<a href="$dat->{$comic}->{$strip}->{'url'}">Site</a>
					<a href="/$comic.ndx">$comic</a> 
					<br><a href="$strip.strp=bookmark">Bookmark</a>
					</div>
				</body>)
			;
			$usr->{$comic}->{'aktuell'} = $strip;
			$usr->{$comic}->{bookmark} = $strip if $bookmark;
		}
		else {
			$return = 
				qq( <body bgcolor="#000000" text="#009900">
					<div align="center">
					leider kein weiterer strip verfügbar
					<a href="/_index">
					<br>Index</a>
					</div>
				</body>)
			;
		}
		return $return;
	}
}



sub load {
	my $comic = shift;
	if (!$dat->{$comic} or ($comic eq 'index')) {
		print "$comic.dat geladen\n";
		$dat->{$comic} = ReadINI("./data/$comic.dat",{'case'=>'preserve', 'sectionorder' => 1});
		my @strips = @{$dat->{$comic}->{__SECTIONS__}};
		unless ($cfg->{$comic}->{first}) {
			
			my $first = $strips[0];
			while ($dat->{$comic}->{$first}->{prev}) {
				last if $first eq $dat->{$comic}->{$first}->{prev};
				$first = $dat->{$comic}->{$first}->{prev};
				print "zurueck: ". $comic."/".$dat->{$comic}->{$first}->{'next'} ." -> " . $first . "\n";
			}	
			$cfg->{$comic}->{first} = $first;
		}
		$cfg->{$comic}->{strip_count} = $#strips +1;
		my $point = $cfg->{$comic}->{first};
		my $i = 1;
		while ($dat->{$comic}->{$point}->{next}) {
			if ($point eq $dat->{$comic}->{$point}->{'next'}) {
				print "selbstreferenz in $comic bei $point gefunden\n";
				last;
			}
			else {
				$i ++;
				print "weiter: ". $comic."/".$point ." -> " . $dat->{$comic}->{$point}->{next} . " $i<" . $cfg->{$comic}->{strip_count} . "\n";
				$point = $dat->{$comic}->{$point}->{next};
				last if ($i > $cfg->{$comic}->{strip_count});
			}
		}
		$cfg->{$comic}->{last} = $point;
		$cfg->{$comic}->{strips_counted} = $i;
	}
}



sub load_dats {

	$usrfix = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});
	if (-e 'server.ini') {
		$usr = ReadINI('server.ini',{'case'=>'preserve', 'sectionorder' => 1});
		@{$usr->{__SECTIONS__}} = @{$usrfix->{__SECTIONS__}};
	}
	else {
		$usr = $usrfix;
	}
	$cfg = ReadINI('data/_CFG_',{'case'=>'preserve', 'sectionorder' => 1});

}
