#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#18:47 03.10.2008

package Page;

use 5.010;
use strict;
use warnings;

=head1 NAME

Comic.pm 

=head1 DESCRIPTION

This package is used to navigate inside the comic and mange the list of pages.

=cut

use dlutil;

use DBI;
use URI;
$URI::ABS_REMOTE_LEADING_DOTS = 1;

use Digest::MD5 qw(md5_hex);
use Digest::SHA qw(sha1_hex);
use Time::HiRes;


our $VERSION;
$VERSION = '30';

=head1 general Methods

=head2 new

	Page->new($hashref);

I<$hashref> needs the following keys:

=over 4

=item * C<cmc> - the comic object. required

=item * C<url> - the url of the page. required

=back

returns: the newly blessed page object

=cut

sub new {
	my $class = shift;
	my $s = shift;
	bless $s,$class;
	$s->status("NEW PAGE: ".$s->url,'DEBUG');
	return $s;
}

=head2 all_strips

	Page->all_strips();

finds L</title> for L</strips> and L<save>s them. L<index_all> afterwards

returns: C<1> if successful, undefined if L<save> threw an error 

=cut

sub all_strips {
	my $s = shift;
	return 0 unless $s->body;
	foreach my $strip (@{$s->strips}) {
		$s->title($strip);
		return unless $s->save($strip); #beim speichern wurde ein kritischer fehler gefunden
	}
	$s->index_all();
	return 1;
}

=head2 index_all

	Page->index_all();

if there are multiple strips on the page links them with each other

returns: nothing (useful)

=cut

sub index_all {
	my $s = shift;
	my $n = $#{$s->strips};
	for (0..($n-1)) {
		$s->dat($s->file($_),'next',$s->file($_+1));
		$s->dat($s->file($_+1),'prev',$s->file($_));
	}
}

=head2 save

	Page->save($strip);

C<$strip> is the url of the file to download. required

L<get_file_name> and returns 0 if the file name was already seen in the seession. also deletes C<url_current>
checks if the file already exists if not L<_save>s the file

returns: -1 if there are no files on the page. 0 if the file_name is duplicated in the session, 1 if the file (name) exists on disk,
0 if the download threw an error and 1 if the download was successful

=cut

sub save {
	my $s = shift;
	my $strip = shift;
	return -1 if ($s->dummy);
	my $file_name = $s->get_file_name($strip);
	if ($s->cmc->{seen_file_names}->{$file_name}) {
		$s->status("ERROR: file name '$file_name' already seen in this session",'ERR');
		$s->usr("url_current",0,1); #delete url current
		return 0;
	}
	$s->cmc->{seen_file_names}->{$file_name} = 1;
	if  (-e "./strips/".$s->name."/$file_name") {
		$s->status("EXISTS: ".$file_name,'UINFO');
		return 1;
	}
	else {
		my $home = $s->cmc->url_home();
		$s->url =~ m#(?:$home)?(.+)#;
		my $se_url = $1;
		$strip =~ m#(?:$home)?(.+)#;
		my $se_strip = $1;
		local $| = 1; #dont wait for newline to print the text
		print "GET: " . $se_url . " => " . $file_name;
		$s->status("GET: $file_name URL: " . $s->url ." SURL: " .$strip,"DEBUG");
		return $s->_save($strip,$file_name);
	}
}

=head2 _save

	Page->_save($surl,$file_name);

C<$surl> is the url of the file to download. required
C<$file_name> is the name the file will be named on disk. required

downloads the file with L<dlutil/getref>
creates md5 and sha1 hashes and saves the file to disk

returns: 0 if the download threw an error and 1 if the download was successful

=cut

sub _save {
	my ($s,$surl,$file_name) = @_;
	my $u_referer = $s->cfg('referer');
	my $time = Time::HiRes::time;
	my $img = dlutil::getref($surl,$u_referer);
	$time = Time::HiRes::time - $time;
	if ($img =~ m/^\d+$/) {
		$s->status("ERROR downloading $file_name code: $img","ERR");
		return 0;
	}
	open(my $fh,">./strips/".$s->name."/".$file_name);
	binmode $fh;
	print $fh $img;
	say " (".int((-s $fh)/($time*1000)) ." kb/s)";
	close $fh;
	$s->dat($file_name,'md5',md5_hex($img));
	$s->dat($file_name,'sha1',sha1_hex($img));
	$s->usr('last_save',time);
	return 1;
}

=head2 title

	Page->title($surl);

C<$surl> is the url of the strip. required

uses L<get_file_name> and L<url> and L<body> to get information about the comic to store in the database.

returns: the created title string or undef if there is no body

database access: READ cfg, WRITE _comic

=cut

sub title {
	my $s = shift;
	my $surl = shift;
	my $file = $s->get_file_name($surl);
	my $url = $s->url();
	my $body = $s->body();
	return unless $body;
	my ($urlpart) = ($surl =~ m#.*/(.*)#);
	
	my $regex_title = $s->cfg('regex_title');
	my @ut = ($body =~ m#$regex_title#gis) if ($regex_title);
	$body =~ m#<title>([^<]*?)</title>#is;
	my $st = $1;
	
	my $img;
	if ($urlpart) {
		if ($body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
			$img = $1;
		}
	}
	my $it;
	my $ia;
	if ($img) {
		if ($img =~ m#title=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
			$it = $1;
		}
		if ($img =~ m#alt=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
			$ia = $1;
		}
	}
	
	my @h1 = ($body =~ m#<h\d>([^<]*?)</h\d>#gis);
	my @dt = ($body =~ m#<div[^>]+id="comic[^"]*?title"[^>]*>([^<]+?)</div>#gis);
	my $sl;
	if ($body =~ m#<option[^>]+?selected[^>]*>([^<]+)</option>#is) {
		 $sl = $1;
	}
	
	foreach my $one (@ut,$st,$it,$ia,@h1,@dt,$sl) {
		next unless defined $one;
		$one =~ s/"/''/g;
		$one =~ s/\s+/ /g;
	}
	my $ut = ("['" . join("','",@ut) . "']") if @ut;
	my $h1 = ("['" . join("','",@h1) . "']") if @h1;
	my $dt = ("['" . join("','",@dt) . "']") if @dt;
	$ut //= ''; $st //= '';	$it //= '';	$ia //= '';	$h1 //= '';	$dt //= ''; $st //= '';
	my $title_string = "{ut=>q($ut),st=>q($st),it=>q($it),ia=>q($ia),h1=>q($h1),dt=>q($dt),st=>q($st)}";

	$s->cmc->{sqlstr_title_update} //= $s->cmc->dbh->prepare('update _'.$s->name .' set title=?,url=?,surl=?,c_version=?,time=? where strip == ?');
	if($s->cmc->{sqlstr_title_update}->execute($title_string,$s->url,$surl,$main::VERSION,time,$file) < 1) {
		$s->cmc->{sqlstr_title_insert} //= $s->cmc->dbh->prepare('insert into _'.$s->name .' (title,url,surl,c_version,time,strip) values (?,?,?,?,?,?)');
		$s->cmc->{sqlstr_title_insert}->execute($title_string,$s->url,$surl,$main::VERSION,time,$file);
	}
	# $s->dat($file,'title',$title_string);
	# $s->dat($file,'url',$s->url);
	# $s->dat($file,'surl',$surl);
	# $s->dat($file,'c_version',$main::VERSION);
	# $s->dat($file,'time',time);
	
	$s->status("TITEL $file: " . $title_string,'DEBUG');
	return $title_string; 
}

=head1 side url Methods

=cut

{ #side urls wrapper

=head1 url_prev

	$s->url_prev();
	
gets previous url. 

uses I<regex_prev> to get url from L</body> if both are defined.

if not, trys to get them via L<list_side_urls> if I<list_url_regex> is defined.

if that is also not defined it uses L<try_get_side_urls>

checks for I<regex_never_goto>

returns: array of prev urls

database access: READ cfg(3)

=cut

sub url_prev {
	my ($s) = @_;
	my $body = $s->body();
	return unless $body;
	my $urls =[];
	if (my $regex_prev = $s->cfg('regex_prev')) {
		if ($body =~ m#$regex_prev#is) {
			$urls->[0] = $s->concat_url($+{u}//$1);
		}
	}
	elsif ($s->cfg('list_url_regex')) {
		my ($purl,$nurl) = $s->list_side_urls();
		$urls->[0] = $purl;
	}
	else {
		my ($purl,$nurl) = $s->try_get_side_urls();
		$urls = $purl;
	}
	
	my $regex_not_goto = $s->cfg("regex_not_goto");
	my $regex_never_goto = $s->cfg("regex_never_goto");
	my $url_start = $s->cfg("url_start");
	my @ret_urls;
	foreach my $url (@{$urls}) { 
		if 	(	!($url and ($url ne $s->url)) or	#nicht die eigene url
					($s->url eq $url_start) or #nicht von der start url zurück
					($regex_not_goto and ($url =~ m#$regex_not_goto#i)) or
					($regex_never_goto and ($url =~ m#$regex_never_goto#i))
			) {
			next;
		}
		else {
			push (@ret_urls,$url);
		}
	}
	$s->status("PREV URLS: @ret_urls",'DEBUG');
	return @ret_urls;
}

=head1 url_next

	$s->url_next();
	
gets next url. 

uses I<regex_next> to get url from L</body> if both are defined.

if not, trys to get them via L<list_side_urls> if I<list_url_regex> is defined.

if that is also not defined it uses L<try_get_side_urls>

checks for I<regex_never_goto>

returns: array of next urls

database access: READ cfg(3)

=cut

sub url_next {
	my ($s) = @_;
	my $body = $s->body();
	return unless $body;
	my $urls = [];
	if (my $regex_next = $s->cfg('regex_next')) {
		if ($body =~ m#$regex_next#is) {
			$urls->[0] = $s->concat_url($+{u}//$1);
		}
	}
	elsif ($s->cfg('list_url_regex')) {
		my ($purl,$nurl) = $s->list_side_urls();
		$urls->[0] = $nurl;
	}
	else {
		my ($purl,$nurl) = $s->try_get_side_urls();
		$urls = $nurl;
	}
	my $regex_never_goto = $s->cfg("regex_never_goto");
	my $url_start = $s->cfg("url_start");
	my @ret_urls;
	foreach my $url (@{$urls}) { 
		if 	(	!($url and ($url ne $s->url)) or	#nicht die eigene url
				($url eq $url_start) or	#nicht die start url
				($regex_never_goto and ($url =~ m#$regex_never_goto#i))
			) {
			next;
		}
		else {
			push (@ret_urls,$url);
		}
	}
	$s->status("NEXT URLS: @ret_urls",'DEBUG');
	return @ret_urls;
}

=head1 list_side_urls

	$s->list_side_urls();
	
it works. dont ask how.

returns: previous url, next url

database access: READ cfg(4)

=cut

sub list_side_urls {
	my $s = shift;
	my $url = $s->url;
	my $body = $s->body;
	my $url_regex = $s->cfg('list_url_regex');
	my $insert_into = $s->cfg('list_url_insert');
	my $chap_regex = $s->cfg('list_chap_regex');
	my $page_regex = $s->cfg('list_page_regex');
	$url =~ m#$url_regex#i;
	my $chap = $+{chap};
	my $page = $+{page};
	my @chaps;
	while ($body =~ m#$chap_regex#gis) {
		push(@chaps,$+{chaps} // $1) unless ($s->cfg('list_chap_reverse'));
		unshift(@chaps,$+{chaps} // $1) if ($s->cfg('list_chap_reverse'));
	}
	my @pages;
	while ($body =~ m#$page_regex#gis) {
		push(@pages,$+{pages} // $1) unless ($s->cfg('list_page_reverse'));
		unshift(@pages,$+{pages} // $1) if ($s->cfg('list_page_reverse'));
	}
	my $chap_i = undef;
	for (my $i = 0;$i <= $#chaps; $i++) {
		if ($chaps[$i] eq $chap) {
			$chap_i = $i;
			last;
		}
	}	
	my $page_i;
	for (my $i = 0;$i <= $#pages; $i++) {
		if ($pages[$i] eq $page) {
			$page_i = $i;
			last;
		}
	}
	if ( !defined $page_i) {
		my $next = $insert_into;
		my $nc = $chaps[$chap_i + 1];
		if ($nc) {
			$next =~ s/{{chap}}/$nc/egi;
			$next =~ s/{{page}}/$pages[0]/egi;
		}
		else {
			$next = undef;
		}
		return (undef,$next);
	}
	if ($page_i > 0 and $page_i < $#pages) {
		my $prev = $insert_into;
		my $next = $insert_into;
		$prev =~ s/{{chap}}/$chap/egi;
		$next =~ s/{{chap}}/$chap/egi;
		my $pp = $pages[$page_i - 1];
		my $np = $pages[$page_i + 1];
		$prev =~ s/{{page}}/$pp/egi;
		$next =~ s/{{page}}/$np/egi;
		return ($prev,$next);
	}
	if ($page_i == 0) {
		my $next = $insert_into;
		$next =~ s/{{chap}}/$chap/egi;
		my $np = $pages[$page_i + 1];
		$next =~ s/{{page}}/$np/egi;
		return (undef,$next);
	}
	if ($page_i == $#pages) {
		my $next = $insert_into;
		my $prev = $insert_into;
		$prev =~ s/{{chap}}/$chap/egi;
		my $pp = $pages[$page_i - 1];
		$prev =~ s/{{page}}/$pp/egi;
		my $nc = $chaps[$chap_i + 1];
		if ($nc) {
			$next =~ s/{{chap}}/$nc/egi;
			$next =~ s/{{page}}/$pages[0]/egi;
		}
		else {
			$next = undef;
		}
		return ($prev,$next);
	}

}

=head1 try_get_side_urls

	$s->try_get_side_urls();
	
L<concat_url>s the result of L<try_get_side_url_parts>. thus just acting as a wrapper around these functions

returns: arrayref with previous urls, hashref with next next urls

database access: none

=cut

sub try_get_side_urls {
	my $s = shift;
	return (@{$s->{try_get_side_urls}}) if $s->{try_get_side_urls};
	my ($prev,$next) = $s->try_get_side_url_parts();
	my @prev_url = $s->concat_url($prev);
	my @next_url = $s->concat_url($next);
	$s->{try_get_side_urls} = [\@prev_url,\@next_url];
	return (@{$s->{try_get_side_urls}});
}

=head1 try_get_side_url_parts

	$s->try_get_side_url_parts();
	
searches L<body> for prev and next urls. uses a lot of guessing but is quite successful.

returns: previous url, next url

database access: READ cfg

=cut

sub try_get_side_url_parts {
	my $s = shift;
	my $body = $s->body();
	return unless $body;
	my @aref = ($body =~ m#(<a\s+.*?>.*?</a>)#gis); 
	my @filter;
	foreach my $as (@aref) {
		 if ($as =~ m#(prev|next|back|forward|prior|ensuing)#i
			and $as !~m#(preview|be\s*back|background)#i) {
			push(@filter,$as)
		 }
	}
	my @prev;
	my @next;
	my $url_home = $s->cmc->url_home();
	my $regex_never_goto = $s->cfg('regex_never_goto');
	foreach my $fil (@filter) {
		next unless $fil;
		next if ($regex_never_goto) and ($fil =~ m#$regex_never_goto#i);
		my $re_link = qr#<\s*a[^>]*href\s*=\s*((?<p>["'])(?<link>.*?)\k<p>|(?<link>.*?)(\s*>|\s+\w+\s*=))#i;
		if ($fil =~ m#prev|back|prior#i) {
			if ($fil =~ $re_link) {
				my $tmp_url = $+{link};
				next if ((($tmp_url!~m#\.\w{3,4}\?#i) and ($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#i)) or
						(($tmp_url =~ m#http://#i) and !(
							($tmp_url =~ m#$url_home#i))));
				push (@prev, $tmp_url);
			}
		}
		if ($fil =~ m#next|forward|ensuing#i) {
			if ($fil =~ $re_link) {
				my $tmp_url = $+{link};
				next if ((($tmp_url!~m#\.\w{3,4}\?#i) and ($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#i)) or
						(($tmp_url =~ m#http://#i) and !(
							($tmp_url =~ m#$url_home#i))));
				push (@next, $tmp_url);
			}
		}
	}
	return (\@prev,\@next);
}

}

=head1 strip url Methods

=cut

{ #strip url wrapper

=head2 strips

	$s->strips();
	
calls L<strip_urls> to get the urls of the strips, creates the dummy if there is no strip and sets I<dummy> to C<1>

returns: a array with the strip urls

database access: none

=cut

sub strips {
	my $s = shift;
	$s->{strips} = $s->strip_urls() unless $s->{strips};
	unless ($s->{strips}->[0]) {
		$s->{strips}->[0] =  "dummy_" . time . "_" . int rand 1e10;
		$s->{dummy} = 1;
		$s->status("KEINE STRIPS: ".$s->url,'WARN')
	}
	return $s->{strips};
}

=head2 strip_urls

	$s->strip_urls();
	
searches L<body> for strip urls if I<regex_strip_url> is defined.
calls L<try_get_strip_urls> otherwise

returns: a array with the strip urls

database access: READ cfg(2)

=cut

sub strip_urls {
	my $s = shift;
	my $surl;
	if ($s->cfg('regex_strip_url')) {
		my $body = $s->body();
		return unless $body;
		my $regex = $s->cfg('regex_strip_url');
		my @surl = ($body =~ m#$regex#gsi);
		if ($s->cfg('regex_strip_url2')) {
			$regex = $s->cfg('regex_strip_url2');
			@surl = ($surl[0] =~ m#$regex#gsi);
		}
		@surl = $s->concat_url(\@surl);
		$surl = \@surl;
	}
	else {
		$surl = $s->try_get_strip_urls();
	}
	return unless $surl and @{$surl};
	if (my $re_ignore = $s->cfg("regex_ignore_strip")) {
		@{$surl} = grep {$_ !~ m#$re_ignore#i} @{$surl}; #we can ignore special srips, like 404 pages or other placeholders
	}
	if (my $subs = $s->cfg("substitute_strip_url")) {
		my ($re_substitute,$substitute) = split(/#/,$subs,2);
		@{$surl} = map {$_ =~ s#$re_substitute#substitute#i} @{$surl}; #if we really have to change a strip url, we can do so
	}
	$s->status("STRIP_URLS ".$s->url.": ". join(", ",@{$surl}),'DEBUG');
	return $surl;
}

=head2 try_get_strip_urls

	$s->try_get_strip_urls();
	
L<concat_url>s L<try_get_strip_urls_part>. so its just a wrapper

returns: a array with the strip urls

database access: none

=cut

sub try_get_strip_urls {
	my $s = shift;
	my @urls = $s->concat_url($s->try_get_strip_urls_part());
	return unless $urls[0];
		
	#delete doublicates
	my (%seen);
	@urls = grep { !$seen{$_}++ } @urls;
	
	return \@urls;
}

=head2 try_get_strip_urls_part

	$s->try_get_strip_urls_part();
	
searches L<body> for strip urls. filters images smaller than 21 pixel in width or height. 
uses other filters operating on the found img src strings. and removes duplicates found.

returns: a array ref with parts of the strip urls

database access: READ cfg

=cut

sub try_get_strip_urls_part {
	my $s = shift;
	my $body = $s->body;
	return unless $body;
	
	my $imgs = [];
	my @tags = ($body =~ m#(<\s*ima?ge?[^>]+>)#gis);

	my $i = 0;
	foreach my $tag (@tags) {
		my $regex = q#src\s*=\s*((?<p>["'])(?<src>.*?)\k<p>|(?<src>.*?)(\s*>|\s+\w+\s*=))#;
		if ($tag =~ m#$regex#is) {
			$imgs->[$i]->{src} = $+{src};
		}
		$regex =~ s/src/width/g;
		if ($tag =~ m#$regex#is) {
			($imgs->[$i]->{width}) = ($+{width} =~ /^\D*(\d+)\D*$/);
		}
		$regex =~ s/width/height/g;
		if ($tag =~ m#$regex#is) {
			($imgs->[$i]->{height}) = ($+{height} =~ /^\D*(\d+)\D*$/);
		} 
		$i++;
	}

	
	my @return;
	my @bad_return;
	foreach my $img (@{$imgs}) {
		my $url = $img->{src};
		next unless defined $url;
		if ((defined $img->{width} and $img->{width} < 21) or (defined $img->{height} and $img->{height} < 21)) {
			next;
		}
		if (defined $s->cfg('heur_strip_url')) {
			my $regex = $s->cfg('heur_strip_url');
			if ($url =~ m#$regex#i) {
				push(@return,$url);
			}
			next;
		}
		if ($url =~ m#([\?;=&]|nav|logo|header|template|resource|banner|thumb|file://|theme|icon|smiley|%3e)#i) {
			next;
		}
		if ($url =~ m#drunkduck.com/.*?/pages/#) {
			push(@return,$url);
			next;
		}
		if ($url =~ m#drunkduck.com/#) {
			next;
		}
		if ($url =~ m#^http://#) {
			my $url_home = $s->cmc->url_home;
			my $add_url_home = $s->cfg('add_url_home');
			next unless (($url =~ m#$url_home#) or 
						((defined $add_url_home) and $url =~ m#$add_url_home#));
		}
		if (($url =~ m#(^|\D)(\d{8}|\d{14})\D[^/]*$#) or ($url =~ m#(^|\D)\d{4}-\d\d-\d\d\D[^/]*$#)) {
			push(@return,$url);
			next;
		}		
		if ($url =~ m#comics?/|(?:^|/)pages?/|(?:^|/)strips?/#i) {
			push(@return,$url);
			next;
		}		
		my ($name) = ($s->url() =~ m#^.*/.*?(\w\w\w+)[^/]*$#) if ($s->url() !~ m/\.php\?/);
		if ($name) {
			if ($url =~ m#(.*/|[^/]*)$name#) {
				push(@bad_return,$url);
				next;
			}
		}
		my ($num) = ($s->url() =~ m#.*/.*?(\d+)[^/]*$#);
		if ($num) {
			if ($url =~ m#(?:\D|\D0+|^)$num\D[^/]*$#) {
				push(@bad_return,$url);
			}
		}		
	}
	@return = @bad_return unless $return[0];
	
	return \@return;
}

=head2 dummy

	$s->dummy();

calls L<strips> which sets the dummy flag.	

returns: returns true if there are no files found on the page.

database access: READ cfg

=cut

sub dummy {
	my $s = shift;
	$s->strips;
	return $s->{dummy};
}

}

=head1 wrapper and utility Methods

=cut

{ #data wrapper and utils

=head2 name

	$s->name();
	
see L<comic/name>
	
returns: the name of the comic

database access: none

=cut

sub name {
	my $s = shift;
	return $s->cmc->name;
}

=head2 name

	$s->name($new_url);
	
I<$new_url> will set the url to an new value (thats possibly a bad idea. create a new page object instead!)
	
returns: the url of the page

database access: none

=cut

sub url {
	my $s = shift;
 	$s->{url} = $_[0] if @_;
	return $s->{url};
}

=head2 name

	$s->name($n);
	
L<get_file_name> of L<strip> number C<$n>
	
returns: the file name of the C<$n>th file.

database access: none

=cut

sub file {
	my $s = shift;
	my $n = shift;
	return $s->get_file_name($s->strips->[$n]);
}

=head2 name

	$s->get_file_name($strip_url);
	
takes I<$strip_url> and extracts the file name. uses I<rename> if defined or some general logic if not.
	
returns: file name

database access: READ cfg

=cut

sub get_file_name {
	my $s = shift;
	my $surl = shift;
	my $url = $s->url();
	return $surl if ($s->dummy);
	my $filename = $surl;
	$filename =~ s#^.*/##;
	
	if ($surl =~ /drunkduck/) {
		$url =~ m/p=(\d+)/;
		my $name = $1;
		my $ending;
		if ($surl =~ m#(\.(jpe?g|gif|png))#) {
			$ending = $1;
		}
		$ending = ".jpg" unless ($ending);
		$filename = $name . $ending;
	}
	if ($s->cfg('rename')) {
		if ($s->cfg('rename') =~ m/^strip_url#(?<reg>.*)#(?<nm>\d*)/) {
			my $regex = $+{reg};
			my @wnum = split('',$+{nm});
			my @num = ($surl =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			$name = join('',@num) unless @wnum;
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
		if ($s->cfg('rename') =~ m/^url#(.*)#(\d*)/) {
			my $regex = $1;
			my @wnum = split('',$2);
			
			my @num = ($url =~ m#$regex#g);
			
			my $name;
			foreach my $wnum (@wnum) {
				$name .= $num[$wnum];
			}
			$name = join('',@num) unless @wnum;
			my $ending;
			if ($surl =~ m#(\.(jpe?g|gif|png))#) {
				$ending = $1;
			}
			$ending = ".jpg" unless ($ending);
			$filename = $name . $ending;
		}
		if ($s->cfg('rename') =~ m/^url_only#(.*)#(.*)#(\d*)/) {
			my $only = $1;
			my $regex = $2;
			my @wnum = split('',$3);
			if ($filename =~ /$only/i) {
				my @num = ($url =~ m#$regex#g);
				
				my $name;
				foreach my $wnum (@wnum) {
					$name .= $num[$wnum];
				}
				$name = join('',@num) unless @wnum;
				$filename = $name . $filename;
			}
		}
		if ($s->cfg('rename') =~ m/^strip_url_only#(.*)#(.*)#(\d*)/) {
			my $only = $1;
			my $regex = $2;
			my @wnum = split('',$3);
			if ($filename =~ /$only/i) {
				$surl =~ m#^(.+)/([^/]+)$#i;
				my $burl = $1;
				my $strp = $2;
				my @num = ($surl =~ m#$regex#g);
				my $name;
				foreach my $wnum (@wnum) {
					$name .= $num[$wnum];
				}
				$name = join('',@num) unless @wnum;
				$filename = $name . $filename;
			}
		}
	}
	return $filename;
}

=head2 body

	$s->body();
	
calls L<dbutil/get> to fetch the body (well the complete source including the head!) from L<url>
	
returns: the body or undef

database access: none

=cut

sub body {
	my $s = shift;
	unless ($s->{body}) {
		return undef if $s->{no_body};
		$s->{'body'} = dlutil::get($s->url());
		$s->status("BODY requestet: " . $s->url,'DEBUG');
		if ($s->{body} =~ m#^\d+$#) {
			$s->status("Body Request error: " . $s->{body},"ERR",$s->url());
			$s->{body} = undef;
			$s->{no_body} = 1;
			return undef;
		}
	}
	return $s->{'body'};
}

=head2 concat_url

	$s->concat_url($url_part);
	
C<$url_part> can be a single url or an array ref with a list of urls. 
it concats them with L<_concat_url>
	
returns: an array with all concatenated urls or the first url

database access: none

=cut

sub concat_url {
	my $s = shift;
	my $url_part = shift;
	my @url;
	
	if (ref $url_part) {
		foreach my $part (@{$url_part}) {
			push(@url,$s->_concat_url($part));
		}
	}
	else {
		$url[0] = $s->_concat_url($url_part);
	}

	return wantarray ? @url : $url[0];
}

=head2 _concat_url

	$s->_concat_url($url_part);
	
calls the URI module to concat C<$url_part> with L<url> to a complete url. (replaces C<$amp;> and C<$#038;> with C<&>)

	
returns: url

database access: none

=cut

sub _concat_url {
	my $s = shift;
	my $url_part = shift;
	return unless $url_part;
	$url_part =~ s!([^&])&amp;|&#038;!$1&!gs;
	return if ($url_part eq '#');
	if ($s->cfg('use_home_only')) {
		$url_part =~ s'^[\./]+'';
		$url_part = "/" . $url_part;
	}
	return URI->new($url_part)->abs($s->url())->as_string;
}

=head2 cmc

	$s->cmc();
	
returns: the comic object

database access: none

=cut

sub cmc {
	my $s = shift;
	return $s->{cmc};
}

=head2 cfg

	$s->cfg($key,$value);
	
loads config and sets defaults (L<class_change>)
sets C<$key> to C<$value> if C<$value>
	
see L<comic/cfg>
	
returns: value of C<$key>

database access: READ _CONF, WRITE _CONF

=cut

sub cfg {
	my $s = shift;
	return $s->cmc->cfg(@_);
}

=head2 dat  
	
	$s->dat($strip,$key,$value,$null);

accesses the C<_I<comic>> table
if C<$null> is true, sets $key for C<$strip> to C<NULL> 
if C<$value> is C<defined> updates/inserts C<$value>

see L<comic/dat>
	
returns: value of C<$key> where C<$strip>

database access: READ _I<comic>, WRITE _I<comic>

=cut

sub dat {
	my $s = shift;
	return $s->cmc->dat(@_);
}

=head2 usr 
	
	$s->usr($key,$value,$null);

accesses the C<USER> table
if C<$null> is true, sets $key for comic to C<NULL>
if C<$value> is C<defined> updates/inserts C<$value>
	
see L<comic/usr>
	
returns: value of C<$key>

database access: READ USER, WRITE USER

=cut

sub usr {
	my $s = shift;
	return $s->cmc->usr(@_);
}

=head2 status 
	
	$s->status($status,$type,$addinfo);

	
see L<comic/status>
	
returns: C<1>

database access: none

=cut

sub status {
	my $s = shift;
	$s->cmc->status(@_);
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->url,'DEBUG');
}

}

1;
