#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#13:37 12.07.2008

use lib "./lib";

use HTTP::Daemon;
use HTTP::Status;
use Config::IniHash;
use CGI qw(:standard *table);

use strict;

use vars qw($VERSION);
$VERSION = '2.4';

my $d = HTTP::Daemon->new(LocalPort => 80);

my $sdat;
my $srv;

my $res = HTTP::Response->new( 200, 'erfolg', ['Content-Type','text/html; charset=iso-8859-1']);
my %index;

print "Please contact me at: <URL:", $d->url,">\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if ($r->method eq 'GET') {
			if ($r->url->path =~ m#^/strips/(.*?)/(.*)$#) {
				$c->send_file_response("./strips/$1/$2");
			}
			else {
				restore_parameters($r->url->query);
				if ($r->url->path =~ m#^/comics$#) {
					$res->content(&ccomic);
				}
				elsif ($r->url->path =~ m#^/tools$#) {
					$res->content(&ctools);
				}
				else {
					$res->content(&cindex);
				}
				$c->send_response($res);
			}
		}
		$c->close;
		&write_dat()
	}
	undef($c);
}


sub kategorie {
	my $kat = shift;
	my $ord = shift;
	if ($ord) {
		&dat->{_CFG_}->{kategorien} = $ord;
		return split(',',&dat->{_CFG_}->{kategorien});
	}
	&dat->{_CFG_}->{kategorien} = &dat->{_CFG_}->{kategorien} || "gelesen,beobachten,andere,uninteressant";
	&dat->{_CFG_}->{kategorien} .= ','.$kat if $kat;
	return split(',',&dat->{_CFG_}->{kategorien});
}

sub sdat {
	my $comic = shift;
	if ($comic) {
		return load($comic);
	}
	return undef;
}

sub dat {
	return &load_dat;
}

sub comics {
	return sort { lc $a cmp lc $b} grep {$_ ne '_CFG_'} @{dat->{__SECTIONS__}};
}

sub kopf {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	return start_html(-title=>$title. " - ComCol http $VERSION" ,-BGCOLOR=>'black', -TEXT=>'#009900',
							-head=>[Link({-rel=>'index',	-href=>"/"	})			,
                            $next ?	Link({-rel=>'next',		-href=>$next})	: undef	,
                            $prev ?	Link({-rel=>'previous',	-href=>$prev})	: undef	,
							$first?	Link({-rel=>'first',	-href=>$first})	: undef	,
							$last ?	Link({-rel=>'last',		-href=>$last})	: undef	,]);
}


sub cindex {
	my %kat;
	my $ret = &kopf("Index");
	my %kat_count;
	my %kat_counted;
	$ret .= "Tools:" . br;
	$ret 	.=	a({-href=>"/tools?tool=load_all"},"Load all Comics") . br 
			.	a({-href=>"/tools?tool=kategoriereihenfolge"},"Kategoriereihenfolge ändern"). br 
			.	br;
	$ret .= "Inhalt:" . br;
	foreach (kategorie) {
		$ret .= a({href=>"#$_"},$_) . br;
		$kat{$_} = ("-"x 20).a({name=>$_},$_).("-"x 20).br.start_table;
	}
	$ret .= br;
	foreach my $comic (&comics) {
		dat->{$comic}->{kategorie} = dat->{$comic}->{kategorie} || 'andere';
		$kat{dat->{$comic}->{kategorie}} .= Tr([
			td([
			a({-href=>"/comics?comic=$comic"},$comic) ,
			dat->{$comic}->{'first'} ? a({-href=>"/comics?comic=$comic&strip=".dat->{$comic}->{'first'}},"Anfang") : undef ,
			dat->{$comic}->{'aktuell'} ? a({-href=>"/comics?comic=$comic&strip=".dat->{$comic}->{'aktuell'}},"Aktuell") : undef ,
			dat->{$comic}->{'bookmark'} ? a({-href=>"/comics?comic=$comic&strip=".dat->{$comic}->{'bookmark'}},"bookmark") : undef ,
			dat->{$comic}->{'last'} ? a({-href=>"/comics?comic=$comic&strip=".dat->{$comic}->{'last'}},"Ende") : undef ,
			a({href=>"/tools?tool=kategorie&comic=$comic"},'Kategorie'),&dat->{$comic}->{strip_count},&dat->{$comic}->{strips_counted} ,
			a({href=>"/tools?tool=datalyzer&comic=$comic"},'Datalyzer')
			])
		]);
		$kat_count{dat->{$comic}->{kategorie}} += &dat->{$comic}->{strip_count};
		$kat_counted{dat->{$comic}->{kategorie}} += &dat->{$comic}->{strips_counted};
	}

	my %double;
	foreach (&kategorie) {
		$double{$_} = 1;
	}
	foreach (sort keys %kat) {
		unless ($double{$_}) {
			&kategorie($_) if $_;
			$double{$_} = 1;
		}
	}
	
	foreach (&kategorie) {
		$ret .= $kat{$_};
		$ret .=  Tr([td([undef,undef,undef,undef,undef,undef,$kat_count{$_},$kat_counted{$_}])]) . end_table ;
	}
	return $ret . end_html;
}


sub ccomic {
	my $comic = param('comic');
	my $strip = param('strip');
	my $ret = &kopf("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	if ($strip) {
		my $return;
		my $title = sdat($comic)->{$strip}->{'title'};
		$title =~ s/-§-//g;
		$title =~ s/!§!/|/g;
		$title =~ s/~§~/~/g;
		
		$strip =~ s/%7C/|/ig;
		$return = &kopf($title,
					sdat($comic)->{$strip}->{'prev'}?"/comics?comic=$comic&strip=".sdat($comic)->{$strip}->{'prev'}:"0",
					sdat($comic)->{$strip}->{'next'}?"/comics?comic=$comic&strip=".sdat($comic)->{$strip}->{'next'}:"0",
					dat->{$comic}->{first}	?"/comics?comic=$comic&strip=".dat->{$comic}->{first}	:"0",
					dat->{$comic}->{last}	?"/comics?comic=$comic&strip=".dat->{$comic}->{last}	:"0",
					);
					
		$return .= div({-align=>"center"},
				(-e "./strips/$comic/$strip") ? img({-src=>"/strips/$comic/$strip"}) :
				sdat($comic)->{$strip}->{surl}!~m/^dummy/ ? img({-src=>sdat($comic)->{$strip}->{surl}}) . br . 
				a({-href=>"/tools?tool=download&comic=$comic&strip=$strip"},"(download)")	:
				"Diese Seite ist nur ein Dummy. Es könnte sein, dass der strip nicht korrekt heruntergeladen werden konnte."
				,br,br,
				sdat($comic)->{$strip}->{'prev'}?a({-href=>"/comics?comic=$comic&strip=".sdat($comic)->{$strip}->{'prev'}},"zurück"):undef,
				sdat($comic)->{$strip}->{'next'}?a({-href=>"/comics?comic=$comic&strip=".sdat($comic)->{$strip}->{'next'}},"weiter"):undef,br,
				a({-href=>"/"},"Index"),
				sdat($comic)->{$strip}->{'url'}?a({-href=>sdat($comic)->{$strip}->{'url'}},"Site"):undef,
				a({-href=>"/comics?comic=$comic"},$comic),br, 
				a({-href=>"/comics?comic=$comic&strip=$strip&bookmark=1"},"Bookmark")
				);
		dat->{$comic}->{'aktuell'} = $strip;
		dat->{$comic}->{'bookmark'} = $strip if param('bookmark');
		return $return . end_html;
	}
	else {
		unless ($index{$comic}) {
			my %double;
			print "erstelle index ...\n";
			my $strip = dat->{$comic}->{'first'};
			
			$index{$comic} = &kopf($comic);
			
			my $i;
			while (sdat($comic)->{$strip}) {
				if ($double{$strip}) {
					print "loop gefunden, breche ab\n" ;
					last
				}
				$double{$strip} = 1;

				$i++;
				my $title = sdat($comic)->{$strip}->{'title'};
				$title =~ s/-§-//g;
				$title =~ s/!§!/|/g;
				$title =~ s/~§~/~/g;
				$index{$comic} .= a({href=>"./comics?comic=$comic&strip=$strip"},"$i : $strip : $title") . (&dat->{_CFG_}->{thumb}?img({-height=>&dat->{_CFG_}->{thumb}, -src=>"/strips/$comic/$strip"}):undef) . br;
				if ($strip eq sdat($comic)->{$strip}->{'next'}) {
					print "selbstreferenz gefunden, breche ab\n" ;
					last;
				}
				else {
					print "weiter: " .$strip." -> " . sdat($comic)->{$strip}->{'next'} . "\n";
					$strip = sdat($comic)->{$strip}->{'next'};
				}
			}
			$index{$comic} .= end_html;
			print "beendet\n";
		}
		return$index{$comic};
	}
}


sub ctools {
	my $tool = param('tool');
	my $comic = param('comic');
	if ($tool eq "kategorie") {
		my $kategorie = param('kategorie');
		if ($kategorie) {
			dat->{$comic}->{kategorie} = $kategorie;
			return &kopf($comic . " Kategorie geändert") ."$comic kategorie erfolgreich nach $kategorie geändert" . br . br . &cindex();
		}
		my $res = &kopf($comic." Kategorie ändern");
		
		$res .= start_form("GET","tools");
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"kategorie");
		$res .= radio_group(-name=>'kategorie',
	                             -values=>[&kategorie],
	                             -default=>dat->{$comic}->{kategorie},
	                             -linebreak=>'true');
		$res .= submit('ok');
		$res .= end_form;
		$res .= end_html;;
		return $res;	
	}	
	if ($tool eq "load_all") {
		my $time = time;
		foreach (&comics) { sdat($_); }
		return "benötigte ". (time - $time) ." sekunden";
	}
	if ($tool eq "kategoriereihenfolge") {
		my $res = &kopf("Kategoriereihenfolge ändern");
		my $i;
		if (param('order')) {
			my @kat = &kategorie;
			my @ord = split("-",param('order'));
			my @kat2 = @kat[@ord];
			&kategorie(0,join(",",@kat2));
		}
		$res .= table(Tr( [map {td([$i++,$_])} &kategorie]));
		$res .= start_form("GET","tools");
		$res .= hidden('tool',"kategoriereihenfolge");
		$res .= textfield(-name=>'order', -default=>join('-',0..$i-1));
		$res .= submit('ok');
		$res .= br . br .  a({-href=>"/"},"Index");
		return $res . end_html;
	}
	if ($tool eq "download") {
		require dlutil;
		my $strip = param('strip');
		&dlutil::getstore(sdat($comic)->{$strip}->{surl},"./strips/$comic/$strip");
		return &ccomic;
	}
	if ($tool eq "datalyzer") {
		my %d;
		my @strps = keys %{&sdat($comic)};
		foreach my $strp (@strps) {
			next if $strp eq '__SECTIONS__';
			$d{count}->{$d{count}->{n}} = $strp;
			$d{count}->{n}++;
			$d{strps}->{$strp} = \%{&sdat($comic)->{$strp}};
			if ($strp =~ m/^dummy/) {
				$d{dummy}->{$d{dummy}->{n}} = $strp;
				$d{dummy}->{n}++ ;
			}
			if (sdat($comic)->{$strp}->{prev} and sdat($comic)->{$strp}->{next}) {  #hat prev und next
				$d{prevnext}->{$d{prevnext}->{n}} = $strp;
				$d{prevnext}->{n}++ ;
			} elsif (sdat($comic)->{$strp}->{prev}) { #hat nur prev
				$d{prev}->{$d{prev}->{n}} = $strp;
				$d{prev}->{n}++;
			} elsif (sdat($comic)->{$strp}->{next}) { #hat nur next
				$d{next}->{$d{next}->{n}} = $strp;
				$d{next}->{n}++;
			} else {									# hat keines von beiden
				$d{none}->{$d{none}->{n}} = $strp;
				$d{none}->{n}++;
			}
			
			my $next = &sdat($comic)->{$strp}->{next};
			if ($next and !(&sdat($comic)->{$next}->{prev} eq $strp)) { #if prev of next is not self
				$d{backlink_next}->{$d{backlink_next}->{n}} = $strp;
				$d{backlink_next}->{n}++;
			}
			my $prev = &sdat($comic)->{$strp}->{prev};
			if ($prev and !(&sdat($comic)->{$prev}->{next} eq $strp)) { #if next of prev is not self
				$d{backlink_prev}->{$d{backlink_prev}->{n}} = $strp;
				$d{backlink_prev}->{n}++;
			}
			
		}
		
		my $res = &kopf("Datalyzer");
		my $sec = param('section') ;
		
		if ($sec eq 'strps') {
			$res .= table(Tr([map {td([ #creating table with key : value pairs via map
					#if it is prev or next make it a link; else just print out the information
					$_,":",	($_ =~ m/prev|next/)	?	a({href=>"/tools?tool=datalyzer&comic=$comic&section=strps&strip=".$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					#make links klickable
					($_ =~ m/url/)	?	 a({href=>$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					$d{$sec}->{param('strip')}->{$_}
					])} grep {$_ ne 'n'} keys %{$d{$sec}->{param('strip')}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools?tool=datalyzer&comic=$comic"},"Back")
		}
		elsif ($sec) {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools?tool=datalyzer&comic=$comic&section=strps&strip=" . $d{$sec}->{$_}},$d{$sec}->{$_})
					])} grep {$_ ne 'n'} keys %{$d{$sec}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools?tool=datalyzer&comic=$comic"},"Back")
		}
		else {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools?tool=datalyzer&comic=$comic&section=$_"},$_) , ':' , $d{$_}->{n}
					])} grep {$_ ne 'strps'} keys %d]));	#getting all keys 
		}
		$res .= br . a({-href=>"/"},"Index") . end_html;
		return $res;
	}
	if ($tool eq "thumb") {
		&dat->{_CFG_}->{thumb} = param('height');
	}
}


sub load {
	my $comic = shift;
	return $sdat->{$comic} if $sdat->{$comic};
	print "$comic.dat wird geladen\n";
	$sdat->{$comic} = ReadINI("./data/$comic.dat",{'case'=>'preserve', 'sectionorder' => 1});
	return unless $sdat->{$comic}->{__SECTIONS__};
	my @strips = @{$sdat->{$comic}->{__SECTIONS__}};
	unless (dat->{$comic}->{first}) {
		my %double;
		my $first = $strips[0];
		while ($sdat->{$comic}->{$first}->{prev}) {
			if ($double{$first}) {
				print "loop bei $first gefunden, breche ab\n";
				last;
			}
			$double{$first} = 1;
			$first = $sdat->{$comic}->{$first}->{prev};
			print "zurueck: ". $comic."/".dat($comic)->{$first}->{'next'} ." -> " . $first . "\n";
		}	
		&dat->{$comic}->{first} = $first;
	}
	dat->{$comic}->{strip_count} = $#strips + 1;
	my $point = dat->{$comic}->{first};
	my $i = 1;
	my %double;
	while ($sdat->{$comic}->{$point}->{next}) {
		if ($double{$point}) {
			print "loop bei $point gefunden, breche ab\n";
			last;
		}
		else {
			$i++;
			#print "weiter: ". $comic."/".$point ." -> " . $sdat->{$comic}->{$point}->{next} . " $i<" . dat->{$comic}->{strip_count} . "\n";
			$point = $sdat->{$comic}->{$point}->{next};
			last if ($i > dat->{$comic}->{strip_count});
		}
	}
	dat->{$comic}->{last} = $point;
	dat->{$comic}->{strips_counted} = $i;
	return $sdat->{$comic};
}



sub load_dat {
	my $usr;
	return $srv if $srv;
	if (-e 'user.ini') {
		$usr = ReadINI('user.ini',{'case'=>'preserve', 'sectionorder' => 1});
	}
	if (-e 'server.ini') {
		$srv = ReadINI('server.ini',{'case'=>'preserve', 'sectionorder' => 1});
	}
	
	if (!$srv) {
		if ($usr) {
			$srv = $usr;
		}
		else {
			$srv = {}
		}
	}

	@{$srv->{__SECTIONS__}} = @{$usr->{__SECTIONS__}} if $usr and $srv;
	
	my $cfg = ReadINI('data/_CFG_',{'case'=>'preserve', 'sectionorder' => 1});
	
	foreach (@{$cfg->{__SECTIONS__}}) {
		$srv->{$_}->{first} = $cfg->{$_}->{first}
	}
	
	return $srv;
}

sub write_dat {
	WriteINI("server.ini", &dat);
}