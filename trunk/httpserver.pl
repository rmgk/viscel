#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:15 06.10.2008

use lib "./lib";

use HTTP::Daemon;
use HTTP::Status;
use Config::IniHash;
use CGI qw(:standard *table :html3);
use DBI;
use Data::Dumper;

use strict;

use vars qw($VERSION);
$VERSION = '2.22';

my $d = HTTP::Daemon->new(LocalPort => 80);

my $res = HTTP::Response->new( 200, 'erfolg', ['Content-Type','text/html; charset=iso-8859-1']);
my %index;
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
my %broken;

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

sub usr { #gibt die aktuellen einstellungen des comics aus # hier geh�ren die ver�nderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
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

sub dat { #gibt die dat und die dazugeh�rige configuration des comics aus # hier werden alle informationen zu dem comic und seinen srips gespeichert
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

sub tags {
	my $comic = shift;
	my $new = shift;
	if ($new) {
		my $tag_to_add = join(" ",split(/\W+/,$new));
		usr($comic,'tags',$tag_to_add);
	}
	if ($comic) {
		return ($dbh->selectrow_array("select tags from USER where comic='$comic'") =~ m/(\w+)/gs);
	}
	my $tags = $dbh->selectcol_arrayref("select tags from USER");
	my %taglist;
	foreach (@{$tags}) {
		foreach my $tag ($_ =~ m/(\w+)/gs) {
			$taglist{$tag} = 1;
		}
	}
	return sort {uc($a) cmp uc($b)} (keys %taglist);
}

sub flags {
	my $comic = shift;
	my $new = shift;
	my @flaglist = qw(read complete hiatus warn loop);
	
	if ($new =~ /^\+(\w+)/) {
		&flags($comic,&flags($comic) . " $1");
	}	
	elsif ($new =~ /^-(\w+)/) {
		my $f = &flags($comic);
		$f =~ s/$1//i;
		&flags($comic,$f);
	}
	elsif ($new) {
		my %flags;
		foreach (split(/\W+/,$new)) {
			$flags{$_} = 1;
		}
		my $fl;
		foreach (@flaglist) {
			$fl .= $flags{$_}?'1':'0';
		}
		usr($comic,'flags',$fl);
	}
	
	if ($comic) {
		my @ret;
		my @flag_codes = split(//,$dbh->selectrow_array("select flags from USER where comic='$comic'"));
		for (0..$#flag_codes) {
			push(@ret,$flaglist[$_]) if $flag_codes[$_]; 
		}
		return @ret;
	}
	return @flaglist;
	
}

sub kopf {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	
	my $c_bg 	= &config('color_bg') || 'black';
	my $c_text 	= &config('color_text') || '#009900';
	my $c_link 	= &config('color_link') || '#0050cc';
	my $c_vlink = &config('color_vlink') || '#900090';
	return start_html(-title=>$title. " - ComCol http $VERSION" ,-BGCOLOR=>$c_bg, -TEXT=>$c_text, -LINK=>$c_link, -VLINK=>$c_vlink,
							-head=>[Link({-rel=>'index',	-href=>"/"	})			,
                            $next ?	Link({-rel=>'next',		-href=>$next})	: undef	,
                            $prev ?	Link({-rel=>'previous',	-href=>$prev})	: undef	,
							$first?	Link({-rel=>'first',	-href=>$first})	: undef	,
							$last ?	Link({-rel=>'last',		-href=>$last})	: undef	,
							q(<style type="text/css">
<!--
a {text-decoration:none}
//-->
</style>)
									]);
}

sub preview_head {
	return q(
<div id="pre" style="visibility:hidden;position:fixed;right:0;bottom:0;"> </div>
<script type="text/javascript">
var preview = document.getElementById("pre");
function showImg (imgSrc) {
	preview.innerHTML = "<img src='"+imgSrc+"' alt='' />";
	preview.style.visibility='visible';
}
</script>
);
}

sub cindex {
	my $ret = &kopf("Index");
	$ret .= div({-style=>"right:0;width:150px;position:fixed;"},
		"Tools:" . br 
			.	a({-href=>"/tools?tool=config"},"Configuration") . br 
			.	a({-href=>"/tools?tool=user"},"User Config"). br 
			.	a({-href=>"/tools?tool=kategoriereihenfolge"},"change category order"). br 
			.	a({-href=>"/tools?tool=query"},"Custom Query"). br 
			.	a({-href=>"/tools?tool=random"},"Random Comic"). br 
			.	br .
		"Contents:" . br .
		join(" ",map { a({href=>"#$_"},$_) . br} &kategorie()) .
		a({href=>"#default"},'default') . br);	
	
	$ret .= &preview_head();
		
	foreach (kategorie) {
		$ret .= ("-"x 20).a({name=>$_},$_).("-"x 20).br;
		$ret .= html_comic_listing($dbh->selectcol_arrayref(qq(select comic from USER where kategorie="$_"))).br;
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
			a({-href=>"/front?comic=$comic"},$comic) ,
			$usr->{'first'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'first'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'first'}."')"},"|&lt;&lt;") : undef ,
			$usr->{'aktuell'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'aktuell'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'aktuell'}."')"},"&gt;&gt;") : undef ,
			$usr->{'bookmark'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'bookmark'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'bookmark'}."')"},"||") : undef ,
			$usr->{'aktuell'} eq $usr->{'last'} ? "&gt;&gt;|" : $usr->{'last'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'last'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'last'}."')"},"&gt;&gt;|") : undef ,
			#$usr->{'strip_count'},$usr->{'strips_counted'},
			#a({href=>"/tools?tool=cataflag&comic=$comic"},'categorize'),
			#a({href=>"/tools?tool=datalyzer&comic=$comic"},'datalyzer'),
			#a({href=>"/tools?tool=user&comic=$comic"},'user'),
			#$broken{$comic} ? "broken" : undef , flags($comic)
			])
		]);
		$count += $usr->{'strip_count'};
		$counted += $usr->{'strips_counted'};
	}
	
	#$ret .=  Tr([td([undef,undef,undef,undef,undef,undef,$count,$counted])]) . end_table ;
	return $ret . end_table;
}

sub cfront {
	my $comic = param('comic') // shift;
	my $random = shift;
	my $ret = &kopf($comic . " Frontpage",0,0,
					&usr($comic,'first') ?"/comics?comic=$comic&strip=".&usr($comic,'first') :"0",
					&usr($comic,'last' ) ?"/comics?comic=$comic&strip=".&usr($comic,'last' ) :"0",
					);
	$ret .= div({-align=>"center"},
				&usr($comic,'aktuell')?(
				a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first')},img({-style=>'width:33%',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"})) ,
				a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'aktuell')},img({-id=>'aktuell',-style=>'width:33%',-src=>"/strips/$comic/".&usr($comic,'aktuell'),-alt=>"current"}))  ,
				a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last')},img({-style=>'width:33%',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"}))
				):(
				a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first')},img({-style=>'width:49%',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"})) ,
				a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last')},img({-style=>'width:49%',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"}))
				)
				,
				br,br,br,
				&usr($comic,'bookmark')?a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'bookmark'),
				-onmouseover=>"document.aktuell.src='/strips/$comic/".&usr($comic,'bookmark')."'",
				-onmouseout =>"document.aktuell.src='/strips/$comic/".&usr($comic,'aktuell')."'"
				},'bookmark') . br : undef,
				a({-href=>"/"},"Index"),
				a({-href=>"/comics?comic=$comic"},"Striplist"),
				a({href=>"/tools?tool=cataflag&comic=$comic"},'Categorize'),
				br,
				usr($comic,'strip_count'),usr($comic,'strips_counted'),
				a({href=>"/tools?tool=datalyzer&comic=$comic"},'datalyzer'),
				a({href=>"/tools?tool=user&comic=$comic"},'user'),
				$broken{$comic} ? "broken" : undef ,br,
				"flags: " ,flags($comic), "tags: " ,tags($comic) ,
				$random? br. a({-href=>"/tools?tool=random"},"Random"):undef
			);
	
	return $ret . end_html;
}


sub ccomic {
	my $comic = param('comic') // shift;
	my $strip = param('strip') // shift;
	my $random = shift;
	my $ret = &kopf("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	if ($strip) {
		my $return;
		my $title = &dat($comic,$strip,'title');
		$title =~ s/-�-//g;
		$title =~ s/!�!/|/g;
		$title =~ s/~�~/~/g;
		
		$strip =~ s/%7C/|/ig;
		$strip =~ s/ /%20/ig;
		
		$return = &kopf($title,
					&dat($comic,$strip,'prev')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev')	:"0",
					&dat($comic,$strip,'next')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'next')	:"0",
					&usr($comic,'first')		?"/comics?comic=$comic&strip=".&usr($comic,'first')			:"0",
					&usr($comic,'last')			?"/comics?comic=$comic&strip=".&usr($comic,'last')			:"0",
					);
					
		$return .= div({-align=>"center"},
				(-e "./strips/$comic/$strip") ? img({-src=>"/strips/$comic/$strip",-alt=>"$strip"}) :
				&dat($comic,$strip,'surl')!~m/^dummy/ ? img({-src=>&dat($comic,$strip,'surl')}) . br . 
				a({-href=>"/tools?tool=download&comic=$comic&strip=$strip"},"(download)")	:
				"Diese Seite ist nur ein Dummy. Es k�nnte sein, dass der strip nicht korrekt heruntergeladen werden konnte."
				,br,br,
				&dat($comic,$strip,'prev')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev')},"back"):undef,
				&dat($comic,$strip,'next')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'next')},"next"):undef,br,
				a({-href=>"/"},"Index"),
				a({-href=>"/front?comic=$comic"},"Frontpage"),
				&dat($comic,$strip,'url')?a({-href=>&dat($comic,$strip,'url')},"Site"):undef,br, 
				a({-href=>"/comics?comic=$comic&strip=$strip&bookmark=1"},"Bookmark"),
				a({href=>"/tools?tool=cataflag&comic=$comic"},'categorize')
				);
		&usr($comic,'aktuell',$strip) unless $random;
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
				$title =~ s/-�-//g;
				$title =~ s/!�!/|/g;
				$title =~ s/~�~/~/g;
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
			return &kopf($comic . " Kategorie ge�ndert") ."$comic category changed to $kategorie " . a({-href=>"/"},"Back") . br . br . &cindex();
		}
		my $res = &kopf($comic." Kategorie �ndern");
		
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
	if ($tool eq "cataflag") {
		my $kategorie = param('new_cat') || param('kategorie');
		my $tags =  join(" ",param('tags')) ." ". param('new_tag');
		my $flags = join(" ",param('flags'));
		if ($kategorie) {
			usr($comic,'kategorie',$kategorie);
			#return &kopf($comic . " changed category") ."$comic category changed to $kategorie " . a({-href=>"/"},"Back") . br . br . &cindex();
		}
		if ($tags) {
			tags($comic,$tags);
			#return &kopf($comic . " changed category") ."$comic category changed to $kategorie " . a({-href=>"/"},"Back") . br . br . &cindex();
		}
		if ($flags) {
			flags($comic,$flags);
			#return &kopf($comic . " changed category") ."$comic category changed to $kategorie " . a({-href=>"/"},"Back") . br . br . &cindex();
		}
		&usr($comic,'tags',0,'delete') if (param('delete_tags'));
		&usr($comic,'flags',0,'delete') if (param('delete_flags'));
		my $res = &kopf($comic." category tags and flags");
		
		$res .= h1('Category');
		$res .= start_form("GET","tools");
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"cataflag");
		$res .= radio_group(-name=>'kategorie',
	                             -values=>[&kategorie],
	                             -default=>usr($comic,'kategorie'),
	                             -linebreak=>'true');
		$res .= "new: " . textfield(-name=>'new_cat');
		$res .= br . submit('ok');
		$res .= end_form;
		
		$res .= h1('Tags');
		$res .= start_form("GET","tools");
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"cataflag");
		$res .= checkbox_group(-name=>'tags',
	                             -values=>[&tags],
								 -default=>[&tags($comic)],
	                             -linebreak=>'true');
		$res .= "new: " . textfield(-name=>'new_tag');
		$res .= br . checkbox(-name=>'delete_tags',
				   -checked=>0,
				   -value=>'1',
				   -label=>'delete all tags');
		$res .= br . submit('ok');
		$res .= end_form;
		
		$res .= h1('Flags');
		$res .= start_form("GET","tools");
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"cataflag");
		$res .= checkbox_group(-name=>'flags',
	                             -values=>[&flags],
								 -default=>[&flags($comic)],
	                             -linebreak=>'true');
		$res .= br . checkbox(-name=>'delete_flags',
                           -checked=>0,
                           -value=>'1',
                           -label=>'delete all flags');
		$res .= br . submit('ok');
		
		$res .= br .br .br. a({-href=>"/tools?comic=$comic&tool=cataflag"},"reload") .br. a({-href=>"/"},"Index");
		$res .= end_form;
		$res .= end_html;;
		return $res;	
	}	
	if ($tool eq "kategoriereihenfolge") {
		my $res = &kopf("Kategoriereihenfolge �ndern");
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
	if ($tool eq 'forceupdate') {
		if ($comic) {
			my $time = time;
			$dbh->do("UPDATE USER set server_update = NULL where comic like '$comic'");
			&update;
			return &kopf('Force Update All') . "Time: " . (time - $time) . " Seconds" . end_html;
		}
		my $time = time;
		$dbh->do("UPDATE USER set server_update = NULL");
		&update;
		return &kopf('Force Update All') . "Time: " . (time - $time) . " Seconds" . end_html;
	}
	if ($tool eq 'random') {
		my $firsts = $dbh->selectall_hashref('SELECT comic,first FROM user where flags like "0%" OR flags IS NULL' , 'comic');
		my @comics = keys %{$firsts};
		while($comic = $comics[rand(int @comics)]) {
			next if $broken{$comic};
			return cfront($comic,1);
		}
	}
}


sub update {
	$dbh->do("UPDATE USER set first = NULL , server_update = NULL where first like 'dummy|%'");
	
	# foreach my $comic (@{$dbh->selectcol_arrayref(qq(select comic from USER where first IS NULL))}) {
		# my @first = @{$dbh->selectcol_arrayref("select strip from _$comic where prev IS NULL and next IS NOT NULL")};
		# next if (@first == 0); 
		# @first = grep {$_ !~ /^dummy/} @first if (@first > 1);
		# usr($comic,'first',$first[0]);
	# }
	my @comics = @{$dbh->selectcol_arrayref(qq(select comic,server_update - last_save as time from USER where (time <= 0) OR (server_update IS NULL)))};
	local $| = 1;
	print "updating ". scalar(@comics) ." comics" if @comics;
	foreach my $comic (@comics){
		usr($comic,'server_update',time);
		
		my @dummys = $dbh->selectrow_array("select strip from _$comic where (strip like 'dummy|%') and ((prev IS NULL) or (next IS NULL))");
		$dbh->do("DELETE FROM _$comic where (strip like 'dummy|%') and ((prev IS NULL) or (next IS NULL))");
		foreach my $dummy (@dummys) {
			$dbh->do("update _$comic set next = NULL where next = '$dummy'");
			$dbh->do("update _$comic set prev = NULL where prev = '$dummy'");
		}
		
		if ($dbh->selectrow_array("SELECT COUNT(next) AS dup_count FROM _$comic GROUP BY next HAVING (COUNT(next) > 1)")
		or  $dbh->selectrow_array("SELECT COUNT(prev) AS dup_count FROM _$comic GROUP BY prev HAVING (COUNT(prev) > 1)")) {
			&flags($comic,'+warn');
		}
		else {
			&flags($comic,'-warn');
		}
		
		my $first = usr($comic,'first');
		unless ($first) {
			my @first = @{$dbh->selectcol_arrayref("select strip from _$comic where prev IS NULL and next IS NOT NULL")};
			next if (@first == 0); 
			@first = grep {$_ !~ /^dummy/} @first if (@first > 1);
			usr($comic,'first',$first[0]);
			$first = $first[0];
		}
		
		usr($comic,'strip_count',$dbh->selectrow_array(qq(select count(*) from _$comic)));
		
		my %double;
		my $strp = $first;
		my $strps = {};
		$strps = $dbh->selectall_hashref(qq(select strip , next from _$comic), "strip");
		
		my $i = 0;
		if ($strp) {
			$i++ ;
			while($strps->{$strps->{$strp}->{next}}) {
				$strp = $strps->{$strp}->{next};
				if ($double{$strp} == 1) {
					last;
				}
				else {
					$double{$strp} = 1;
				}
				$i++;
			}
		}
		usr($comic,'last',$strp);
		usr($comic,'strips_counted',$i);
		print ".";
	}
	my $comini = ReadINI('comic.ini',{'case'=>'preserve'});
	foreach my $name (keys %{$comini}) {
		if ($comini->{$name}->{broken}) {
			$broken{$name} = 1;
		}
	}
	print "\n" if @comics;
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
				elsif ($r->url->path =~ m#^/front$#) {
					$res->content(&cfront);
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