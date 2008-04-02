#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:16 11.01.2008

use HTTP::Daemon;
use HTTP::Status;
use Config::IniHash;

use strict;

use vars qw($VERSION);
$VERSION = '1.7';
my $dat = {};
my $d = HTTP::Daemon->new(LocalPort => 80);

my $usr;
my $usrfix = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});
if (-e 'server.ini') {
	$usr = ReadINI('server.ini',{'case'=>'preserve', 'sectionorder' => 1});
	$usr->{__SECTIONS__} = $usrfix->{__SECTIONS__};
}
else {
	$usr = $usrfix;
}
my $cfg = ReadINI('data/_CFG_',{'case'=>'preserve', 'sectionorder' => 1});

my $res = HTTP::Response->new( 200, 'erfolg', ['Content-Type','text/html; charset=iso-8859-1']);
my %index;
my @Kategorien = qw(gelesen beobachten andere mekrwuerdig uninteressant);


print "Please contact me at: <URL:", $d->url, "_index>\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if ($r->method eq 'GET') {
			if ($r->url->path =~ m#^/strips/(.*?)/(.*)$#) {
				$c->send_file_response("./strips/$1/$2");
			}
			else {
				$cfg = ReadINI('data/_CFG_',{'case'=>'preserve', 'sectionorder' => 1});
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
		my $index = '<body text="#009900" bgcolor="#000000">';
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
			
			$kategorien{$usr->{$comic}->{kategorie}} = ("-"x 20) .$usr->{$comic}->{kategorie}. ("-"x 20)."<br>" unless $kategorien{$usr->{$comic}->{kategorie}} ;
			$kategorien{$usr->{$comic}->{kategorie}} .= qq(<a href="/$comic.ndx">$comic</a> 
				<a href="/$comic/$cfg->{$comic}->{'first'}.strp">Anfang<a> 
				<a href="/$comic/$usr->{$comic}->{'aktuell'}.strp">Aktuell</a>
				<a href="/$comic/$usr->{$comic}->{bookmark}.strp">bookmark</a>
				<a href="/$comic/$cfg->{$comic}->{'last'}.strp">Ende</a> 
				<a href="/$comic/_kategorie_">Kategorie</a> 
				$cfg->{$comic}->{strip_count} $cfg->{$comic}->{strips_counted}
				<br>\n);
			$kat_count{$usr->{$comic}->{kategorie}} += $cfg->{$comic}->{strip_count};
			$kat_counted{$usr->{$comic}->{kategorie}} += $cfg->{$comic}->{strips_counted};
			
		}
		foreach my $key (@Kategorien) {
			next unless $kategorien{$key};
			$index .= $kategorien{$key} . "ges: $kat_count{$key} $kat_counted{$key}<br>";
		}
		$index .= '</body>';
		return $index;
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
				$index{$comic} .= qq(<a href="./$comic/$strip.strp">$i : $strip : $dat->{$comic}->{$strip}->{'title'}</a><br>);
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
		print "3 : $3 : 4 : $4\n";
		my $return;
		load($comic);
		if ($strip) {
			$strip =~ s/%7C/|/ig;
			$return =
				qq( <title>$dat->{$comic}->{$strip}->{'title'}</title>
					<body bgcolor="#000000" text="#009900">
					<div align="center">
					<!-- $dat->{$comic}->{$strip}->{'title'}<br> -->
					<img src="/strips/$comic/$2"><br><br>
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
			print "bookmark : $bookmark\n";
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
				print "weiter: ". $comic."/".$point ." -> " . $dat->{$comic}->{$point}->{next} . "\n";
				$point = $dat->{$comic}->{$point}->{next};
			}

			
		}
		$cfg->{$comic}->{last} = $point;
		$cfg->{$comic}->{strips_counted} = $i;
	}
}