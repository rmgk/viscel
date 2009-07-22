#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::index;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *table :html3 *div gradient);


use ServerPlugin qw(dbh make_head dbstrps dbcmcs tags flags is_broken);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.0.0';

sub get_content {
	my ($plugin,@arguments) = @_;
	return (cindex());
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

=item * L</"Favourites">

=item * L</"Random">

=back

=cut


sub cindex {
	my $ret = make_head("Index");
	my @tag = param('tag');
	$ret .= start_div({-id=>"menu"});
	$ret .=	"Tools:" . br 
				.	a({-href=>"/tools/query",-accesskey=>'q',-title=>'query'},"Custom Query"). br 
				#.	a({-href=>"/tools/filter",-accesskey=>'f',-title=>'filter'},"Custom Contents"). br 
				.	a({-href=>"/tools/favourites",-accesskey=>'f',-title=>'favourites'},"Favourites"). br
				.	a({-href=>"/tools/random",-accesskey=>'r',-title=>'random'},"Random Comic"). br 
				.	a({-href=>"/pod",-accesskey=>'h',-title=>'help'},"Help"). br ;
				
	my $i = 1;
	$ret .=	br . "Contents:" . br .
				join("",map { a({href=>"#$_",-accesskey=>$i++,-title=>$_},$_) . br} (qw(continue other finished stopped),)); #TODO &filter));
				 
	$ret .=	br . "Tags:" . br .
				 a({-href=>"/index"},'Any Tag') . br .
				 join("",map {a({-href=>"?tag=$_".join("",map {"&tag=$_"} (@tag)) },$_) . br} (tags()));	
				 
	$ret .=	br . "Color Scale:" .br.
				 join("",map {div({-style=>"color:#".colorGradient(log($_),10)},$_ )} (10000,5000,2000,1000,500,200,100,50,10,1)) . br;
		 
	$ret .= end_div(); 
	
	$ret .= &preview_head();
	
	my $cmcs = dbh->selectall_hashref("SELECT * FROM comics",'comic');
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
	my $comics = dbh->selectcol_arrayref("SELECT comic FROM comics WHERE ($filter)");
	
	return undef unless $comics;
	
	my $ret = start_div({-class=>"group"}) . h1(a({name=>$name},$name));
	$ret .= start_table();
	
	my $count;
	my $counted;
	
	my %toRead;
	foreach my $comic ( @{$comics}) {
		if (is_broken($comic)) {
			$toRead{$comic} = -1;
			next;
		}
		my $to_read = $user->{$comic}->{'strip_count'};
		if (defined $to_read) {
			$toRead{$comic} = $to_read;
			next;
		}
		my $bookmark = $user->{$comic}->{'bookmark'};
		if ($bookmark) {
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
		dbcmcs($comic,'strip_count'=>$toRead{$comic});
	}
	
	foreach my $comic ( sort {$toRead{$b} <=> $toRead{$a}} @{$comics}) {
		my $usr = $user->{$comic};
		my $mul = $toRead{$comic};

		$mul = ($mul > 0) ? log($mul) : $mul ? -1 : 0;# dont hit me! (bigger 0 is log; equal 0 is 0; smaller zero is -1)
		
		
		my ($first,$bookmark,$last) = ($usr->{'first'},$usr->{'bookmark'},$usr->{'last'});
		my $cmc_str = td(a({-href=>"/pages/$comic/%s",-onmouseout=>"hideIMG();",
						-onmouseover=>"showImg('/strips/$comic/%s')"},"%s"));
		
		$ret .= "<tr>";
		$ret .= td(a({-href=>"/front/$comic",-class=>(is_broken($comic)?'broken':'comic'),
			-style=>"color:#". colorGradient($mul,10) .";font-size:".(($mul/40)+0.875)."em;"},$comic));
			
		$ret .= $first	 ?	sprintf($cmc_str , $first	 , $first	 , '|&lt;&lt;')  :td();
		$ret .= $bookmark?	sprintf($cmc_str , $bookmark , $bookmark , '||'		  )  :td();
		
		if ($bookmark and $last and ($bookmark eq $last)) {
			$ret .=	td('&gt;&gt;|');
		}
		elsif ($last) {
			$ret .= sprintf($cmc_str, $last ,$last , '&gt;&gt;|');
		}
		$ret .= td($toRead{$comic}) if param('toread');
		#$ret .= td($mul);
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
	elsif ($col==0) {
		return '33cc55';
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

1;
