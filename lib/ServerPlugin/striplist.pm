#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::striplist;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *div);

use ServerPlugin qw(dbh make_head dbstrps cache_strps);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.0.1';

sub get_content {
	my ($plugin,@arguments) = @_;
	return (cclist($arguments[0]));
}

sub cclist {
	my $comic = shift;
	my $ret = make_head("Error");
	
	return $ret . "no comic defined" unless $comic;
	
	my $list;
	my $dat = dbh->selectall_hashref("SELECT id,number,title,file FROM _$comic WHERE number IS NOT NULL","number");
	
	$list = make_head($comic);
	$list .= preview_head();
	
	
	#we save this string cause we need it really often, so we can save some processing time :)
	my $strip_str = div({-class=>"striplist"},img({-src=>"/strips/$comic/%s"}) . #the img is normally hidden by the strylesheet
		a({-href=>"/pages/$comic/%s",-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/$comic/%s')",}, "%s"));
	
	
	for (my $i = 1;defined $dat->{$i};$i++) {

		my %titles = get_title($comic,$dat->{$i}->{id},$dat->{$i}->{title});
		$list .= sprintf $strip_str,
			$dat->{$i}->{file}, #direct img url
			$dat->{$i}->{id}, #strip page url
			$dat->{$i}->{file}, #direct img url
			"$i : $dat->{$i}->{file} : " .join(' - ',grep { $_ } values(%titles) ); #strip title
	}
	$list .= end_html;
	return $list;
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

sub get_title {
	my ($comic,$strip,$title) = @_;
	return undef unless $title;
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

1;
