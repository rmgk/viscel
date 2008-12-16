#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#17:15 06.10.2008


use 5.010;
use strict;
use warnings;
use lib "./lib";

use HTTP::Daemon;
use HTTP::Status;
use CGI qw(:standard *table :html3);
use DBI;
use Data::Dumper;
use Time::HiRes;
use dbutil;


use vars qw($VERSION);
$VERSION = '2.39';

my $d = HTTP::Daemon->new(LocalPort => 80);
die "could not listen on port 80 - someones listening there already?" unless $d;

my $res = HTTP::Response->new( 200, 'success', ['Content-Type','text/html; charset=iso-8859-1']);
my $rescss = HTTP::Response->new( 200, 'success', ['Content-Type','text/css; charset=iso-8859-1']);
my %index;
my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout');
my %broken;
my %rand_seen;
my @db_cache = ('','','','');
my $measure_time = 0;


my $def_css;
{
	local $/ = undef;
	open(CSS,"<default.css");
	$def_css =  <CSS>;
	close(CSS);
	if (-e "overwrite.css") {
		$def_css .= "\n";
		open(CSS2,"<overwrite.css") ;
		$def_css .= <CSS2>;
		close(CSS2);
	}

}



&update;

#print "Please contact me at: <URL:", $d->url,">\n";
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
			elsif ($r->url->path =~ m#^/style\.css$#) {
					$rescss->content(&ccss);
					$c->send_response($rescss);
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
			say $r->url->path . '?' . ($r->url->query // '') . " " . (Time::HiRes::time - $req_start_time) if $measure_time;
			$measure_time = param('measure_time') if defined param('measure_time');
		}
		$c->close;
		#$dbh->commit;
	}
	undef($c);
}




sub comics {
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
		push(@kat,$_) unless (!$_ or $d{$_});
	}
	return @kat;
}

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
		my $tags = (($tagsref->[0]//'').' '.($tagsref->[1]//''));
		my $tag = {};
		
		$tag->{lc $_} = 1 for(split(/\W+/,$tags));
		
		if ($new) {
			if ($new =~ /^\+([\w\s]+)/) {
				$tag->{lc $_} = 1 for (split(/\W+/,$1));
			}	
			elsif ($new =~ /^-([\w\s]+)/) {
				delete $tag->{lc $_} for (split(/\W+/,$1));		
			}
			elsif($new =~ /^([\w\s]+)$/) {
				$tag = {};
				$tag->{lc $_} = 1 for (split(/\W+/,$new));		
			}
			my $t = join(' ',keys %{$tag});
			if ($import) {
				$t ? usr($comic,'itags',$t) : usr($comic,'itags',0,'delete');
			}
			else {
				$t ? usr($comic,'tags',$t) : usr($comic,'tags',0,'delete');
				usr($comic,'itags',0,'delete');
			}
		}
		return keys %{$tag};
	}
	else {
		my $tags = $dbh->selectcol_arrayref("select tags from USER");
		my $itags = $dbh->selectcol_arrayref("select tags from USER");
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
	my $flags = (($flagref->[0]//'').($flagref->[1]//''));
	if ($flags =~ /^\d+$/) {
		my @flaglist = qw(read complete hiatus warn loop);
		my @flag_codes = split(//,$flags);
		$flags = "";
		$flags .= "r" if $flag_codes[0];
		$flags .= "c" if $flag_codes[1];
	}
	
	my $flag = {};
	
	$flag->{$_} = 1 for(split(//,$flags));
	
	if ($new) {
		if ($new =~ /^\+(\w+)/) {
			$flag->{$_} = 1 for (split(//,$1));
		}	
		elsif ($new =~ /^-(\w+)/) {
			delete $flag->{$_} for (split(//,$1));		
		}
		elsif ($new =~ /^(\w+)$/) {
			$flag = {};
			$flag->{$_} = 1 for (split(//,$new));		
		}
		my $f = join('',keys %{$flag});
		if ($import) {
			$f ? usr($comic,'iflags',$f) : usr($comic,'iflags',0,'delete');
		}
		else {
			$f ? usr($comic,'flags',$f) : usr($comic,'flags',0,'delete');
			usr($comic,'iflags',0,'delete');
		}
	}
	return $flag;
}

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

sub kopf {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	
	return start_html(-title=>$title. " - ComCol http $VERSION" ,
							-head=>[
									Link({-rel=>"stylesheet", -type=>"text/css", -href=>"/style.css"}),
									Link({-rel=>'index',	-href=>"/"	})			,
                            $next ?	Link({-rel=>'next',		-href=>$next})	: undef	,
                            $prev ?	Link({-rel=>'previous',	-href=>$prev})	: undef	,
							$first?	Link({-rel=>'first',	-href=>$first})	: undef	,
							$last ?	Link({-rel=>'last',		-href=>$last})	: undef	,
									]);
}

sub preview_head {
	return q(
<div id="prevbox" style="visibility:hidden;position:fixed;right:0;bottom:0;"> </div>
<script type="text/javascript">
var preview = document.getElementById("prevbox");
function showImg (imgSrc) {
	preview.innerHTML = "<img id='prevIMG' src='"+imgSrc+"' alt='' />";
	preview.style.visibility='visible';
}
</script>
);
}

sub cindex {
	my $ret = &kopf("Index",0,"/tools?tool=random");
	my $i = 0;
	my @tag = param('tag');
	$ret .= div({-id=>"menu"},
		"Tools:" . br 
			#.	a({-href=>"/tools?tool=config",-accesskey=>'c',-title=>'config'},"Configuration") . br 
			.	a({-href=>"/tools?tool=user",-accesskey=>'u',-title=>'user'},"User Config"). br 
			.	a({-href=>"/tools?tool=query",-accesskey=>'q',-title=>'query'},"Custom Query"). br 
			.	a({-href=>"/tools?tool=filter",-accesskey=>'f',-title=>'filter'},"Custom Contents"). br 
			.	a({-href=>"/tools?tool=random",-accesskey=>'r',-title=>'random'},"Random Comic"). br 
			.	br .
		"Contents:" . br .
		join("",map { a({href=>"#$_",-accesskey=>$i++,-title=>$_},$_) . br} (qw(continue other finished stopped),filter))
		. br. "Color Scale:" .br.
		 join("",map {div({-style=>"color:#".colorGradient(log($_),10)},$_ )} (10000,5000,2000,1000,500,200,100,50,10,1))
		 .br . "Tags:" .br.
		 a({-href=>"/"},'Any Tag') . br .
		 join("",map {a({-href=>"/?tag=$_".join("",map {"&tag=$_"} (@tag)) },$_) . br} (tags()))
		);	
	
	$ret .= &preview_head();
	my $cmcs = $dbh->selectall_hashref("select * from user",'comic');
	my $tagcheck = '1';
	$tagcheck = join(' and ',map {"tags like '%$_%'"} (@tag)) if @tag;
	$ret .= html_comic_listing('continue',$cmcs,qq{flags like '%r%' and flags not like '%f%' and flags not like '%s%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('other',$cmcs,qq{((flags not like '%r%' and flags not like '%f%' and flags not like '%s%') or flags is null) and ($tagcheck) }).br;
	$ret .= html_comic_listing('finished',$cmcs,qq{flags like '%f%' and ($tagcheck)}).br;
	$ret .= html_comic_listing('stopped',$cmcs,qq{flags like '%s%' and ($tagcheck)}).br;
	foreach (filter) {
		$ret .= html_comic_listing($_,$cmcs, '(' . filter($_) .") and ($tagcheck)").br;
	}
	return $ret . end_html;
}

sub html_comic_listing {
	my $name = shift;
	my $user = shift;
	my $filter = shift;
	my $comics = $dbh->selectcol_arrayref(qq{select comic from USER where ($filter)});
	my $ret = h1({-class=>'group'},a({name=>$name},$name));
	$ret .= start_table({-class=>"group"});
	
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
		
		$ret .= Tr([
			td([
			a({-href=>"/front?comic=$comic",-class=>($broken{$comic}?'broken':'comic'),-style=>"color:#". colorGradient($mul,10) .";font-size:".(($mul/40)+0.875)."em;"},$comic) ,
			$usr->{'first'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'first'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'first'}."')"},"|&lt;&lt;") : undef ,
			$usr->{'aktuell'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'aktuell'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'aktuell'}."')"},"&gt;&gt;") : undef ,
			$usr->{'bookmark'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'bookmark'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'bookmark'}."')"},"||") : undef ,
			($usr->{'aktuell'} and $usr->{'last'} and ($usr->{'aktuell'} eq $usr->{'last'})) ? "&gt;&gt;|" : $usr->{'last'} ? a({-href=>"/comics?comic=$comic&strip=".$usr->{'last'},-onmouseout=>"preview.style.visibility='hidden';",-onmouseover=>"showImg('/strips/$comic/".$usr->{'last'}."')"},"&gt;&gt;|") : undef ,
			param('toread')? $toRead{$comic} :undef, 
			param('count')?$usr->{'strip_count'}:undef,
			param('counted')?$usr->{'strips_counted'}:undef,
			])
		]);
	}
	return $ret . end_table;
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
		my $g = sprintf "%x", (1-$col) * 255 * 2;
		return "ff${g}00";
	}
	return "ffffff";
}


sub cfront {
	my $comic = param('comic') // shift;
	my $random = shift;
	my $ret = &kopf($comic . " Frontpage",0,0,
					&usr($comic,'first') ?"/comics?comic=$comic&strip=".&usr($comic,'first') :"0",
					&usr($comic,'last' ) ?"/comics?comic=$comic&strip=".&usr($comic,'last' ) :"0",
					);
	$ret .= div({-class=>'frontpage'},
				h2($comic),
				&usr($comic,'aktuell')?(
					a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first'),-accesskey=>'f',-title=>'first strip'},
						img({-class=>"front3",-id=>'first',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"})) ,
					a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last'),-accesskey=>'l',-title=>'last strip'},
						img({-class=>"front3",-id=>'last',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"})),
					a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'aktuell'),-accesskey=>'n',-title=>'current strip'},
						img({-class=>"front3",-id=>'current',-src=>"/strips/$comic/".&usr($comic,'aktuell'),-alt=>"current"}))  ,
					):(
					a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'first'),-accesskey=>'f',-title=>'first strip'},
						img({-class=>"front2",-id=>'first',-src=>"/strips/$comic/".&usr($comic,'first'),-alt=>"first"})) ,
					a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'last'),-accesskey=>'l',-title=>'last strip'},
						img({-class=>"front2",-id=>'last',-src=>"/strips/$comic/".&usr($comic,'last'),-alt=>"last"}))
					)
				,
				br,br,br,
				&usr($comic,'bookmark')?a({-href=>"/comics?comic=$comic&strip=".&usr($comic,'bookmark'),-accesskey=>'b',-title=>'paused strip',
				-onmouseover=>"document.getElementById('current').src='/strips/$comic/".&usr($comic,'bookmark')."'",
				-onmouseout =>"document.getElementById('current').src='/strips/$comic/".&usr($comic,'aktuell')."'"
				},'bookmark') . br : undef,
				a({-href=>"/",-accesskey=>'i',-title=>'Index'},"Index"),
				a({-href=>"/comics?comic=$comic",-accesskey=>'s',-title=>'striplist'},"Striplist"),
				a({href=>"/tools?tool=cataflag&comic=$comic",-accesskey=>'c',-title=>'categorize'},'Categorize'),
				br,
				usr($comic,'strip_count'),usr($comic,'strips_counted'),
				a({href=>"/tools?tool=datalyzer&comic=$comic",-accesskey=>'d',-title=>'datalyzer'},'datalyzer'),
				a({href=>"/tools?tool=user&comic=$comic",-accesskey=>'u',-title=>'user'},'user'),
				$broken{$comic} ? "broken" : undef ,br,
				"tags: " ,tags($comic) ,
				$random? br. a({-href=>"/tools?tool=random",-accesskey=>'r',-title=>'random strip'},"Random"):undef
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
		my @titles = split(' !§! ',$title);
		$title =~ s/-§-//g;
		$title =~ s/!§!/|/g;
		$title =~ s/~§~/~/g;
		
		$strip =~ s/%7C/|/ig;
		$strip =~ s/ /%20/ig;
		
		$return = &kopf($title,
					&dat($comic,$strip,'prev')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev')	:"0",
					&dat($comic,$strip,'next')	?"/comics?comic=$comic&strip=".&dat($comic,$strip,'next')	:"0",
					&usr($comic,'first')		?"/comics?comic=$comic&strip=".&usr($comic,'first')			:"0",
					&usr($comic,'last')			?"/comics?comic=$comic&strip=".&usr($comic,'last')			:"0",
					);
		$return .= div({-class=>"comic"},
				h3($title),
				(-e "./strips/$comic/$strip") ? 
					img({-src=>"/strips/$comic/$strip",-title=>($titles[2]//''),-alt=>($titles[3]//'')}) :
					$strip!~m/^dummy/ ? 
						img({-src=>&dat($comic,$strip,'surl'),-title=>($titles[2]//''),-alt=>($titles[3]//'')}).br.
						a({-href=>"/tools?tool=download&comic=$comic&strip=$strip"},"(download)") :
						"This page is a dummy. Errors are likely",
				br,
				br,
				&dat($comic,$strip,'prev')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'prev'),-accesskey=>'v',-title=>'previous strip'},'&lt;&lt;'):undef,
				&dat($comic,$strip,'next')?a({-href=>"/comics?comic=$comic&strip=".&dat($comic,$strip,'next'),-accesskey=>'n',-title=>'next strip'},'&gt;&gt;'):undef,
				br,
				&dat($comic,$strip,'next')?a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=r",-accesskey=>'d',-title=>'pause reading this comic'},"pause"):
				flags($comic)->{c} ? a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=rcf",-accesskey=>'d',-title=>'finish reading this comic'},"finish"):
				a({-href=>"/tools?tool=cataflag&comic=$comic&bookmark=$strip&addflag=r",-accesskey=>'d',-title=>'pause reading this comic'},"pause")
				,
				a({-href=>"/front?comic=$comic",-accesskey=>'f',-title=>'frontpage'},"front"),
				&dat($comic,$strip,'url')?a({-href=>&dat($comic,$strip,'url'),-accesskey=>'s',-title=>'original strip site'},"site"):undef,
				
				);
		&usr($comic,'aktuell',$strip) unless ($random or $strip=~m/^dummy/);
		&usr($comic,'bookmark',$strip) if param('bookmark');
		return $return . end_html;
	}
	else {
		unless ($index{$comic}) {
			my %double;
			my $dat = $dbh->selectall_hashref(qq(select strip,next,title from _$comic),"strip");
			
			my $strip = &usr($comic,'first');
			$index{$comic} = &kopf($comic);
			$index{$comic} .= preview_head;
			
			my $i;
			while ($strip and $dat->{$strip}->{'strip'}) {
				if ($double{$strip}) {
					print "loop gefunden, breche ab\n" ;
					last;
				}
				$double{$strip} = 1;

				$i++;
				my $title = $dat->{$strip}->{'title'} // '';
				$title =~ s/-§-//g;
				$title =~ s/!§!/|/g;
				$title =~ s/~§~/~/g;
				$index{$comic} .= div({-class=>"striplist"},
				img({-src=>"/strips/$comic/$strip"}) .
				a({-href=>"./comics?comic=$comic&strip=$strip",
				-onmouseout=>"preview.style.visibility='hidden';",
				-onmouseover=>"showImg('/strips/$comic/$strip')",
				}, "$i : $strip : $title").br
				);
				$strip = $dat->{$strip}->{'next'};
			}
			$index{$comic} .= end_html;
		}
		return $index{$comic};
	}
}


sub ctools {
	my $tool = param('tool');
	my $comic = param('comic');
	
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
		if (defined param('delete') and param('delete') ne '') {
			&config(param('delete'),0,'delete');
		}
		foreach my $key (keys %{$config}) {
			if (defined param($key) and param($key) ne '') {
				&config($key,param($key));
			}
			$res .=  Tr(td("$key"),td(textfield(-name=>$key, -default=>&config($key), -size=>"100")),td(a({-href=>"/tools?tool=config&delete=$key"},"delete $key")));
		}
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools?tool=config"},"reload") .br. a({-href=>"/"},"Index") . end_html;
	}
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
		foreach my $filter (filter) {
			if (defined param($filter) and param($filter) ne '') {
				filter($filter,param($filter));
			}
			$res .=  Tr(td($filter),td(filter($filter),td(a({-href=>"/tools?tool=filter&delete=$filter"},'delete'))));
		}
		$res .=  Tr(td(textfield(-name=>"new_filter_name", -size=>"20")),td(textfield(-name=>"new_filter", -size=>"100")));
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools?tool=filter"},"reload") .br. a({-href=>"/"},"Index") . end_html;
	}
}


sub update {
	my $ttu = Time::HiRes::time;
	dbutil::check_table($dbh,"USER");
	dbutil::check_table($dbh,"CONFIG");
	$dbh->do("UPDATE USER set first = NULL , server_update = NULL where first like 'dummy|%'");
	
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
		
		my @dummys = $dbh->selectrow_array("select strip from _$comic where (strip like 'dummy|%') and ((prev IS NULL) or (next IS NULL))");
		$dbh->do("DELETE FROM _$comic where (strip like 'dummy|%') and ((prev IS NULL) or (next IS NULL))");
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
			dat($comic,$strp,'number',$i);
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

sub ccss {

my $c_bg 	= &config('color_bg') || 'black';
my $c_text 	= &config('color_text') || '#009900';
my $c_link 	= &config('color_link') || '#0050cc';
my $c_vlink = &config('color_vlink') || '#900090';

my $css = '';
$css = eval('"'.$def_css.'"');

return $css;
}