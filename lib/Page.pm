#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009
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

use URI;
$URI::ABS_REMOTE_LEADING_DOTS = 1;

use Time::HiRes;
use Scalar::Util;

use Strip;

our $VERSION;
$VERSION = '42';

our $Pages = 0;

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
	$Page::Pages++;
	$s->status("NEW PAGE: ($Page::Pages) ".$s->url,'DEBUG');
	Scalar::Util::weaken($s->{cmc});
	return $s;
}

=head2 all_strips

	Page->all_strips();

finds L</title> for L</strips> and L<save>s them. L<index_all> afterwards

returns: C<1> if successful, undefined if L<save> returned an error 

=cut

sub all_strips {
	my $s = shift;
	my ($prev_strip) = @_;
	return 0 unless $s->body;
	
	foreach my $strip (@{$s->strips}) {
		return undef unless $strip->get_data();
		$prev_strip->next($strip) if $prev_strip;
		$strip->prev($prev_strip);
		return undef unless $strip->commit_info();
		$prev_strip = $strip;
	}
	
	#$s->release_strips;
	
	return $prev_strip;
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

database access: READ ini(3)

=cut

sub url_prev {
	my ($s) = @_;
	my $body = $s->body();
	return unless $body;
	my $urls =[];
	if (my $regex_prev = $s->ini('regex_prev')) {
		if ($body =~ m#$regex_prev#is) {
			$urls->[0] = $s->concat_url($+{u}//$1);
		}
	}
	elsif ($s->ini('list_url_regex')) {
		my ($purl,$nurl) = $s->list_side_urls();
		$urls->[0] = $purl;
	}
	elsif ($s->{header}->{prev}) {
		$urls->[0] = $s->{header}->{prev};
	}
	else {
		my ($purl,$nurl) = $s->try_get_side_urls();
		$urls = $purl;
	}
	
	my $regex_not_goto = $s->ini("regex_not_goto");
	my $regex_never_goto = $s->ini("regex_never_goto");
	my $url_start = $s->ini("url_start");
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

database access: READ ini(3)

=cut

sub url_next {
	my ($s) = @_;
	return @{$s->{url_next_arrayref}} if $s->{url_next_arrayref};
	my $body = $s->body();
	return unless $body;
	my $urls = [];
	if (my $regex_next = $s->ini('regex_next')) {
		if ($body =~ m#$regex_next#is) {
			$urls->[0] = $s->concat_url($+{u}//$1);
		}
	}
	elsif ($s->ini('list_url_regex')) {
		my ($purl,$nurl) = $s->list_side_urls();
		$urls->[0] = $nurl;
	}
	elsif ($s->{header}->{next}) {
		$urls->[0] = $s->{header}->{next};
	}
	else {
		my ($purl,$nurl) = $s->try_get_side_urls();
		$urls = $nurl;
	}
	my $regex_never_goto = $s->ini("regex_never_goto");
	my $url_start = $s->ini("url_start");
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
	$s->{url_next_arrayref} = \@ret_urls;
	return @ret_urls;
}

=head1 list_side_urls

	$s->list_side_urls();
	
takes the page url and matches against I<list_url_regex>. 
finds chapters and pages listet on current page with I<list_chap_regex> and I<list_page_regex>.
inserts next chapter/page into I<list_url_insert> and returns prev and or next;

returns: previous url, next url

database access: READ ini(4)

=cut

sub list_side_urls {
	my $s = shift;
	my $url = $s->url;
	my $body = $s->body;
	my $url_regex = $s->ini('list_url_regex');
	my $insert_into = $s->ini('list_url_insert');
	my $chap_regex = $s->ini('list_chap_regex');
	my $page_regex = $s->ini('list_page_regex');
	$url =~ m#$url_regex#i;
	my $chap = $+{chap};
	my $page = $+{page};
	my @chaps;
	while ($body =~ m#$chap_regex#gis) {
		push(@chaps,$+{chaps} // $1) unless ($s->ini('list_chap_reverse'));
		unshift(@chaps,$+{chaps} // $1) if ($s->ini('list_chap_reverse'));
	}
	my @pages;
	while ($body =~ m#$page_regex#gis) {
		push(@pages,$+{pages} // $1) unless ($s->ini('list_page_reverse'));
		unshift(@pages,$+{pages} // $1) if ($s->ini('list_page_reverse'));
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

database access: READ ini

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
	my $regex_never_goto = $s->ini('regex_never_goto');
	foreach my $fil (@filter) {
		next unless $fil;
		next if ($regex_never_goto) and ($fil =~ m#$regex_never_goto#i);
		
		my $re_link = qr#<\s*a[^>]*href\s*=\s*((?<p>["'])(?<link>.*?)\k<p>|(?<link>.*?)(\s*>|\s+\w+\s*=))#i;
		if ($fil =~ $re_link) {
			my $tmp_url = $+{link};
			next if (($tmp_url!~m#\.\w{3,4}\?#i) and ($tmp_url =~ m#\.jpe?g$|\.png$|\.gif$#i));
			next if (($tmp_url =~ m#http://#i) and !($tmp_url =~ m#$url_home#i));
			next if ($tmp_url =~ m#^\w+:#) and !($tmp_url =~ m#https?://#i);
			if ($fil =~ m#prev|back|prior#i) {
					push (@prev, $tmp_url);
			}
			if ($fil =~ m#next|forward|ensuing#i) {
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

returns: a array with the strip objects

database access: none

=cut

sub strips {
	my $s = shift;
	return $s->{strips} if $s->{strips};
	my $urls = $s->strip_urls();
	unless ($urls->[0]) {
		$s->{dummy} = 1;
		$s->status("NO STRIPS: ".$s->url,'WARN');
		$s->{strips}->[0] =  Strip->new({page=>$s,dummy=>1})
	}
	foreach my $url (@$urls) {
		push (@{$s->{strips}},Strip->new({url=>$url,page=>$s}));
	}
	return $s->{strips};
}

sub release_strips {
	my $s = shift;
	delete $s->{strips};
}

=head2 strip_urls

	$s->strip_urls();
	
searches L<body> for strip urls if I<regex_strip_url> is defined.
calls L<try_get_strip_urls> otherwise

returns: a array ref with the strip urls

database access: READ ini(2)

=cut

sub strip_urls {
	my $s = shift;
	my $surl;
	if ($s->ini('regex_strip_url')) {
		my $body = $s->body();
		return unless $body;
		my $regex = $s->ini('regex_strip_url');
		my @surl = ($body =~ m#$regex#gsi);
		if ($s->ini('regex_strip_url2')) {
			$regex = $s->ini('regex_strip_url2');
			@surl = ($surl[0] =~ m#$regex#gsi);
		}
		@surl = $s->concat_url(\@surl);
		$surl = \@surl;
	}
	else {
		$surl = $s->try_get_strip_urls();
	}
	return unless $surl and @{$surl};
	if (my $re_ignore = $s->ini("regex_ignore_strip")) {
		@{$surl} = grep {$_ !~ m#$re_ignore#i} @{$surl}; #we can ignore special srips, like 404 pages or other placeholders
	}
	if (my $subs = $s->ini("substitute_strip_url")) {
		my ($re_substitute,$substitute) = split(/#/,$subs,2);
 		@{$surl} = map {$_ =~ s#$re_substitute#$substitute#i;$_} @{$surl}; #if we really have to change a strip url, we can do so
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
	
searches L<body> for strip urls. filters images smaller than 51 pixel in width or height. 
uses other filters operating on the found img src strings. and removes duplicates found.

returns: a array ref with parts of the strip urls

database access: READ ini

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
		if ((defined $img->{width} and $img->{width} < 51) or (defined $img->{height} and $img->{height} < 51)) {
			next;
		}
		if (defined $s->ini('heur_strip_url')) {
			my $regex = $s->ini('heur_strip_url');
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
			next unless ($url =~ m#$url_home#);
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
	
	if (!@return) {
		my (@embed) = ($body =~ m#<embed([^>]+)>#is);
		if (@embed) {
			my $regex = q#src\s*=\s*((?<p>["'])(?<src>.*?)\k<p>|(?<src>.*?)(\s*>|\s+\w+\s*=))#;
			foreach my $em (@embed) {
				if ($em  =~ m#$regex#is) {
					next if ($+{src} !~ m#\?[^/]*$#is);
					push (@return, $+{src});
					$s->status('WARNING: using embedded object as strip: '. $+{src},'WARN');
				}
			}
		}
	}
	
	return \@return;
}

=head2 dummy

	$s->dummy();

calls L<strips> which sets the dummy flag.	

returns: returns true if there are no files found on the page.

database access: READ ini

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

=head2 url

	$s->url($new_url);
	
I<$new_url> will set the url to an new value (thats possibly a bad idea. create a new page object instead!)
	
returns: the url of the page

database access: none

=cut

sub url {
	my $s = shift;
 	$s->{url} = $_[0] if @_;
	return $s->{url};
}

=head2 strip

	$s->strip($n);
	
L<get_file_name> of L<strip> number C<$n>
	
returns: the C<$n>th strip object.

database access: none

=cut

sub strip {
	my $s = shift;
	my $n = shift;
	return $s->strips->[$n];
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
		my $res = dlutil::get($s->url(),$s->ini('referer'));
		$s->status("BODY requestet: " . $s->url,'DEBUG');
		if ($res->is_error()) {
			$s->status("Body Request error: " . $res->status_line(),"ERR",$s->url());
			$s->{body} = undef;
			$s->{no_body} = 1;
			return undef;
		}
		$s->url($res->request->uri);
		if (($s->{header}->{next}) = grep {/rel=["']next["']/i} $res->header('Link')) {
			$s->{header}->{next} =~ s#^<([^>]+)>.*$#$1#;
		}
		if (($s->{header}->{prev}) = grep {/rel=["']prev["']/i} $res->header('Link')) {
			$s->{header}->{prev} =~ s#^<([^>]+)>.*$#$1#;
		}
		say "OMG OMG OMG HAS NEXT OR PREV HEADER!!!!!!!!!!!!!"  if $s->{header}->{next} or $s->{header}->{prev};

		$s->{'body'} = $res->decoded_content();
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
	if ($s->ini('use_home_only') and ($url_part !~ m#^https?://#i)) {
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

=head2 ini

	$s->ini($key,$value);
	
loads config and sets defaults (L<class_change>)
sets C<$key> to C<$value> if C<$value>
	
see L<comic/ini>
	
returns: value of C<$key>

database access: READ _CONF, WRITE _CONF

=cut

sub ini {
	my $s = shift;
	return $s->cmc->ini(@_);
}

=head2 dbstrps  
	
	$s->dbstrps($get,$key,$select,$value);

accesses the C<_I<comic>> table

where column C<$get> equals C<$key>:
if C<$value> is C<defined> updates C<$select> to C<$value>
	
returns: value of C<$select> where column C<$get> equals C<$key>

database access: READ _I<comic>, WRITE _I<comic>

=cut

sub dbstrps {
	my $s = shift;
	return $s->cmc->dbstrps(@_);
}

=head2 dbcmc 
	
	$s->dbcmc($key,$value);

accesses the C<comics> table

if C<$value> is C<defined> updates C<$value>
	
returns: value of C<$key>

database access: READ comics, WRITE comics

=cut

sub dbcmc {
	my $s = shift;
	return $s->cmc->dbcmc(@_);
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
	delete $s->{strips}; # we need to manually destroy the strips so that this object still exists while strips are gc'd
	$Page::Pages--;
	$s->status("DESTROYED: ($Page::Pages) ". $s->url,'DEBUG');
}

}

1;
