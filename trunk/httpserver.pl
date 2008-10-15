#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:15 06.10.2008

use lib "./lib";

use HTTP::Daemon;
use HTTP::Status;
use Config::IniHash;
use CGI qw(:standard *table);
use DBI;
use Data::Dumper;

use strict;

use vars qw($VERSION);
$VERSION = '2.15';

my $d = HTTP::Daemon->new(LocalPort => 80);

my $res = HTTP::Response->new( 200, 'erfolg', ['Content-Type','text/html; charset=iso-8859-1']);
my %index;
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});


sub comics {
	#my $comics = ReadINI('comic.ini',{'case'=>'preserve', 'sectionorder' => 1});
	#return @{$comics->{__SECTIONS__}};
	return @{$dbh->selectcol_arrayref("select comic from USER")};
}

sub config {
	my ($key,$value,$null) = @_;
	if ($null) {
		$dbh->do(qq(update CONFIG set $key = NULL));
	}
	elsif (defined $value and $value ne '') {
		if ($dbh->do(qq(update CONFIG set $key = "$value"))< 1) {
			$dbh->do(qq(insert into CONFIG ($key) VALUES ("$value")));
		}
	}
	return $dbh->selectrow_array(qq(select $key from CONFIG));
}

sub usr { #gibt die aktuellen einstellungen des comics aus # hier gehören die veränderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
	my ($c,$key,$value,$null) = @_;
	if ($null) {
			$dbh->do(qq(update USER set $key = NULL where comic="$c"));
	}
	elsif (defined $value and $value ne '') {
		if ($dbh->do(qq(update USER set $key = "$value" where comic="$c")) < 1) { #try to update
			$dbh->do(qq(insert into USER (comic,$key) VALUES ("$c","$value"))); #insert if update fails
		}
	}
	return $dbh->selectrow_array(qq(select $key from USER where comic="$c"));
}

sub dat { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu dem comic und seinen srips gespeichert
	my ($c,$strip,$key,$value,$null) = @_;
	if ($null) {
			$dbh->do(qq(update _$c set $key = NULL where strip="$strip"));
	}
	elsif (defined $value and $value ne '') {
		if ($dbh->do(qq(update _$c set $key = "$value" where strip="$strip")) < 1) { #try to update
			$dbh->do(qq(insert into _$c  (strip,$key) values ("$strip","$value"))); #insert if update fails
		}
	}
	return $dbh->selectrow_array(qq(select $key from _$c where strip="$strip"));
}

sub kategorie {
	my $kat = shift;
	my $ord = shift;
	if ($ord) {
		config('kat_order',$ord);
		return split(',',config('kat_order'));
	}
	config('kat_order',config('kat_order') .','.$kat) if $kat;
	
	my @kat = split(',',config('kat_order')); #sortierte kategorien
	my %d;
	$d{$_} = 1 for (@kat); 
	my @kat2 = @{$dbh->selectcol_arrayref(qq(select distinct kategorie from USER))}; #alle vorhandenen kategorien
	for (@kat2) {
		push(@kat,$_) unless ($d{$_} or !$_);
	}
	return @kat;
}

sub kopf {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	
	my $c_bg 	= &config('color_bg') || 'black';
	my $c_text 	= &config('color_text') || '009900';
	my $c_link 	= &config('color_link') || '0050cc';
	my $c_vlink = &config('color_vlink') || '900090';
	return start_html(-title=>$title. " - ComCol http $VERSION" ,-BGCOLOR=>$c_bg, -TEXT=>$c_text, -LINK=>$c_link, -VLINK=>$c_vlink,
							-head=>[Link({-rel=>'index',	-href=>"/"	})			,
                            $next ?	Link({-rel=>'next',		-href=>$next})	: undef	,
                            $prev ?	Link({-rel=>'previous',	-href=>$prev})	: undef	,
							$first?	Link({-rel=>'first',	-href=>$first})	: undef	,
							$last ?	Link({-rel=>'last',		-href=>$last})	: undef	,]);
}


sub cindex {
	my $ret = &kopf("Index");
	$ret .= "Tools:" . br;
	$ret 	.=	a({-href=>"/tools?tool=config"},"Configuration") . br 
			.	a({-href=>"/tools?tool=user"},"User Config"). br 
			.	a({-href=>"/tools?tool=kategoriereihenfolge"},"Kategoriereihenfolge ändern"). br 
			.	a({-href=>"/tools?tool=query"},"Custom Query"). br 
			.	br;
	
	
	$ret .= "Inhalt:" . br;
	foreach (kategorie) {
		$ret .= a({href=>"#$_"},$_) . br;
	}
	$ret .= a({href=>"#default"},'default') . br;
	foreach (kategorie) {
		$ret .= ("-"x 20).a({name=>$_},$_).("-"x 20).br;
		$ret .= html_comic_listing($dbh->selectcol_arrayref(qq(select comic from USER where kategorie="$_")));
	}
	$ret .= ("-"x 20).a({name=>'default'},'default').("-"x 20).br;
	$ret .= html_comic_listing($dbh->selectcol_arrayref(qq(select comic from USER where kategorie IS NULL)));
	return $ret . end_html;
}

sub html_comic_listing {
	my $comics = shift;
	my $ret = start_table;
	my $count;
	my $counted;
	foreach my $comic (@{$comics}) {
		my $usr = $dbh->selectrow_hashref(qq(select * from USER where comic="$comic"));
		$ret .= Tr([
			td([
			a({-href=>"/comics?comic=$comic"},$comic) ,
			$usr->{'first'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'first'}},"start") : undef ,
			$usr->{'aktuell'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'aktuell'}},"current") : undef ,
			$usr->{'bookmark'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'bookmark'}},"bookmark") : undef ,
			$usr->{'aktuell'} eq $usr->{'last'} ? "end" : $usr->{'last'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'last'}},"end") : undef ,
			a({href=>"/tools?tool=kategorie&comic=$comic"},'category'),$usr->{'strip_count'},$usr->{'strips_counted'} ,
			a({href=>"/tools?tool=datalyzer&comic=$comic"},'datalyzer')
			])
		]);
		$count += $usr->{'strip_count'};
		$counted += $usr->{'strips_counted'};
	}
	
		$ret .=  Tr([td([undef,undef,undef,undef,undef,undef,$count,$counted])]) . end_table ;
}


sub ccomic {
	my $comic = param('comic');
	my $strip = param('strip');
	my $ret = &kopf("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	if ($strip) {
		my $return;
		my $title = &dat($comic,$strip,'title');
		$title =~ s/-§-//g;
		$title =~ s/!§!/|/g;
		$title =~ s/~§~/~/g;
		
		$strip =~ s/%7C/|/ig;
		$return = &kopf($title,
					&dat($comic,$strip,'prev')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev')	:"0",
					&dat($comic,$strip,'next')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'next')	:"0",
					&usr($comic,'first')		?"/comics?comic=$comic&strip=".&usr($comic,'first')			:"0",
					&usr($comic,'last')			?"/comics?comic=$comic&strip=".&usr($comic,'last')			:"0",
					);
					
		$return .= div({-align=>"center"},
				(-e "./strips/$comic/$strip") ? img({-src=>"/strips/$comic/$strip"}) :
				&dat($comic,$strip,'surl')!~m/^dummy/ ? img({-src=>&dat($comic,$strip,'surl')}) . br . 
				a({-href=>"/tools?tool=download&comic=$comic&strip=$strip"},"(download)")	:
				"Diese Seite ist nur ein Dummy. Es könnte sein, dass der strip nicht korrekt heruntergeladen werden konnte."
				,br,br,
				&dat($comic,$strip,'prev')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev')},"zurück"):undef,
				&dat($comic,$strip,'next')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'next')},"weiter"):undef,br,
				a({-href=>"/"},"Index"),
				&dat($comic,$strip,'url')?a({-href=>&dat($comic,$strip,'url')},"Site"):undef,
				a({-href=>"/comics?comic=$comic"},$comic),br, 
				a({-href=>"/comics?comic=$comic&strip=$strip&bookmark=1"},"Bookmark"),
				a({href=>"/tools?tool=kategorie&comic=$comic"},'Kategorie')
				);
		&usr($comic,'aktuell',$strip);
		&usr($comic,'bookmark',$strip) if param('bookmark');
		return $return . end_html;
	}
	else {
		unless ($index{$comic}) {
			my %double;
			print "erstelle index ...\n";
			
			my $dat = $dbh->selectall_hashref(qq(select strip,next,title from _$comic),"strip");
			
			my $strip = &usr($comic,'first');
			$index{$comic} = &kopf($comic);
			
			my $i;
			while ($dat->{$strip}->{'strip'}) {
				if ($double{$strip}) {
					print "loop gefunden, breche ab\n" ;
					last;
				}
				$double{$strip} = 1;

				$i++;
				my $title = $dat->{$strip}->{'title'};
				$title =~ s/-§-//g;
				$title =~ s/!§!/|/g;
				$title =~ s/~§~/~/g;
				$index{$comic} .= a({href=>"./comics?comic=$comic&strip=$strip"},"$i : $strip : $title") .br;#. (&config('thumb')?img({-height=>&config('thumb'), -src=>"/strips/$comic/$strip"}):undef) . br;
				if ($strip eq $dat->{$strip}->{'next'}) {
					print "selbstreferenz gefunden, breche ab\n" ;
					last;
				}
				else {
					print "weiter: " .$strip." -> " . $dat->{$strip}->{'next'} . "\n";
					$strip = $dat->{$strip}->{'next'};
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
		my $kategorie = param('neu') || param('kategorie') ;
		if ($kategorie) {
			usr($comic,'kategorie',$kategorie);
			return &kopf($comic . " Kategorie geändert") ."$comic category changed to $kategorie " . a({-href=>"/"},"Back") . br . br . &cindex();
		}
		my $res = &kopf($comic." Kategorie ändern");
		
		$res .= start_form("GET","tools");
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"kategorie");
		$res .= radio_group(-name=>'kategorie',
	                             -values=>[&kategorie],
	                             -default=>usr($comic,'kategorie'),
	                             -linebreak=>'true');
		$res .= "neu: " . textfield(-name=>'neu');
		$res .= br . submit('ok');
		$res .= br . a({-href=>"/"},"Index");
		$res .= end_form;
		$res .= end_html;;
		return $res;	
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
		&dlutil::getstore(&dat($comic,$strip,'surl'),"./strips/$comic/$strip");
		return &ccomic;
	}
	if ($tool eq "datalyzer") {
		my %d;
		my $strips = $dbh->selectall_hashref("select * from _$comic","strip");
		foreach my $strp (keys %{$strips}) {
			$d{count}->{$d{count}->{n}} = $strp;
			$d{count}->{n}++;
			$d{strps}->{$strp} = \%{$strips->{$strp}};
			if ($strp =~ m/^dummy/) {
				$d{dummy}->{$d{dummy}->{n}} = $strp;
				$d{dummy}->{n}++ ;
			}
			if ($strips->{$strp}->{prev} and $strips->{$strp}->{next}) {  #hat prev und next
				$d{prevnext}->{$d{prevnext}->{n}} = $strp;
				$d{prevnext}->{n}++ ;
			} elsif ($strips->{$strp}->{prev}) { #hat nur prev
				$d{prev}->{$d{prev}->{n}} = $strp;
				$d{prev}->{n}++;
			} elsif ($strips->{$strp}->{next}) { #hat nur next
				$d{next}->{$d{next}->{n}} = $strp;
				$d{next}->{n}++;
			} else {									# hat keines von beiden
				$d{none}->{$d{none}->{n}} = $strp;
				$d{none}->{n}++;
			}
			
			my $next = $strips->{$strp}->{next};
			if ($next and !($strips->{$next}->{prev} eq $strp)) { #if prev of next is not self
				$d{backlink_next}->{$d{backlink_next}->{n}} = $strp;
				$d{backlink_next}->{n}++;
			}
			my $prev = $strips->{$strp}->{prev};
			if ($prev and !($strips->{$prev}->{next} eq $strp)) { #if next of prev is not self
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
		return $res .= br . a({-href=>"/"},"Index") . end_html;
	}
	if ($tool eq "thumb") {
		&config('thumb',param('height'));
	}
	if ($tool eq "colorizer") {
		my $res = &kopf("Change Colors");
		if (defined param('color_bg')) {
			&config('color_bg',param('color_bg'));
		}
		if (defined param('color_text')) {
			&config('color_text',param('color_text'));
		}
		if (defined param('color_link')) {
			&config('color_link',param('color_link'));
		}
		if (defined param('color_vlink')) {
			&config('color_vlink',param('color_vlink'));
		}
		$res .= start_form("GET","tools");
		$res .= hidden('tool',"colorizer");
		$res .= "Background: " . textfield(-name=>'color_bg', -default=>&config('color_bg')) . br;
		$res .= "Text: " . textfield(-name=>'color_text', -default=>&config('color_text')) . br;;
		$res .= "Link: " . textfield(-name=>'color_link', -default=>&config('color_link')) . br;;
		$res .= "vlink: " . textfield(-name=>'color_vlink', -default=>&config('color_vlink')) . br;;
		$res .= submit('ok');
		$res .= br . br .  a({-href=>"/"},"Index");
		return $res . end_html;
	}
	if ($tool eq 'config') {
		my $config = $dbh->selectrow_hashref("select * from CONFIG");
		my $res = &kopf('Config');
		$res .= start_form("GET","tools");
		$res .= hidden('tool',"config");
		$res .= start_table;
		if (param('delete') ne '') {
			&config(param('delete'),0,'delete');
		}
		foreach my $key (keys %{$config}) {
			if (param($key) ne '') {
				&config($key,param($key));
			}
			$res .=  Tr(td("$key"),td(textfield(-name=>$key, -default=>&config($key), -size=>"100")),td(a({-href=>"/tools?tool=config&delete=$key"},"delete $key")));
		}
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools?tool=config"},"reload") .br. a({-href=>"/"},"Index") . end_html;
	}
	if ($tool eq 'user') {
		my $user = $dbh->selectall_hashref("select * from USER","comic");
		my $res = &kopf('user');
		if (param('comic')) {
			$res .= start_form("GET","tools");
			$res .= hidden('tool',"user");
			$res .= start_table;
			if (param('delete') ne '') {
				&usr(param('comic'),param('delete'),0,'delete');
			}
			foreach my $key (keys %{$user->{param('comic')}}) {
				if (param($key)) {
					&usr(param('comic'),$key,param($key));
				}
				$res .=  Tr(td("$key"),td(textfield(-name=>$key, -default=>&usr(param('comic'),$key), -size=>"100")),td(a({-href=>"/tools?tool=user&delete=$key&comic=" . param('comic')},"delete $key")));
			}
			return $res . end_table . submit('ok'). br . br .a({-href=>"/tools?tool=user&comic=".param('comic')},"reload") .br. a({-href=>"/tools?tool=user"},"Back") . br . a({-href=>"/"},"Index") . end_html;
		}
		$res .= start_table;
		my $h = 0;
		foreach my $comic (sort{uc($a) cmp uc($b)} (keys %{$user})) {

			$res .=  Tr(td('name'),td([keys %{$user->{$comic}}])) if !$h;
			$h = 1;
			$res .=  Tr(td(a({-href=>"/tools?tool=user&comic=". $comic},$comic)),td([map {textfield(-name=>$_, -default=>&usr($comic,$_))} keys %{$user->{$comic}}]));
		}
		return $res . end_table . br . br .  a({-href=>"/"},"Index") . end_html;
	}
	if ($tool eq 'query') {
		my $res = &kopf('Query');
		if (param('query')) {
			if (param('hashkey')) {
				$res .= pre(Dumper($dbh->selectall_hashref(param('query'),param('hashkey'))));
			}
			else {
				$res .= pre(Dumper($dbh->selectall_arrayref(param('query'))));
			}
			return $res . br . br . a({-href=>"/tools?tool=query"},"Back") . br .  a({-href=>"/"},"Index") . end_html;
		}
		$res .= start_form("GET","tools");
		$res .= hidden('tool',"query");
		$res .= textfield(-name=>"query", -size=>"200") . br;
		$res .= textfield(-name=>"hashkey", -size=>"20");
		$res .= br . submit('ok');
		return $res . br . br .  a({-href=>"/"},"Index") . end_html;
	}
}


sub update {
	foreach my $comic (@{$dbh->selectcol_arrayref(qq(select comic from USER where first IS NULL))}) {
		my @first = @{$dbh->selectcol_arrayref("select strip from _$comic where prev IS NULL and next IS NOT NULL")};
		next if (@first == 0); 
		@first = grep {$_ !~ /^dummy/} @first if (@first > 1);
		usr($comic,'first',$first[0]);
	}

	foreach my $comic (@{$dbh->selectcol_arrayref(qq(select comic,server_update - last_save as time from USER where (time <= 0) OR (server_update IS NULL)))}){
		usr($comic,'server_update',time);
		
		usr($comic,'strip_count',$dbh->selectrow_array(qq(select count(*) from _$comic)));
		
		
		my %double;
		my $strp = usr($comic,'first');

		my $strps = {};
		$strps = $dbh->selectall_hashref(qq(select strip , next from _$comic), "strip");
		
		my $i = 1;
		while($strps->{$strp}->{next}) {
			$strp = $strps->{$strp}->{next};
			if ($double{$strp} == 1) {
				last;
			}
			else {
				$double{$strp} = 1;
			}
			$i++;
		}
		usr($comic,'last',$strp);
		usr($comic,'strips_counted',$i);
	}
}

&update;

#print "Please contact me at: <URL:", $d->url,">\n";
print "Please contact me at: <URL:", "http://127.0.0.1/" ,">\n";
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
		#$dbh->commit;
	}
	undef($c);
}