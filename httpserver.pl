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
$VERSION = '2.51';

my $d = HTTP::Daemon->new(LocalPort => 80);
die "could not listen on port 80 - someones listening there already?" unless $d;

my $res = HTTP::Response->new( 200, 'success', ['Content-Type','text/html; charset=iso-8859-1']); #our main response
my $rescss = HTTP::Response->new( 200, 'success', ['Content-Type','text/css; charset=iso-8859-1']); #response for style
#my %index;
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout'); #we dont want to timeout (timeout happens if comic3.pl and httpserver.pl are run at the same time)
my %broken; #we save all the comics marked as broken in comic.ini here
my %rand_seen; #this is for remembering which comics we already selected randomly
my $measure_time = $ARGV[0]; #set this to one to get some info on request time
my $css; #we save our style sheet in here
my %strpscache; #caching strips db

# $dbh->{Profile} = 6 if $measure_time;

#if we are processing something, we dont want to update!
# if ($dbh->selectrow_array(qq(select processing from CONFIG where processing is not null and processing not like "")))  {
	# say "skipping update while comics are processed..."; TODO
# }
# else {
	# &update;
# }
my $comini = dbutil::readINI('comic.ini',);
foreach my $name (keys %{$comini}) {
	if ($comini->{$name}->{broken}) {
		$broken{$name} = 1;
	}
}


=head1 Usage

when you run httpserver.pl it will update the database and listen for you on port 80.
you can connect with any webbrowser and start reading some comics.

=cut

print "Please contact me at: <URL:", "http://127.0.0.1/" ,">\n";
while (my $c = $d->accept) {
	while (my $r = $c->get_request) {
		if (($r->method eq 'GET')) {
			my $req_start_time = Time::HiRes::time if $measure_time; 
			if ($r->url->path eq '/favicon.ico') {
				$c->send_file_response("./favicon.ico");
			}
			elsif ($r->url->path =~ m#^/strips/(.*?)/(.*)$#) {
				my ($comic,$strip) = ($1,$2);
				($strip =~ /^\d+$/) ? $strip = dbstrps($comic,'id'=>$strip,'file') : undef;
				$c->send_file_response("./strips/$comic/$strip");
			}
			elsif ($r->url->path eq '/style.css') {
					$rescss->content(&ccss);
					$c->send_response($rescss);
			}
			else {
				restore_parameters($r->url->query);

				if ($r->url->path =~ m#/comics/(\w+)(?:/(\w+))?#i) {
					cache_strps($1);
					if ($2) { $res->content(&ccomic($1,$2)); }
					else { $res->content(&cclist($1)); }
					
				}
				elsif ($r->url->path =~ m#/front/(\w+)#i) {
					$res->content(&cfront($1));
				}
				elsif ($r->url->path =~ m#^/tools/(\w+)(?:/(\w+))?#i) {
					my $answ = &ctools($1,$2);
					if (!$answ)  { 
						$c->send_redirect( 'http://127.0.0.1' . $r->url->path );
						next;
					}
					$res->content($answ);
				}
				elsif ($r->url->path eq '/tools') {
					say "error called old tool link";
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
		$c->send_crlf;
		#$c->close;
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

the three (you can only see those actually linking to something) links next to the comic name 
will bring you to the I<first>, I<bookmarked> and I<last> strip.

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
				.	a({-href=>"/tools/query",-accesskey=>'q',-title=>'query'},"Custom Query"). br 
				#.	a({-href=>"/tools/filter",-accesskey=>'f',-title=>'filter'},"Custom Contents"). br 
				.	a({-href=>"/tools/random",-accesskey=>'r',-title=>'random'},"Random Comic"). br 
				.	a({-href=>"/pod",-accesskey=>'h',-title=>'help'},"Help"). br ;
				
	my $i = 1;
	$ret .=	br . "Contents:" . br .
				join("",map { a({href=>"#$_",-accesskey=>$i++,-title=>$_},$_) . br} (qw(continue other finished stopped),)); #TODO &filter));
				 
	$ret .=	br . "Tags:" . br .
				 a({-href=>"/"},'Any Tag') . br .
				 join("",map {a({-href=>"/?tag=$_".join("",map {"&tag=$_"} (@tag)) },$_) . br} (tags()));	
				 
	$ret .=	br . "Color Scale:" .br.
				 join("",map {div({-style=>"color:#".colorGradient(log($_),10)},$_ )} (10000,5000,2000,1000,500,200,100,50,10,1)) . br;
		 
	$ret .= end_div(); 
	
	$ret .= &preview_head();
	
	my $cmcs = $dbh->selectall_hashref("SELECT * FROM comics",'comic');
	my $tagcheck = '1';
	$tagcheck = join(' and ',map {"tags like '%$_%'"} (@tag)) if @tag;
	$ret .= html_comic_listing('continue',$cmcs,qq{flags like '%r%' and flags not like '%f%' and flags not like '%s%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('other',$cmcs,qq{((flags not like '%r%' and flags not like '%f%' and flags not like '%s%') or flags is null) and ($tagcheck) }).br;
	$ret .= html_comic_listing('finished',$cmcs,qq{flags like '%f%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('stopped',$cmcs,qq{flags like '%s%' and ($tagcheck)}).br;
	# foreach (&filter) { TODO
		# next unless $_;
		# $ret .= html_comic_listing($_,$cmcs, '(' . filter($_) .") and ($tagcheck)").br;
	# }
	return $ret . end_html;
}

sub html_comic_listing {
	my $name = shift;
	my $user = shift;
	my $filter = shift;
	my $comics = $dbh->selectcol_arrayref("SELECT comic FROM comics WHERE ($filter)");
	
	my $ret = start_div({-class=>"group"}) . h1(a({name=>$name},$name));
	$ret .= start_table();
	
	my $count;
	my $counted;
	
	my %toRead;
	foreach my $comic ( @{$comics}) {
		if ($broken{$comic}) {
			$toRead{$comic} = -1;
			next;
		}
		my $bookmark = $user->{$comic}->{'bookmark'};
		if ($bookmark) {
			#my $sc = $user->{$comic}->{'strip_count'};
			my $sc = dbstrps($comic,'id'=>$user->{$comic}->{'last'},'number');
			my $num = dbstrps($comic,'id'=>$bookmark,'number');
			if ($sc and $num) {
				$toRead{$comic} =  $sc - $num;
			}
			else {
				$toRead{$comic} = -1;
			}
		}
		else {
			$toRead{$comic} = dbstrps($comic,'id'=>$user->{$comic}->{'last'},'number') // -1;
		}
	}
	
	foreach my $comic ( sort {$toRead{$b} <=> $toRead{$a}} @{$comics}) {
		my $usr = $user->{$comic};
		my $mul = $toRead{$comic};

		$mul = ($mul > 0) ? log($mul) : $mul;
		
		
		my ($first,$bookmark,$last) = ($usr->{'first'},$usr->{'bookmark'},$usr->{'last'});
		my $cmc_str = td(a({-href=>"/comics/$comic/%s",-onmouseout=>"hideIMG();",
						-onmouseover=>"showImg('/strips/$comic/%s')"},"%s"));
		
		$ret .= "<tr>";
		$ret .= td(a({-href=>"/front/$comic",-class=>($broken{$comic}?'broken':'comic'),
			-style=>"color:#". colorGradient($mul,10) .";font-size:".(($mul/40)+0.875)."em;"},$comic));
			
		$ret .= $first	 ?	sprintf($cmc_str , $first	 , $first	 , '|&lt;&lt;')  :td();
		$ret .= $bookmark?	sprintf($cmc_str , $bookmark , $bookmark , '||'		  )  :td();
		
		if ($bookmark and $last and ($bookmark eq $last)) {
			$ret .=	td('&gt;&gt;|');
		}
		elsif ($last) {
			$ret .= sprintf($cmc_str, $last ,$last , '&gt;&gt;|');
		}
		$ret .= td(toRead{$comic}) if param('toread');
		$ret .= td($usr->{'strip_count'}) if param('count');
		
		$ret .= "</tr>";
	}
	return $ret . end_table . end_div;
}

sub colorGradient {
	my $cv = shift;
	my $base = shift;
	my $col = $cv/$base;
	if ($col<0) {
		return 'ffffff';
	}
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
you can click them to start reading.

you can also click the B<stripslist> which is basically just a list of all strips,
and you can L<categorize|/"Cataflag"> the comic.

=cut 

sub cfront {
	my $comic = param('comic') // shift;
	my $first =  dbcmcs($comic,'first');
	my $last = dbcmcs($comic,'last' );
	my $bookmark = dbcmcs($comic,'bookmark' );
	my $ret = &kopf($comic . " Frontpage",0,0,
					$first ?"/comics/$comic/$first" :"0",
					$last  ?"/comics/$comic/$last " :"0",
					);
					
	$ret .= start_div({-class=>'frontpage'});
	$ret .=		h2($comic);
	
	if($bookmark) {	#if a bookmark is set we display 3 strips at once.
		$ret .= a({-href=>"/comics/$comic/$first",-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front3",-id=>'first',-src=>"/strips/$comic/".$first,-alt=>"first"}));
		$ret .= a({-href=>"/comics/$comic/$last",-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front3",-id=>'last',-src=>"/strips/$comic/".$last,-alt=>"last"}));
		$ret .= a({-href=>"/comics/$comic/$bookmark",-accesskey=>'n',-title=>'bookmarked strip'},
				img({-class=>"front3",-id=>'bookmark',-src=>"/strips/$comic/".$bookmark,-alt=>"bookmark"}));
	} 
	else { #if not we just display two
		$ret .= a({-href=>"/comics/$comic/$first",-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front2",-id=>'first',-src=>"/strips/$comic/".$first,-alt=>"first"}));
		$ret .= a({-href=>"/comics/$comic/$last",-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front2",-id=>'last',-src=>"/strips/$comic/".$last,-alt=>"last"}));
	};
					
	$ret .=		start_div({-class=>"navigation"});
	
	$ret .=		a({-href=>"/",-accesskey=>'i',-title=>'Index'},"Index").' '.
				a({-href=>"/comics/$comic",-accesskey=>'s',-title=>'striplist'},"Striplist").' '.
				a({href=>"/tools/cataflag/$comic",-accesskey=>'c',-title=>'categorize'},'Categorize').
				br;
	$ret .=		'Strips: ' . $last .' '; #dbcmcs($comic,'strip_count').' ';
				
	if ($broken{$comic}) {
		$ret .= "broken " 
	}
	

	$ret .= "tags: " . join(" ",tags($comic)) if tags($comic);
	
	$ret .=		end_div;
	$ret .=		end_div();
	
	return $ret . end_html;
}



=head2 Strip Pages

these pages are pretty straight forward use the B<next> and B<prev> links to navigate 
use B<pause> to bookmark the strip and go to the L<categorize|/"Cataflag"> page
B<front> returns you to the L<frontpage|/"Frontpage"> and B<site> links to the page the strip was downloaded from

=cut

sub ccomic {
	my $comic = shift;
	my $strip = shift;
	
	return &kopf("Error") . "no comic defined" unless $comic;
	return &kopf("Error") . "no strip defined" unless $strip;

	my %titles = get_title($comic,$strip);
	
	my ($prev,$next, $first,$last,$file) = (
			dbstrps($comic,'id'=>$strip,'prev'),dbstrps($comic,'id'=>$strip,'next'),
			dbcmcs($comic,'first'),dbcmcs($comic,'last'),dbstrps($comic,'id'=>$strip,'file')
		);
	my $nfile = dbstrps($comic,'id'=>$next,'file');
	my $ret = &kopf($titles{st}//($comic .' - '. $strip),
				$prev  ?"/comics/$comic/$prev" :"0",
				$next  ?"/comics/$comic/$next" :"0",
				$first ?"/comics/$comic/$first":"0",
				$last  ?"/comics/$comic/$last" :"0",
				$nfile ?"/strips/$comic/$nfile":"0", #prefetch
				$nfile  ?"(new Image()).src = '/strips/$comic/$nfile'":"0", #more prefetch .. 
				);
				
	$ret .= start_div({-class=>"comic"});
	
	$ret .= h3($titles{h1}) if $titles{h1};
	if ($file and (-e "./strips/$comic/$file")) { 
		$ret .= img({-src=>"/strips/$comic/$file",-title=>($titles{it}//''),-alt=>($titles{ia}//'')});
	}
	elsif ($file) {
		$ret .= img({-src=>dbstrps($comic,'id'=>$strip,'surl'),-title=>($titles{it}//''),-alt=>($titles{ia}//'')});
		$ret .= br . 'this strip is not local';
	}
	else {
		my @animals = (	['(\__/)','|\__/|','()__()','(\.../)',,'@_@'],
						["(0'.'0)",'(o.o)','(O.o)','( °.° )','(*,,*)'],
						['( >< )','(")_(")','( >.< )','(>_<)','(")(")']
					);
		my @animal = map { $_->[rand(@$_)]} @animals;
		
		$ret .= br.br.join(br,@animal).br;
		$ret .= 'something has stolen the strip! ';
		$ret .= 'check the site for reasons';
	}
	
	
	$ret .=start_div({-class=>"navigation"});
	
	$ret .= a({-href=>"/comics/$comic/$prev",
			-title=>'previous strip',-accesskey=>'v'},'&lt;&lt; ') if $prev;
	$ret .= a({-href=>"/comics/$comic/$next",
			-title=>'next strip',-accesskey=>'n'},'&gt;&gt; ') if $next;

	$ret .= br;
	if ($next) {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=r",
				-accesskey=>'d',-title=>'pause reading this comic'},"pause ");	
	}
	elsif (flags($comic)->{c}) {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=rcf",
				-accesskey=>'d',-title=>'finish reading this comic'},"finish ");	
	}
	else {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=r",
				-accesskey=>'d',-title=>'pause reading this comic'},"pause ");	
	}
	$ret .= a({-href=>"/front/$comic",
			-accesskey=>'f',-title=>'frontpage'},"front ");			
			
	my $purl = dbstrps($comic,'id'=>$strip,'purl');
	$ret .= a({-href=>$purl, -accesskey=>'s',-title=>'homepage of the strip'},"site ") if $purl;	
			
	$ret .= end_div;
	$ret .= end_div;
	
	dbcmcs($comic,'bookmark',$strip) if param('bookmark');
	return $ret . end_html;

}

sub cclist {
	my $comic = shift;
	my $ret = &kopf("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	my $list;
	my $dat = $dbh->selectall_hashref("SELECT id,number,title,file FROM _$comic WHERE number IS NOT NULL","number");
	
	$list = &kopf($comic);
	$list .= preview_head;
	
	
	#we save this string cause we need it really often, so we can save some processing time :)
	my $strip_str = div({-class=>"striplist"},img({-src=>"/strips/$comic/%s"}) . #the img is normally hidden by the strylesheet
		a({-href=>"/comics/$comic/%s",-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/%s')",}, "%s"));
	
	
	for (my $i = 1;defined $dat->{$i};$i++) {

		my %titles = get_title($comic,$dat->{$i}->{id});
		$list .= sprintf $strip_str,
			$dat->{$i}->{file}, #direct img url
			$dat->{$i}->{id}, #strip page url
			$dat->{$i}->{file}, #direct img url
			"$i : $dat->{$i}->{file} : " .join(' - ',grep { $_ } values(%titles) ); #strip title
	}
	$list .= end_html;
	return $list;
}

sub get_title {
	my ($comic,$strip) = @_;
	my $title = dbstrps($comic,'id'=>$strip,'title') // '';
	my %titles;
	if ($title =~ /^\{.*\}$/) {
		%titles = %{eval($title)};
		#ut - user title; st - site title; it - image title; ia - image alt; h1 - head 1; dt - div title ; sl - selected title;
	}
	else {
		my @titles = split(' !§! ',$title);
		$title =~ s/-§-//g;
		$title =~ s/!§!/|/g;
		$title =~ s/~§~/~/g;
		$titles{ut} = $titles[0];
		$titles{st} = $titles[1];
		$titles{it} = $titles[2];
		$titles{ia} = $titles[3];
		$titles{h1} = $titles[4];
		$titles{dt} = $titles[5];
		$titles{sl} = $titles[6];
	}
	return %titles;
}

=head1 Tools

=cut

sub ctools {
	my $tool = shift;
	my $comic = shift;
	if (!$tool) {
		return kopf('Eroor') . 'called tools without tool';
	}
	
=head2 Cataflag

add or remove I<tags> by clicking the boxes next to them. or add new tags by typing their name into the box and press ok.
you should not add to many or to specialized tags because they are globally visible. try to find categories fitting a lot of comics.


you can odd or remove I<flags> the same way. 

=over

=item * r : you are B<r>eading this comic

=item * c and f : the comic is B<c>omplete and you have B<f>inished reading it

=item * s : comic is B<stopped>

=item * l and w : comic has B<l>oop or B<w>arning

=back

r will be set when you click pause to bookmark the comic, w and l are for debugging


click L<advanced|/"Database View"> to see a view of the comics table 

click L<datalyzer|/"Datalyzer"> gives you some counts on the comics table

=cut

	if ($tool eq "cataflag") {
		my $tags = join(" ",param('tags')) .  (param('new_tag') ? " ". param('new_tag') : '');
		if ($tags) { tags($comic,$tags); }
		elsif (param('st')) { tags($comic,'<>'); }
		my $flags = join ('',param('flags'));
		if ($flags) {flags($comic,$flags)}
		elsif (param('sf')) { flags($comic,'<>'); }
		my $addflag = param('addflag');
		flags($comic,"+$addflag") if $addflag;
		if (param('bookmark')) {
			my $bookmark = param('bookmark');
			dbcmcs($comic,'bookmark',$bookmark );
		}
		if (param()) {
			return undef; # this causes to redirect back to this page without parameters
		}
		my $res = &kopf($comic." tags and flags");
		$res .= start_div({-class=>'tools'});
		$res .= h1('Tags');
		$res .= start_form({-method=>'GET',-action=>"/tools/cataflag/$comic",-name=>'setTags'});
		$res .= hidden('st',1);
		$res .= checkbox_group(-name=>'tags',
								 -onclick=>'document.setTags.submit()',
	                             -values=>[&tags],
								 -default=>[&tags($comic)],
	                             -linebreak=>'true');
		$res .= br . textfield(-name=>'new_tag') . " enter new";
		#$res .= br . submit('ok');
		$res .= end_form;
		$res .= h1('Flags');
		$res .= start_form({-method=>'GET',-action=>"/tools/cataflag/$comic",-name=>'setFlags'});
		$res .= hidden('sf',1);
		$res .= checkbox_group(-name=>'flags',
								 -onclick=>'document.setFlags.submit()',
	                             -values=>[qw(c r f s l w)],
								 -default=>[ keys %{flags($comic)}],
	                             -linebreak=>'true',
								 -disabled=>[qw(l w)],
								 -labels=>{c=>'this comic is complete',r=>'you are reading this comic',
								 f=>'you finished reading this comic (needs c)',s=>'you stopped reading this comic',
								 l=>'this comic has a loop',w=>'database error warning'});
		#$res .= br . submit('ok');
		$res .= end_form;
		# $res .= br . (flags($comic)->{c} ?a({-href=>"/tools/cataflag/$comic?addflag=rf"},"this comic is complete and i have finished reading it")
					# : a({href=>"/tools/cataflag/$comic?addflag=c"},'this comic is complete'));
		# $res .= br . a({href=>"/tools/cataflag/$comic?addflag=s"},'stop reading this comic');
		$res .= br. a({-href=>"/tools/comics/$comic"},"advanced") .br;
		$res .= a({href=>"/tools/datalyzer/$comic",-accesskey=>'d',-title=>'datalyzer'},'datalyzer').br;
		$res .= br. a({-href=>"/tools/cataflag/$comic"},"reload") .br. a({-href=>"/front/$comic"},"Frontpage").br. a({-href=>"/"},"Index");
		$res .= end_div.end_html;;
		return $res;	
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
		my $strips = $dbh->selectall_hashref("SELECT * FROM _$comic","id");
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
		$res .= start_div({-class=>'tools'});
		my $sec = param('section') ;
		
		if ($sec and ($sec eq 'strps')) {
			$res .= table(Tr([map {td([ #creating table with key : value pairs via map
					#if it is prev or next make it a link; else just print out the information
					$_,":",	($_ =~ m/prev|next/)	?	a({href=>"/tools/datalyzer/$comic?section=strps&strip=".$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					#make links klickable
					($_ =~ m/url/)	?	 a({href=>$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					$d{$sec}->{param('strip')}->{$_}
					])} grep {$_ ne 'n'} keys %{$d{$sec}->{param('strip')}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools/datalyzer/$comic"},"Back")
		}
		elsif ($sec) {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools/datalyzer/$comic?section=strps&strip=" . $d{$sec}->{$_}},$d{$sec}->{$_})
					])} grep {$_ ne 'n'} keys %{$d{$sec}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools/datalyzer/$comic"},"Back")
		}
		else {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools/datalyzer/$comic?section=$_"},$_) , ':' , $d{$_}->{n}
					])} grep {$_ ne 'strps'} keys %d]));	#getting all keys 
		}
		return $res .= br . a({-href=>"/"},"Index") . end_div.end_html;
	}

	
=head2 Database View

here you can view the comics table directly. this is just for debugging purposes.

=cut

	if ($tool eq 'comics') {
		my $res = &kopf('comics');
		$res .= start_div({-class=>'tools'});
		if ($comic) {
			my $user = $dbh->selectrow_hashref("SELECT * FROM comics WHERE comic = ?",undef,$comic);
			$res .= start_table;
			foreach my $key (keys %{$user}) {
				$res .=  Tr(td("$key"),td(textfield(-name=>$key, -default=>dbcmcs($comic,$key), -size=>"100")));
			}
			$res .= end_table . br . br. a({-href=>"/tools/strips/$comic"},"strips") . br.br;
			$res .= a({-href=>"/tools/comics/$comic"},"reload") .br.a({-href=>"/tools/cataflag/$comic"},"back")  . br ;
			$res .= a({-href=>"/"},"Index") . end_html;
			return $res;
		}
		my $user = $dbh->selectall_hashref("SELECT * FROM comics","comic");
		$res .= start_table;
		my $h = 0;
		foreach my $cmc (sort{uc($a) cmp uc($b)} (keys %{$user})) {

			$res .=  Tr(td('name'),td([keys %{$user->{$cmc}}])) if !$h;
			$h = 1;
			$res .=  Tr(td(a({-href=>"/tools/comics/$cmc"},$cmc)),td([map {textfield(-name=>$_, -default=>dbcmcs($cmc,$_))} keys %{$user->{$cmc}}]));
		}
		return $res . end_table . br . br .  a({-href=>"/"},"Index") . end_div.end_html;
	}
	
	if ($tool eq 'strips') {
		my $res = &kopf('strips');
		#$res .= start_div({-class=>'tools'});
		if ($comic) {
			my $user = $dbh->selectall_hashref("SELECT * FROM _$comic",'id');
			$res .= start_table;
			my $h = 0;
			foreach my $key (keys %{$user}) {
				$res .=  Tr(td([keys %{$user->{$key}}])) if !$h;
				$h = 1;
				$res .=  Tr(td([map {textfield(-name=>$_, -default=>$user->{$key}->{$_})} keys %{$user->{$key}}]));
			}
			return $res . end_table . br . br .a({-href=>"/tools/strips/$comic"},"reload") .br.a({-href=>"/tools/comics/$comic"},"back")  . br . a({-href=>"/"},"Index") . end_html;
		}
	}
	
	
=head2 Custom Query

input a sqlite query which will be executed in the longer input field.
if you input a column in the second field, the output becomes more readable

=cut
	
	if ($tool eq 'query') {
		my $res = &kopf('Query');
		$res .= start_div({-class=>'tools'});
		if (param('query')) {
			if (param('hashkey')) {
				$res .= pre(Dumper($dbh->selectall_hashref(param('query'),param('hashkey'))));
			}
			else {
				$res .= pre(Dumper($dbh->selectall_arrayref(param('query'))));
			}
			return $res . br . br . a({-href=>"/tools/query"},"Back") . br .  a({-href=>"/"},"Index") . end_div.end_html;
		}
		$res .= start_form("GET","/tools/query");
		$res .= 'enter sql query string here'.br;
		$res .= textarea(-name=>"query", -rows=>4,-columns=>80) . br.br;
		$res .= 'select hash key'.br;
		$res .= textfield(-name=>"hashkey", -size=>"20");
		$res .= br . submit('ok');
		return $res . br . br .  a({-href=>"/"},"Index") . end_div.end_html;
	}
	
=head2 Random

get a random comic frontpage. you dont get comics that you are reading, have completed or stopped. no doubles in one session.
 you can reload the page to get a new random comic .. and another one ... and another one ... have .. to .. stop .. reloading ...

=cut
	
	if ($tool eq 'random') {
		my $firsts = $dbh->selectall_hashref('SELECT comic,first FROM comics WHERE (flags NOT LIKE "%r%" AND flags NOT LIKE "%f%" AND flags NOT LIKE "%s%") OR flags IS NULL' , 'comic');
		my @comics = keys %{$firsts};
		my $comic;
		while($comic = splice(@comics,rand(int @comics),1)) {
			next if $broken{$comic};
			next if $rand_seen{$comic};
			$rand_seen{$comic} = 1;
			return cfront($comic);
		}
		undef %rand_seen;
	}
	
	
=head2 Filter (disabled)

the first field is the name of the filter (use alphanumeric without spaces for peace of mind) 

the second field is a sqlite expression. if it returns true the comic is displayed with this filter. 
it works somewhat like this:

	SELECT comic FROM USER WHERE your_expression_here

you can use all the fields of user to filter from. try this to get only comics with mor than 500 strips:

	strip_count > 500
	
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
		$res .= start_form("GET","/tools/filter");
		$res .= start_table;
		foreach my $filter (&filter) {
			if (defined param($filter) and param($filter) ne '') {
				filter($filter,param($filter));
			}
			$res .=  Tr(td($filter),td(filter($filter),td(a({-href=>"/tools/filter&delete=$filter"},'delete'))));
		}
		$res .=  Tr(td(textfield(-name=>"new_filter_name", -size=>"20")),td(textfield(-name=>"new_filter", -size=>"100")));
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools/filter"},"reload") .br. a({-href=>"/"},"Index") . end_html;
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
	given (param("file") // '') {
		when(/comic3/) {$path = "comic3.pl"}
		when(/comic/) {$path = "lib/comic.pm"}
		when(/httpserver/) {$path = "httpserver.pl"}
		when(/page/) {$path = "lib/page.pm"}
		when(/dbutil/) {$path = "lib/dbutil.pm"}
		when(/dlutil/) {$path = "lib/dlutil.pm"}
		when(/strip/) {$path = "lib/Strip.pm"}
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

sub update { #TODO! is it even neccessary?
	my $ttu = Time::HiRes::time;
	#dbutil::check_table($dbh,"comics");
	#dbutil::check_table($dbh,"CONFIG");
	#$dbh->do("UPDATE USER set first = NULL , server_update = NULL where first like 'dummy%'");TODO?
	
	# foreach my $comic (@{$dbh->selectcol_arrayref(qq(select comic from USER where first IS NULL))}) {
		# my @first = @{$dbh->selectcol_arrayref("select strip from _$comic where prev IS NULL and next IS NOT NULL")};
		# next if (@first == 0); 
		# @first = grep {$_ !~ /^dummy/} @first if (@first > 1);
		# usr($comic,'first',$first[0]);
	# }
	my @comics = @{$dbh->selectcol_arrayref(qq(SELECT comic,server_update - last_save AS time FROM comics WHERE (time <= 0) OR (server_update IS NULL)))};
	local $| = 1;
	$dbh->{AutoCommit} = 0;
	print "updating ". scalar(@comics) ." comics:\n" if @comics;
	foreach my $comic (@comics) {
		
		#dbutil::check_table($dbh,"_$comic");
		
		dbcmcs($comic,'server_update',time);
		
		#my @dummys = $dbh->selectrow_array("SELECT strip FROM _$comic WHERE (strip like 'dummy%') and ((prev IS NULL) or (next IS NULL))");
		#$dbh->do("DELETE FROM _$comic where (strip like 'dummy%') and ((prev IS NULL) or (next IS NULL))");
		#foreach my $dummy (@dummys) {
		#	$dbh->do("update _$comic set next = NULL where next = '$dummy'");
		#	$dbh->do("update _$comic set prev = NULL where prev = '$dummy'");
		#}
		
		if ($dbh->selectrow_array("SELECT COUNT(next) AS dup_count FROM _$comic WHERE next > 0 GROUP BY next HAVING (COUNT(next) > 1)")
		or  $dbh->selectrow_array("SELECT COUNT(prev) AS dup_count FROM _$comic WHERE prev > 0 GROUP BY prev HAVING (COUNT(prev) > 1)")) {
			&flags($comic,'+w');
		}
		else {
			&flags($comic,'-w');
		}
		
		my $first = dbcmcs($comic,'first');
		unless ($first) {
			my @first = @{$dbh->selectcol_arrayref("SELECT id FROM _$comic WHERE prev IS NULL AND next IS NOT NULL")};
			next if (@first == 0); 
			@first = grep {$_ !~ /^dummy/} @first if (@first > 1);
			dbcmcs($comic,'first',$first[0]);
			$first = $first[0];
		}
		
		#usr($comic,'strip_count',$dbh->selectrow_array(qq(select count(*) from _$comic)));
		
		my %double;
		my $strp = $first;
		my $strps = {};
		$strps = $dbh->selectall_hashref(qq(SELECT id , next, number FROM _$comic), "id");
		
		my $i = 0;
		my $prevstrip;
		if ($strp) {
			$i++ ;
			dbstrps($comic,'id'=>$strp,'number',$i) unless ($strps->{$strp}->{number} and $strps->{$strp}->{number} == $i);
			while((defined $strps->{$strp}->{next}) and $strps->{$strps->{$strp}->{next}}) {
				$prevstrip = $strp;
				$strp = $strps->{$strp}->{next};
				if ($double{$strp}) {
					$strp = $prevstrip;
					last;
				}
				$double{$strp} = 1;
				$i++;
				dbstrps($comic,'id'=>$strp,'number',$i) unless ($strps->{$strp}->{number} and $strps->{$strp}->{number} == $i);
			}
		}
		dbcmcs($comic,'last',$strp);
		#dbcmcs($comic,'strip_count',$i);
		print ".";
	}
	print "\nupdating: ". (Time::HiRes::time - $ttu) . " seconds\ncommiting: " if @comics;
	my $ttc = Time::HiRes::time;
	$dbh->{AutoCommit} = 1;
	say "" . (Time::HiRes::time - $ttc) . " seconds" if @comics;
}



sub comics {
	#return @{$comics->{__SECTIONS__}};
	return @{$dbh->selectcol_arrayref("SELECT comic FROM comics")};
}

=head2 config usr dat

C<config(comic,key,value,delet value?)> accesses the table B<CONFIG>

C<usr(comic,key,value,delet value?))> accesses B<USER>

C<dat(comic,strip,key,value,delet value?))> access the table B<_I<comicname>>

usr and dat use caching, so asking them for the same value over and over again should be quiete fast

=cut

sub config {  #todo
	my ($key,$value) = @_;
	return unless $key;
	say join(':',caller) . ' called non supportet routine config TODO';
	return;
	return $dbh->selectrow_array("SELECT $key FROM config");
}

sub dbcmcs { #gibt die aktuellen einstellungen des comics aus # hier gehören die veränderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
	my ($c,$key,$value) = @_;
	return unless $c and $key;
	if (defined $value and $value ne '') {
		$dbh->do("UPDATE comics SET $key = ? WHERE comic = ?",undef,$value,$c);
		%strpscache = ();
	}
	if ($strpscache{comic} and ($c eq $strpscache{comic})) {
		return $strpscache{$key};
	}
	return $dbh->selectrow_array("SELECT $key FROM comics WHERE comic = ?",undef,$c);
	
}

sub dbstrps { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu dem comic und seinen srips gespeichert
	my ($c,$get,$key,$select,$value) = @_;
	return unless $c and $get and $key;
	if (defined $value) {
		$dbh->do("UPDATE _$c SET $select = ? WHERE $get = ?",undef,$value,$key);
		%strpscache = ();
	}
	if ($strpscache{comic} and ($c eq $strpscache{comic}) and ($get eq 'id')) {
		return $strpscache{$key}->{$select};
	}
	return $dbh->selectrow_array("SELECT $select FROM _$c WHERE $get = ?",undef,$key);
}

sub cache_strps {
	my ($comic) = @_;
	return if $strpscache{comic} and ($comic eq $strpscache{comic});
	say "caching $comic";
	%strpscache = %{$dbh->selectall_hashref("SELECT * FROM _$comic",'id')};
	my $cmc = $dbh->selectrow_hashref("SELECT * FROM comics WHERE comic = ?",undef,$comic);
	$strpscache{$_} = $cmc->{$_} for keys %$cmc; 

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
	
	if ($comic) {
		if ($new eq '<>') {
			#usr($comic,'itags',0,'delete');
			#dbcmcs($comic,'tags',0,'delete') if !$import;
			$dbh->do('UPDATE comics SET tags = NULL where comic = ?',undef,$comic );
			return 1;
		}
		
		my $otags = dbcmcs($comic,'tags') // '';
		
		my $otag = {};
		
		$otag->{lc $_} = 1 for(split(/\W+/,$otags));
		
		
		if ($new) {
			if ($new =~ /^\+([\w\s]+)/) {
				for (split(/\W+/,$1)) {
					$otag->{lc $_} = 1;
				}
			}	
			elsif ($new =~ /^-([\w\s]+)/) {
				for (split(/\W+/,$1)) {
					delete $otag->{lc $_};
				}
			}
			elsif($new =~ /^([\w\s]+)$/) {
				$otag = {};	#we delete all of our tags
				$otag->{lc $_} = 1 for (split(/\W+/,$new)); #and recreate with the given ones

			}
			my $ot = join(' ',keys %{$otag});
			if ($ot) { 
				dbcmcs($comic,'tags',$ot) ;
			}
			else {
				$dbh->do('UPDATE comics SET tags = NULL where comic = ?',undef,$comic )
			}

		}
		return keys %$otag;
	}
	else {
		my $tags = $dbh->selectcol_arrayref("SELECT tags FROM comics");
		my %taglist;
		foreach (@{$tags}) {
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
	return 0 unless $comic;
	
	if ($new eq '<>') {
		$dbh->do('UPDATE comics SET flags = NULL where comic = ?',undef,$comic );
		return 1;
	}
	
	my $oflags = dbcmcs($comic,'flags') // '';
	
	my $oflag = {};
	
	$oflag->{$_} = 1 for(split(//,$oflags));
	
	if ($new) {
		if ($new =~ /^\+(\w+)/) {
			for (split(//,$1)) {
				$oflag->{$_} = 1;
			}		
		}	
		elsif ($new =~ /^-(\w+)/) {
			for (split(//,$1)) {
				delete $oflag->{$_};
			}		
		}
		elsif ($new =~ /^(\w+)$/) {
			$oflag = {};	#we delete all of our flags
			$oflag->{$_} = 1 for (split(//,$new)); #and recreate with the given ones
		}
		my $of = join('',keys %$oflag);
		
		if ($of) {
			dbcmcs($comic,'flags',$of) 
		}
		else {
			$dbh->do('UPDATE comics SET flags = NULL where comic = ?',undef,$comic);
		}

	}
	return $oflag;
}

=head2 filter

C<filter(filter name,new filter)>

without parameters returns all filter names

with name returns that filter

with name and new, sets filter (deletes if new is defined but not true)

=cut

sub filter {
	say join(':',caller) . ' called non supportet routine FILTER  TODO';
	return '';
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