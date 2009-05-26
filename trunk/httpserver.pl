#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009

use 5.010;
use strict;
use warnings;
use lib "./lib";

=head1 NAME

comcol httpserver - you just don't want to use some other viewer

=head1 DESCRIPTION

httpserver is the tool to view the strips you have downloaded with comcol.

it will update the database when it starts, so it is a good idea to restart it regularly 
(especially after you downloaded new comics/strips).
while you can run it at the sime time while comcol is running, 
there may be errors or delays because both are using the same database.

=cut

use HTTP::Daemon;
use HTTP::Status;
use CGI qw(:standard *table :html3 *div gradient);
use DBI;
use Data::Dumper; #this is for pretty printing the custom query output
use Time::HiRes; #requests of about 0.005 seconds can't be measured in full seconds
use dbutil;


use vars qw($VERSION);
$VERSION = '2.46';

my $d = HTTP::Daemon->new(LocalPort => 80);
die "could not listen on port 80 - someones listening there already?" unless $d;

my $res = HTTP::Response->new( 200, 'success', ['Content-Type','text/html; charset=iso-8859-1']); #our main response
my $rescss = HTTP::Response->new( 200, 'success', ['Content-Type','text/css; charset=iso-8859-1']); #response for style
my %index;
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout'); #we dont want to timeout (timeout happens if comic3.pl and httpserver.pl are run at the same time)
my %broken; #we save all the comics marked as broken in comic.ini here
my %rand_seen; #this is for remembering which comics we already selected randomly
my @db_cache = ('','','',''); #i often need to get a value multiple times, so we safe the last value for each request as an element of this array
my $measure_time = $ARGV[0]; #set this to one to get some info on request time
my $css; #we save our style sheet in here


#if we are processing something, we dont want to update!
if ($dbh->selectrow_array(qq(select processing from CONFIG where processing is not null and processing not like "")))  {
	say "skipping update while comics are processed...";
}
else {
	&update;
}


=head1 Usage

when you run httpserver.pl it will update the database and listen for you on port 80.
you can connect with any webbrowser and start reading some comics.

=cut

print "Please contact me at: <URL:", "http://127.0.0.1/" ,">\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if ($r->method eq 'GET') {
			my $req_start_time = Time::HiRes::time if $measure_time; 
			if ($r->url->path eq '/favicon.ico') {
				$c->send_file_response("./favicon.ico");
			}
			elsif ($r->url->path =~ m#^/strips/(.*?)/(.*)$#) {
				$c->send_file_response("./strips/$1/$2");
			}
			elsif ($r->url->path eq '/style.css') {
					$rescss->content(&ccss);
					$c->send_response($rescss);
			}
			else {
				restore_parameters($r->url->query);

				if ($r->url->path eq '/comics') {
					$res->content(&ccomic);
				}
				elsif ($r->url->path eq '/front') {
					$res->content(&cfront);
				}
				elsif ($r->url->path eq '/tools') {
					$res->content(&ctools);
				}
				elsif ($r->url->path eq '/pod') {
					$res->content(&cpod);
				}
				else {
					$res->content(&cindex);
				}
				$c->send_response($res);
			}
			say $r->url->path . '?' . ($r->url->query // '') . " " . (Time::HiRes::time - $req_start_time) if $measure_time;
			$measure_time = param('measure_time') if defined param('measure_time');
		}
		$c->close;
		#$dbh->commit;
	}
	undef($c);
}



sub kopf {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	my $prefetch = shift;
	my $javascript = shift;
	
	return start_html(	-title=>$title. " - ComCol ht $VERSION" ,
						-style=>"/style.css",
						-head=>[
									#Link({-rel=>"stylesheet", -type=>"text/css", -href=>}),
									Link({-rel=>'index',	-href=>"/"	})			,
									Link({-rel=>'help',		-href=>"/pod"})			,
							$next ?	Link({-rel=>'next',		-href=>$next})	: undef	,
							$prev ?	Link({-rel=>'previous',	-href=>$prev})	: undef	,
							$first?	Link({-rel=>'first',	-href=>$first})	: undef	,
							$last ?	Link({-rel=>'last',		-href=>$last})	: undef	,
						$prefetch ?	Link({-rel=>'prefetch',	-href=>$prefetch})	: undef	,
								],
							$javascript ? ("-onload"=>"$javascript") : undef, 
						);
}

sub preview_head { #some javascript to have a nice preview of image links your hovering
	my $js =  q(
<div id="prevbox"> </div>
<script type="text/javascript">
var preview = document.getElementById("prevbox");
function showImg (imgSrc) {
	preview.innerHTML = "<img id='prevIMG' src='"+imgSrc+"' alt='' />";
	preview.style.visibility='visible';
}
function hideIMG () {
	preview.style.visibility='hidden';
	preview.innerHTML = "";
}
</script>
);
$js =~ s/\s+/ /gs;
return "\n$js\n";

}


=head2 Index

connecting to the server will bring you to this page.
here you can click on a comic name to go to the comics L<frontpage|/"Frontpage">.

the four (you can only see those actually linking to something) links next to the comic name 
will bring you to the I<first>, I<current>, I<bookmarked> and I<last> strip.

the comics are divided in 4 main L<filters|/"Filter"> you can access them via the content links and you can also create your own.
inside each filter, the comics are sortet by I<strips to read> 
that is the number of strips you have not yet read (countet from your bookmark to the last strip).
the colors give some information about how many strips you have to read (more red more strips, see color scale)

you can use the L<tag|/"Cataflag"> links to fast filter by tag and finally you have links to some tools.

Tools include:

=over

=item * L</"Custom Query">

=item * L<"Custom Contents"|/"Filter">

=item * L</"Random">

=back

=cut


sub cindex {
	my $ret = &kopf("Index");
	my @tag = param('tag');
	$ret .= start_div({-id=>"menu"});
	$ret .=	"Tools:" . br 
				#.	a({-href=>"/tools?tool=config",-accesskey=>'c',-title=>'config'},"Configuration") . br 
				#.	a({-href=>"/tools?tool=user",-accesskey=>'u',-title=>'user'},"User Config"). br 
				.	a({-href=>"/tools?tool=query",-accesskey=>'q',-title=>'query'},"Custom Query"). br 
				.	a({-href=>"/tools?tool=filter",-accesskey=>'f',-title=>'filter'},"Custom Contents"). br 
				.	a({-href=>"/tools?tool=random",-accesskey=>'r',-title=>'random'},"Random Comic"). br ;
				
	my $i = 1;
	$ret .=	br . "Contents:" . br .
				join("",map { a({href=>"#$_",-accesskey=>$i++,-title=>$_},$_) . br} (qw(continue other finished stopped),&filter));
				 
	$ret .=	br . "Tags:" . br .
				 a({-href=>"/"},'Any Tag') . br .
				 join("",map {a({-href=>"/?tag=$_".join("",map {"&tag=$_"} (@tag)) },$_) . br} (tags()));	
				 
	$ret .=	br . "Color Scale:" .br.
				 join("",map {div({-style=>"color:#".colorGradient(log($_),10)},$_ )} (10000,5000,2000,1000,500,200,100,50,10,1)) . br;
		 
	$ret .= end_div(); 
	
	$ret .= &preview_head();
	
	my $cmcs = $dbh->selectall_hashref("select * from user",'comic');
	my $tagcheck = '1';
	$tagcheck = join(' and ',map {"tags like '%$_%'"} (@tag)) if @tag;
	$ret .= html_comic_listing('continue',$cmcs,qq{flags like '%r%' and flags not like '%f%' and flags not like '%s%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('other',$cmcs,qq{((flags not like '%r%' and flags not like '%f%' and flags not like '%s%') or flags is null) and ($tagcheck) }).br;
	$ret .= html_comic_listing('finished',$cmcs,qq{flags like '%f%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('stopped',$cmcs,qq{flags like '%s%' and ($tagcheck)}).br;
	foreach (&filter) {
		$ret .= html_comic_listing($_,$cmcs, '(' . filter($_) .") and ($tagcheck)").br;
	}
	return $ret . end_html;
}

sub html_comic_listing {
	my $name = shift;
	my $user = shift;
	my $filter = shift;
	my $comics = $dbh->selectcol_arrayref(qq{select comic from USER where ($filter)});
	
	my $ret = start_div({-class=>"group"}) . h1(a({name=>$name},$name));
	$ret .= start_table();
	
	my $count;
	my $counted;
	
	my %toRead;
	foreach my $comic ( @{$comics}) {
		my $bookmark = $user->{$comic}->{'bookmark'};
		if ($bookmark) {
			my $sc = $user->{$comic}->{'strips_counted'};
			my $num = dat($comic,$bookmark,'number');
			if ($sc and $num) {
				$toRead{$comic} =  $sc - $num;
			}
			else {
				$sc //= '';
				$num //= '';
				say "comic '$comic' strip '$bookmark' strips counted '$sc' number '$num'"; 
				$toRead{$comic} = $sc;
			}
		}
		else {
			$toRead{$comic} = $user->{$comic}->{'strips_counted'} // 0;
		}
	}
	
	foreach my $comic ( sort {$toRead{$b} <=> $toRead{$a}} @{$comics}) {
		my $usr = $user->{$comic};
		my $mul = $toRead{$comic};
		$mul = log($mul) if $mul;
		
		
		my ($first,$current,$bookmark,$last) = ($usr->{'first'},$usr->{'aktuell'},$usr->{'bookmark'},$usr->{'last'});
		my $cmc_str = td(a({-href=>"/comics?comic=$comic&strip=%s",-onmouseout=>"hideIMG();",
						-onmouseover=>"showImg('/strips/$comic/%s')"},"%s"));
		
		$ret .= "<tr>";
		$ret .= td(a({-href=>"/front?comic=$comic",-class=>($broken{$comic}?'broken':'comic'),
			-style=>"color:#". colorGradient($mul,10) .";font-size:".(($mul/40)+0.875)."em;"},$comic));
			
		$ret .= $first	 ?	sprintf($cmc_str , $first	 , $first	 , '|&lt;&lt;')  :td();
		$ret .= $current ?	sprintf($cmc_str , $current	 , $current	 , '&gt;&gt;' )  :td();
		$ret .= $bookmark?	sprintf($cmc_str , $bookmark , $bookmark , '||'		  )  :td();
		
		if ($current and $last and ($current eq $last)) {
			$ret .=	td('&gt;&gt;|');
		}
		elsif ($last) {
			$ret .= sprintf($cmc_str, $last ,$last , '&gt;&gt;|');
		}
		$ret .= td(toRead{$comic}) if param('toread');
		$ret .= td($usr->{'strip_count'}) if param('count');
		$ret .= td($usr->{'strips_counted'}) if param('counted');
		
		$ret .= "</tr>";
		
		# $ret .= Tr([
			# td([
			# a({-href=>"/front?comic=$comic",-class=>($broken{$comic}?'broken':'comic'),-style=>"color:#". colorGradient($mul,10) .";font-size:".(($mul/40)+0.875)."em;"},$comic) ,
			# $usr->{'first'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'first'},-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/".$usr->{'first'}."')"},"|&lt;&lt;") : undef ,
			# $usr->{'aktuell'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'aktuell'},-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/".$usr->{'aktuell'}."')"},"&gt;&gt;") : undef ,
			# $usr->{'bookmark'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'bookmark'},-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/".$usr->{'bookmark'}."')"},"||") : undef ,
			# ($usr->{'aktuell'} and $usr->{'last'} and ($usr->{'aktuell'} eq $usr->{'last'})) ? "&gt;&gt;|" : $usr->{'last'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'last'},-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/".$usr->{'last'}."')"},"&gt;&gt;|") : undef ,
			# param('toread')? $toRead{$comic} :undef, 
			# param('count')?$usr->{'strip_count'}:undef,
			# param('counted')?$usr->{'strips_counted'}:undef,
			# ])
		# ]);
	}
	return $ret . end_table . end_div;
}

sub colorGradient {
	my $cv = shift;
	my $base = shift;
	my $col = $cv/$base;
	if ($col<0.5) {
		my $r = sprintf "%02x", $col * 255 * 2;
		return "${r}ff00";
	}
	if ($col<1) {
		my $g = sprintf "%02x", (1-$col) * 255 * 2;
		return "ff${g}00";
	}
	return "ffffff";
}


=head2 Frontpage

here you see the first, (the L<bookmarked|/"Cataflag">) and the last strip. 
you can click them to start reading. you can also click on current (you will see a preview while hovering) 
to go to the strip you viewed last.

you can also click the B<stripslist> which is basically just a list of all strips,
go to the L<datalyzer|/"Datalyzer"> or the L<user config|/"User Config"> 
and last but not least you can L<categorize|/"Cataflag"> the comic.

=cut 

sub cfront {
	my $comic = param('comic') // shift;
	my $random = shift;
	my $ret = &kopf($comic . " Frontpage",0,0,
					&usr($comic,'first') ?"/comics?comic=$comic&strip=".&usr($comic,'first') :"0",
					&usr($comic,'last' ) ?"/comics?comic=$comic&strip=".&usr($comic,'last' ) :"0",
					);
					
	$ret .= start_div({-class=>'frontpage'});
	$ret .=		h2($comic);
	
	if(&usr($comic,'bookmark')) {	#if a bookmark is set we display 3 strips at once.
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first'),-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front3",-id=>'first',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"}));
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last'),-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front3",-id=>'last',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"}));
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'bookmark'),-accesskey=>'n',-title=>'bookmarked strip'},
				img({-class=>"front3",-id=>'bookmark',-src=>"/strips/$comic/".&usr($comic,'bookmark'),-alt=>"bookmark"}));
	} 
	else { #if not we just display two
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first'),-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front2",-id=>'first',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"}));
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last'),-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front2",-id=>'last',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"}));
	};
					
	$ret .=		start_div({-class=>"navigation"});
	
	if (&usr($comic,'aktuell')) {
		$ret .= a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'aktuell'),-accesskey=>'c',-title=>'last read strip',
				-onmouseover =>"document.getElementById('bookmark').src='/strips/$comic/".&usr($comic,'aktuell')."'",
				-onmouseout =>"document.getElementById('bookmark').src='/strips/$comic/".&usr($comic,'bookmark')."'"
				},'current') . br;
	}
	
	$ret .=		a({-href=>"/",-accesskey=>'i',-title=>'Index'},"Index").' '.
				a({-href=>"/comics?comic=$comic",-accesskey=>'s',-title=>'striplist'},"Striplist").' '.
				a({href=>"/tools?tool=cataflag&comic=$comic",-accesskey=>'c',-title=>'categorize'},'Categorize').
				br;
	$ret .=		usr($comic,'strip_count') .' '. usr($comic,'strips_counted').' '.
				a({href=>"/tools?tool=datalyzer&comic=$comic",-accesskey=>'d',-title=>'datalyzer'},'datalyzer').' '.
				a({href=>"/tools?tool=user&comic=$comic",-accesskey=>'u',-title=>'user'},'user'). br;
				
	if ($broken{$comic}) {
		$ret .= "broken " 
	}
	

	$ret .= "tags: " . join(" ",tags($comic)) if tags($comic);
	
	if ($random) {
		$ret .= br. a({-href=>"/tools?tool=random",-accesskey=>'r',-title=>'random strip'},"Random");
	}
	
	$ret .=		end_div;
	$ret .=		end_div();
	
	return $ret . end_html;
}



=head2 Strip Pages

these pages are pretty straight forward use the B<next> and B<prev> links to navigate 
use B<pause> to bookmark the strip and go to the L<categorize|/"Cataflag"> page
B<front> return you to the L<frontpage|/"Frontpage"> and B<site> links to the page the strip was downloaded

=cut

sub ccomic {
	my $comic = param('comic') // shift;
	my $strip = param('strip') // shift;
	my $random = shift;
	my $ret = &kopf("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	if ($strip) {
		my $ret;
		my $title = &dat($comic,$strip,'title') // '';
		my @titles = split(' !§! ',$title);
		$title =~ s/-§-//g;
		$title =~ s/!§!/|/g;
		$title =~ s/~§~/~/g;
		
		$strip =~ s/%7C/|/ig;
		$strip =~ s/ /%20/ig;
		
		my ($prev,$next,$first,$last) = (&dat($comic,$strip,'prev'),&dat($comic,$strip,'next'),&usr($comic,'first'),&usr($comic,'last'));
		
		$ret = &kopf($title,
					$prev  ?"/comics?comic=$comic&strip=" . $prev :"0",
					$next  ?"/comics?comic=$comic&strip=" . $next :"0",
					$first ?"/comics?comic=$comic&strip=" . $first:"0",
					$last  ?"/comics?comic=$comic&strip=" . $last :"0",
					"/strips/$comic/$next", #prefetch
					$next  ?qq#(new Image()).src = '/strips/$comic/$next'#:"0", #more prefetch .. 
					);
					
		$ret .= start_div({-class=>"comic"});
		
		$ret .= h3($title);
		
		if (-e "./strips/$comic/$strip") { 
			$ret .= img({-src=>"/strips/$comic/$strip",-title=>($titles[2]//''),-alt=>($titles[3]//'')});
		}
		elsif ($strip!~m/^dummy/) {
			$ret .= img({-src=>&dat($comic,$strip,'surl'),-title=>($titles[2]//''),-alt=>($titles[3]//'')}).br.
					a({-href=>"/tools?tool=download&comic=$comic&strip=$strip"},"(download)");
		}
		else {
			$ret .= "This page is a dummy. Errors are likely";
		}
		
		
		$ret .=start_div({-class=>"navigation"});
		
		$ret .= a({-href=>"/comics?comic=$comic&strip=".$prev,
				-title=>'previous strip',-accesskey=>'v'},'&lt;&lt; ') if $prev;
		$ret .= a({-href=>"/comics?comic=$comic&strip=".$next,
				-title=>'next strip',-accesskey=>'n'},'&gt;&gt; ') if $next;

		$ret .= br;
		if ($next) {
			$ret .= a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=r",
					-accesskey=>'d',-title=>'pause reading this comic'},"pause ");	
		}
		elsif (flags($comic)->{c}) {
			$ret .= a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=rcf",
					-accesskey=>'d',-title=>'finish reading this comic'},"finish ");	
		}
		else {
			$ret .= a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=r",
					-accesskey=>'d',-title=>'pause reading this comic'},"pause ");	
		}
		$ret .= a({-href=>"/front?comic=$comic",
				-accesskey=>'f',-title=>'frontpage'},"front ");			
				
		$ret .= a({-href=>&dat($comic,$strip,'url'),
				-accesskey=>'s',-title=>'homepage of the strip'},"site ") if &dat($comic,$strip,'url');	
				
		$ret .=end_div;
		$ret .= end_div;
		
		&usr($comic,'aktuell',$strip) unless ($random or $strip=~m/^dummy/);
		&usr($comic,'bookmark',$strip) if param('bookmark');
		return $ret . end_html;
	}
	else {
		my $list;
		my $dat = $dbh->selectall_hashref(qq(select strip,number,title from _$comic where number is not null),"number");
		
		$list = &kopf($comic);
		$list .= preview_head;
		
		
		#we save this string cause we need it really often, so we can save some processing time :)
		my $strip_str = div({-class=>"striplist"},img({-src=>"/strips/$comic/%s"}) . #the img is normally hidden by the strylesheet
			a({-href=>"/comics?comic=$comic&strip=%s",-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/%s')",}, "%s"));
		
		
		for (my $i = 1;defined $dat->{$i};$i++) {

			my $title = $dat->{$i}->{'title'} // '';
			$title =~ s/-§-//g;
			$title =~ s/!§!/|/g;
			$title =~ s/~§~/~/g;	
			$list .= sprintf $strip_str,
				$dat->{$i}->{strip}, #direct img url
				$dat->{$i}->{strip}, #strip page url
				$dat->{$i}->{strip}, #direct img url
				"$i : $dat->{$i}->{strip} : $title"; #strip title
		}
		$list .= end_html;
		return $list;
	}
}


=head1 Tools

=cut

sub ctools {
	my $tool = param('tool');
	my $comic = param('comic');
	
	
=head2 Cataflag

add or remove I<tags> by clicking the boxes next to them. or add new tags by typing their name into the box and press ok.

I<this comic is complete> marks the comic to be I<complete> (the story is complete and the comic will no longer update) 
afterwards you can mark the comic as I<finished> to  indicate that you finished reading the whole storyline.

you can also I<strop reading> a comic which basically means that you dont like it and wont red any further.

=cut

	if ($tool eq "cataflag") {
		my $tags = join(" ",param('tags')) .  (param('new_tag') ? " ". param('new_tag') : '');
		if ($tags) { tags($comic,$tags); }
		elsif (param('ok') ) { tags($comic,'<>'); };
		
		my $addflag = param('addflag');
		flags($comic,"+$addflag") if $addflag;
		if (param('bookmark')) {
			my $bookmark = param('bookmark');
			$bookmark =~ s/ /%20/ig;
			usr($comic,'bookmark',$bookmark );
		}
		my $res = &kopf($comic."tags and flags");

		$res .= h1('Tags');
		$res .= start_form({-method=>'GET',-action=>'tools',-name=>'setTags'});
		$res .= hidden('comic',$comic);
		$res .= hidden('tool',"cataflag");
		$res .= hidden('ok',1);
		$res .= checkbox_group(-name=>'tags',
								 -onclick=>'document.setTags.submit()',
	                             -values=>[&tags],
								 -default=>[&tags($comic)],
	                             -linebreak=>'true');
		$res .= "new: " . textfield(-name=>'new_tag');
		$res .= br . submit('ok');
		$res .= end_form;
		$res .= br . (flags($comic)->{c} ?a({-href=>"/tools?tool=cataflag&comic=$comic&addflag=rf"},"this comic is complete and i have finished reading it")
					: a({href=>"/tools?tool=cataflag&comic=$comic&addflag=c"},'this comic is complete'));
		$res .= br . a({href=>"/tools?tool=cataflag&comic=$comic&addflag=s"},'stop reading this comic');
		$res .= br. a({-href=>"/tools?tool=user&comic=$comic"},"advanced") .br;
		$res .= br. a({-href=>"/tools?comic=$comic&tool=cataflag"},"reload") .br. a({-href=>"/front?comic=$comic"},"Frontpage").br. a({-href=>"/"},"Index");
		$res .= end_html;;
		return $res;	
	}	
	if ($tool eq "download") {
		require dlutil;
		my $strip = param('strip');
		&dlutil::getstore(&dat($comic,$strip,'surl'),"./strips/$comic/$strip");
		return &ccomic;
	}
	
	
=head2 Datalyzer

gives you some statistics about your database

=over

=item * count

this is the total number of strips

=item * none

number of strips without I<prev> and I<next>;
this is mostly garbage

=item * prev

number of strips without I<next> but with I<prev>;
this is the last strip (or a strips with broken next) having more than one could be bad

=item * next

number of strips without I<prev> but with I<next>;
this is the first strip (or a strips with broken prev) having more than one could be bad

=item * prevnext

number of strips with I<next> but with I<prev>;
this are normal healty strips somewhere in the comic

=back

=cut
	
	if ($tool eq "datalyzer") {
		my %d;
		$d{count}->{n} = 0;
		$d{prevnext}->{n} = 0;
		$d{prev}->{n} = 0;
		$d{next}->{n} = 0;
		$d{none}->{n} = 0;
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
		
		if ($sec and ($sec eq 'strps')) {
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

	
=head2 User Config

here you can edit the user table directly. this is just for debugging purposes.

=cut

	if ($tool eq 'user') {
		my $user = $dbh->selectall_hashref("select * from USER","comic");
		my $res = &kopf('user');
		if ($comic) {
			$res .= start_form("GET","tools");
			$res .= hidden('tool',"user");
			$res .= start_table;
			if (param('delete') and (param('delete') ne '')) {
				&usr($comic,param('delete'),0,'delete');
			}
			foreach my $key (keys %{$user->{param('comic')}}) {
				if (param($key)) {
					&usr($comic,$key,param($key));
				}
				$res .=  Tr(td("$key"),td(textfield(-name=>$key, -default=>&usr($comic,$key), -size=>"100")),td(a({-href=>"/tools?tool=user&delete=$key&comic=" .$comic},"delete $key")));
			}
			return $res . end_table . submit('ok'). br . br .a({-href=>"/tools?tool=user&comic=".$comic},"reload") .br. a({-href=>"/tools?tool=user"},"all comics") . br . a({-href=>"/"},"Index") . end_html;
		}
		$res .= start_table;
		my $h = 0;
		foreach my $cmc (sort{uc($a) cmp uc($b)} (keys %{$user})) {

			$res .=  Tr(td('name'),td([keys %{$user->{$cmc}}])) if !$h;
			$h = 1;
			$res .=  Tr(td(a({-href=>"/tools?tool=user&comic=". $cmc},$cmc)),td([map {textfield(-name=>$_, -default=>&usr($cmc,$_))} keys %{$user->{$cmc}}]));
		}
		return $res . end_table . br . br .  a({-href=>"/"},"Index") . end_html;
	}
	
=head2 Custom Query

input a sqlite query which will be executed in the longer input field.
if you input a column in the second field, the output becomes more readable

=cut
	
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
	
=head2 force update

if used without a comic parameter it will update all comics (this may take some time). else it updates just the single comic

=cut
	
	if ($tool eq 'forceupdate') {
		if ($comic) {
			my $time = Time::HiRes::time;
			$dbh->do("UPDATE USER set server_update = NULL where comic like '$comic'");
			&update;
			return &kopf("Force Update $comic") . "Time: " . (Time::HiRes::time - $time) . " Seconds" . end_html;
		}
		my $time = Time::HiRes::time;
		$dbh->do("UPDATE USER set server_update = NULL");
		&update;
		return &kopf('Force Update All') . "Time: " . (Time::HiRes::time - $time) . " Seconds" . end_html;
	}
	
=head2 Random

get a random comic frontpage. you dont get comics that you are reading, have completed or stopped. no doubles in one session.
 you can reload the page to get a new random comic .. and another one ... and another one ... have .. to .. stop .. reloading ...

=cut
	
	if ($tool eq 'random') {
		my $firsts = $dbh->selectall_hashref('SELECT comic,first FROM user where (flags not like "%r%" and flags not like "%f%" and flags not like "%s%") OR flags IS NULL' , 'comic');
		my @comics = keys %{$firsts};
		my $comic;
		while($comic = splice(@comics,rand(int @comics),1)) {
			next if $broken{$comic};
			next if $rand_seen{$comic};
			$rand_seen{$comic} = 1;
			return cfront($comic,1);
		}
		undef %rand_seen;
	}

=head2 Export and Import

use export to export to I<export.cie>. containing your I<tags> and I<complete flags>

with import all .cie files in your current folder will be importet, renamed and put into the import folder. 
no changes to your data are made, imported values are just placed on top of your values.
you can delete all imported data via this link:

L<http://127.0.0.1/tools?tool=query&query=update+user+set+itags+%3D+NULL%2C+iflags+%3D+NULL>

copy all the files from you ./import folder to your main folder to reimport everything you had imported.

=cut	

	if($tool eq 'export') {
		my $data = $dbh->selectall_hashref("select comic,tags,flags from user where tags is not null or flags like '%c%'",'comic');
		open(EX,">export.cie");
		print EX "v1 This file is a tag and flag export of httpserver v$VERSION\n";
		foreach my $cmc (keys %{$data}) {
			$data->{$cmc}->{flags} =~ s/[^c]//g;
			print EX join(",",@{[$data->{$cmc}->{comic},$data->{$cmc}->{tags}//'',$data->{$cmc}->{flags}//'']}) . "\n";
		}
		close(EX);
	}
	if($tool eq 'import') {
		opendir(DIR,'.');
		while (my $file = readdir DIR) {
			next unless $file =~ m/\w+\.cie$/;
			open(EX,"<$file");
			my $v = <EX> =~ m/^v(\d+)/;
			while (my $line = <EX>) {
				chomp($line);
				my ($comic,$tags,$flags) = split(',',$line);
				flags($comic,"+$flags",1);
				tags($comic,"+$tags",1);
			}
			close(EX);
			unless (-e "./import/") {
				mkdir("./import/");
			}
			rename($file,'./import/'.time.$file);
		}
		closedir(DIR);
	}
	
	
=head2 Filter

the first field is the name of the filter (use aplphanumeric without spaces for peace of mind) 

the second field is a sqlite expression. if it returns true the comic is displayed with this filter. 
it works somewhat like this:

	SELECT comic FROM USER WHERE your_expression_here

you can use all the fields of user to filter from. try this to get only comics with mor than 500 strips:

	strips_counted > 500
	
or just comics beginning with C or S

	comic like 'C% or comic like 'S%'
	
endless possibilitys!


=cut
	
	if($tool eq 'filter') {
		if (param('new_filter_name') and param('new_filter')) {
			filter(param('new_filter_name'),param('new_filter'))
		}
		if (param('delete')) {
			filter(param('delete'),0)
		}
		my $res = &kopf('Filter');
		$res .= start_form("GET","tools");
		$res .= hidden('tool',"filter");
		$res .= start_table;
		foreach my $filter (&filter) {
			if (defined param($filter) and param($filter) ne '') {
				filter($filter,param($filter));
			}
			$res .=  Tr(td($filter),td(filter($filter),td(a({-href=>"/tools?tool=filter&delete=$filter"},'delete'))));
		}
		$res .=  Tr(td(textfield(-name=>"new_filter_name", -size=>"20")),td(textfield(-name=>"new_filter", -size=>"100")));
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools?tool=filter"},"reload") .br. a({-href=>"/"},"Index") . end_html;
	}
}

=head2 custom style sheets

you can create a I<overwrite.css> next to your I<default.css>. the overwrite will be appended to de default 
so any changes you make there will overwrite the default settings. 

but be careful it is possible to execute perl code from within the style sheet so dont load styles from untrusted sources

=cut

sub ccss {
	my $ret_css = '';
	load_css();
	$ret_css = eval('qq<'.$css.'>');
	return $ret_css;
}


=head2 "Hidden" Features

add I<measure_time=1> as a parameter to any link to get some hi res time output in your console

create a I<favicon.ico> in the comcol main folder to set this as your favicon!

you can give I<toRead=1>, I<count=1> and I<counted> as parameters to the index page displaying more statistic!

goto L<http://127.0.0.1/pod> for documentation! you can also add C<?file=> to specify any file besides httpserver as POD source!

=cut



=head1 Internal Functions

stop reading here unless you want to understand strange behavior or possible bugs :)

=cut

sub cpod {
	require Pod::Simple::HTML;
	my $path;
	given (param("file")) {
		when(/comic3/) {$path = "comic3.pl"}
		when(/comic/) {$path = "lib/comic.pm"}
		when(/httpserver/) {$path = "httpserver.pl"}
		when(/page/) {$path = "lib/page.pm"}
		when(/dbutil/) {$path = "lib/dbutil.pm"}
		when(/dlutil/) {$path = "lib/dlutil.pm"}
		default {$path = "httpserver.pl"};
	}
	my $ret = kopf('POD') . start_div({-class=>'pod'});
	my $parser = Pod::Simple::HTML->new();
	$parser->perldoc_url_prefix("/pod?file=");
	$parser->index(1);
	$parser->bare_output(1);
	$parser->output_string( \$ret );
	$parser->parse_file($path);
	return $ret . end_div() . end_html;
}


=head2 update

no arguments

first of all, this will check the database structure.

then it figures out which comics had new images B<saved> since they were last updated
and does all of the following in this order to each comic one after another:

it removes B<dummy>s which dont have a prev or a next strip (are at the beginning or the end) and deletes links to them

it adds the warn flag if two ore more strips are linking to the same strip in the same direction (have same prev or next)

it tries to find the first strip that is a strip without a prev, if there are multiple, it tries to get a non I<dummy> strip 
(if there are multiple not dummy strips it takes whatever the database returns first)

last it counts the strips (by going from the first to the last), gives each a number and determines the last strip.


=cut

sub update {
	my $ttu = Time::HiRes::time;
	dbutil::check_table($dbh,"USER");
	dbutil::check_table($dbh,"CONFIG");
	$dbh->do("UPDATE USER set first = NULL , server_update = NULL where first like 'dummy%'");
	
	# foreach my $comic (@{$dbh->selectcol_arrayref(qq(select comic from USER where first IS NULL))}) {
		# my @first = @{$dbh->selectcol_arrayref("select strip from _$comic where prev IS NULL and next IS NOT NULL")};
		# next if (@first == 0); 
		# @first = grep {$_ !~ /^dummy/} @first if (@first > 1);
		# usr($comic,'first',$first[0]);
	# }
	my @comics = @{$dbh->selectcol_arrayref(qq(select comic,server_update - last_save as time from USER where (time <= 0) OR (server_update IS NULL)))};
	local $| = 1;
	$dbh->{AutoCommit} = 0;
	print "updating ". scalar(@comics) ." comics:\n" if @comics;
	foreach my $comic (@comics) {
		
		dbutil::check_table($dbh,"_$comic");
		
		usr($comic,'server_update',time);
		
		my @dummys = $dbh->selectrow_array("select strip from _$comic where (strip like 'dummy%') and ((prev IS NULL) or (next IS NULL))");
		$dbh->do("DELETE FROM _$comic where (strip like 'dummy%') and ((prev IS NULL) or (next IS NULL))");
		foreach my $dummy (@dummys) {
			$dbh->do("update _$comic set next = NULL where next = '$dummy'");
			$dbh->do("update _$comic set prev = NULL where prev = '$dummy'");
		}
		
		if ($dbh->selectrow_array("SELECT COUNT(next) AS dup_count FROM _$comic GROUP BY next HAVING (COUNT(next) > 1)")
		or  $dbh->selectrow_array("SELECT COUNT(prev) AS dup_count FROM _$comic GROUP BY prev HAVING (COUNT(prev) > 1)")) {
			&flags($comic,'+w');
		}
		else {
			&flags($comic,'-w');
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
		$strps = $dbh->selectall_hashref(qq(select strip , next, number from _$comic), "strip");
		
		my $i = 0;
		my $prevstrip;
		if ($strp) {
			$i++ ;
			dat($comic,$strp,'number',$i) unless ($strps->{$strp}->{number} and $strps->{$strp}->{number} == $i);
			while((defined $strps->{$strp}->{next}) and $strps->{$strps->{$strp}->{next}}) {
				$prevstrip = $strp;
				$strp = $strps->{$strp}->{next};
				if ($double{$strp}) {
					$strp = $prevstrip;
					last;
				}
				$double{$strp} = 1;
				$i++;
				dat($comic,$strp,'number',$i) unless ($strps->{$strp}->{number} and $strps->{$strp}->{number} == $i);
			}
		}
		usr($comic,'last',$strp);
		usr($comic,'strips_counted',$i);
		print ".";
	}
	print "\nupdating: ". (Time::HiRes::time - $ttu) . " seconds\ncommiting: " if @comics;
	my $ttc = Time::HiRes::time;
	$dbh->{AutoCommit} = 1;
	say "" . (Time::HiRes::time - $ttc) . " seconds" if @comics;
	my $comini = dbutil::readINI('comic.ini',);
	foreach my $name (keys %{$comini}) {
		if ($comini->{$name}->{broken}) {
			$broken{$name} = 1;
		}
	}
}



sub comics {
	#return @{$comics->{__SECTIONS__}};
	return @{$dbh->selectcol_arrayref("select comic from USER")};
}

=head2 config usr dat

C<config(comic,key,value,delet value?)> accesses the table B<CONFIG>

C<usr(comic,key,value,delet value?))> accesses B<USER>

C<dat(comic,strip,key,value,delet value?))> access the table B<_I<comicname>>

usr and dat use caching, so asking them for the same value over and over again should be quiete fast

=cut

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
	else {
		if (($c . $key) eq $db_cache[0]) {
			return $db_cache[1];
		}
	}
	$db_cache[0] = $c . $key;
	$db_cache[1] = $dbh->selectrow_array(qq(select $key from USER where comic="$c"));
	return $db_cache[1];
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
	else {
		if (($c . $strip . $key) eq $db_cache[2]) {
			return $db_cache[3];
		}
	}
	$db_cache[2] = $c . $strip . $key;
	$db_cache[3] = $dbh->selectrow_array(qq(select $key from _$c where strip="$strip"));
	return $db_cache[3];
}

=head2 tags and flags

C<tags(comic,new tag string,import?)> or C<flags(comic,new tag string,import?)>

tags and flags are implemented very similiar.
tags will return all tags if all parameters are omitted, flags will return 0

use C<< <> >> as C<new string> to delete

it creates two hashes one of imported and one of original

deletes all from import which are in original

returns all (import and original) flags|tags unless C<new string>

if string begins with + addes to original (and import if C<import>)

if string begins with - deletes from both

begins with wordcharacter (or space for flags): sets flags|tags to given string (keeps in import what was in import)



=cut

sub tags {
	my $comic = shift || '';
	my $new = shift || '';
	my $import = shift;
	
	if ($comic) {
		if ($new eq '<>') {
			usr($comic,'itags',0,'delete');
			usr($comic,'tags',0,'delete') if !$import;
			return 1;
		}
		
		my $tagsref = $dbh->selectrow_arrayref("select tags,itags from USER where comic='$comic'");
		my $otags = ($tagsref->[0]//'');
		my $itags = ($tagsref->[1]//'');
		
		my $otag = {};
		my $itag = {};
		
		$otag->{lc $_} = 1 for(split(/\W+/,$otags));
		$itag->{lc $_} = 1 for(split(/\W+/,$itags));
		delete $itag->{lc $_} for (keys %$otag); 
		
		
		if ($new) {
			if ($new =~ /^\+([\w\s]+)/) {
				for (split(/\W+/,$1)) {
					$otag->{lc $_} = 1;
					$itag->{lc $_} = 1 if $import; # we dont need to add to import if were not importing
				}
			}	
			elsif ($new =~ /^-([\w\s]+)/) {
				for (split(/\W+/,$1)) {
					delete $otag->{lc $_};  #subtracting from both, cause we dont know which containts
					delete $itag->{lc $_};	
				}
			}
			elsif($new =~ /^([\w\s]+)$/) {
				unless ($import) { # on normal circumstances
					$otag = {};	#we delete all of our tags
					$otag->{lc $_} = 1 for (split(/\W+/,$new)); #and recreate with the given ones
					delete $otag->{lc $_} or delete $itag->{lc $_} for (keys %$itag); #then we delete all which were imported previously, and delete all imported which were not in the new ones
				}
				else { #same as above just changed imported and original
					$itag = {};	
					$itag->{lc $_} = 1 for (split(/\W+/,$new));
					delete $itag->{lc $_} or delete $otag->{lc $_} for (keys %$otag); 
				}
			}
			my $ot = join(' ',keys %{$otag});
			my $it = join(' ',keys %{$itag});
			($ot ? usr($comic,'tags',$ot) : usr($comic,'tags',0,'delete')) if (!$import); #we dont want to save the originals while importing!
			$it ? usr($comic,'itags',$it) : usr($comic,'itags',0,'delete'); #if we are not importingwe save both (deleting from both)

		}
		$otag->{$_} = 1 for keys(%$itag);
		return keys %$otag;
	}
	else {
		my $tags = $dbh->selectcol_arrayref("select tags from USER");
		my $itags = $dbh->selectcol_arrayref("select itags from USER");
		my %taglist;
		foreach (@{$tags},@{$itags}) {
			next unless defined $_;
			foreach my $tag ($_ =~ m/(\w+)/gs) {
				$taglist{lc $tag} = 1;
			}
		}
		return sort {lc($a) cmp lc($b)} (keys %taglist);
	}
}


sub flags {
	my $comic = shift || '';
	my $new = shift || '';
	my $import = shift;
	return 0 unless $comic;
	
	if ($new eq '<>') {
		usr($comic,'iflags',0,'delete');
		usr($comic,'flags',0,'delete') if !$import;
		return 1;
	}
	
	my $flagref = $dbh->selectrow_arrayref("select flags,iflags from USER where comic='$comic'");
	my $oflags = $flagref->[0]//'';
	my $iflags = $flagref->[1]//'';
	
	my $oflag = {};
	my $iflag = {};
	
	$oflag->{$_} = 1 for(split(//,$oflags));
	$iflag->{$_} = 1 for(split(//,$iflags));
	delete $iflag->{$_} for(keys %$oflag);
	
	
	if ($new) {
		if ($new =~ /^\+(\w+)/) {
			for (split(//,$1)) {
				$oflag->{$_} = 1;
				$iflag->{$_} = 1 if $import;
			}		
		}	
		elsif ($new =~ /^-(\w+)/) {
			for (split(//,$1)) {
				delete $oflag->{$_};	
				delete $iflag->{$_};
			}		
		}
		elsif ($new =~ /^(\w+)$/) {
			unless ($import) { # on normal circumstances
				$oflag = {};	#we delete all of our flags
				$oflag->{$_} = 1 for (split(//,$new)); #and recreate with the given ones
				delete $oflag->{$_} or delete $iflag->{$_} for (keys %$iflag); #then we delete all which were imported previously, and delete all imported which were not in the new ones
			}
			else { #same as above just changed imported and original
				$iflag = {};	
				$iflag->{$_} = 1 for (split(//,$new));
				delete $iflag->{$_} or delete $oflag->{$_} for (keys %$oflag);
			}			
		}
		my $of = join('',keys %$oflag);
		my $if = join('',keys %$iflag);
		if (!$import) {
			($of) ? usr($comic,'flags',$of) : usr($comic,'flags',0,'delete');
		}
		($if) ? usr($comic,'iflags',$if) : usr($comic,'iflags',0,'delete');

	}
	$oflag->{$_} = 1 for keys(%$iflag);
	return $oflag;
}

=head2 filter

C<filter(filter name,new filter)>

without parameters returns all filter names

with name returns that filter

with name and new, sets filter (deletes if new is defined but not true)

=cut

sub filter {
	my $filter = shift;
	my $new = shift;
	my $str_fil = config('filter') // '';
	my %filter = split("<,>",$str_fil);
	
	if (defined $new and $filter) {
		if ($new) {
			$filter{$filter} = $new;
		}
		else {
			delete $filter{$filter};
		}
		$str_fil = join("<,>",each(%filter));
		if ($str_fil) {
			config('filter',$str_fil);
		}
		else {
			config('filter',$str_fil,1);
		}
	}
	
	return keys %filter unless $filter;
	return $filter{$filter};

}

sub load_css{
	local $/ = undef;
	open(CSS,"<default.css");
	$css =  <CSS>;
	close(CSS);
	if (-e "overwrite.css") {
		$css .= "\n";
		open(CSS2,"<overwrite.css") ;
		$css .= <CSS2>;
		close(CSS2);
	}

}