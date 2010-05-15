#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::pages;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *div);

use ServerPlugin qw(dbh make_head dbstrps dbcmcs cache_strps flags get_title);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.1.1';

sub get_content {
	my ($plugin,@arguments) = @_;
	cache_strps($arguments[0]);
	return (ccomic($arguments[0],$arguments[1]));
}


=head2 Strip Pages

these pages are pretty straight forward use the B<next> and B<prev> links to navigate 
use B<pause> to bookmark the strip and go to the L<categorize|/"Cataflag"> page
B<front> returns you to the L<frontpage|/"Frontpage"> and B<site> links to the page the strip was downloaded from

=cut

sub ccomic {
	my $comic = shift;
	my $strip = shift;
	
	return make_head("Error") . "no comic defined" unless $comic;
	return make_head("Error") . "no strip defined" unless $strip;
	
	if (param('fav')) {
		unless (dbh->selectrow_array('SELECT * FROM favourites WHERE sha1 = ?',undef,dbstrps($comic,'id'=>$strip,'sha1'))) {
			my @values = dbh->selectrow_array("SELECT file,id,sha1,number,surl,purl,title FROM _$comic WHERE id = ?",undef,$strip);
			unshift(@values,$comic);
			dbh->do('INSERT INTO favourites (comic,file,id,sha1,number,surl,purl,title) VALUES (?,?,?,?,?,?,?,?)',undef,@values);
		}
	}

	my %titles = get_title($comic,$strip);
	
	my ($prev,$next, $first,$last,$file) = (
			dbstrps($comic,'id'=>$strip,'prev'),dbstrps($comic,'id'=>$strip,'next'),
			dbcmcs($comic,'first'),dbcmcs($comic,'last'),dbstrps($comic,'id'=>$strip,'file')
		);
	my $nfile = dbstrps($comic,'id'=>$next,'file');
	my $ret = make_head(($titles{st}//$comic) .' - '. dbstrps($comic,'id'=>$strip,'number')."/".$last,
				$prev  ?"/pages/$comic/$prev" :"0",
				$next  ?"/pages/$comic/$next" :"0",
				$first ?"/pages/$comic/$first":"0",
				$last  ?"/pages/$comic/$last" :"0",
				$nfile ?"/strips/$comic/$nfile":"0", #prefetch
				$nfile  ?"(new Image()).src = '/strips/$comic/$nfile'":"0", #more prefetch .. 
				);
				
	$ret .= start_div({-class=>"comic"});
	
	$ret .= h3({-id=>'title'},$titles{h1}) if $titles{h1};
	if ($file and (-e "./strips/$comic/$file")) { 
		if ($file =~ m#.dcr$|.swf$#i) {
			$ret .= embed({-src=>"/strips/$comic/$file",-quality=>'high',-type=>($titles{et}//''),-width=>($titles{ew}//800),-height=>($titles{eh}//800)});
		}
		else {
			$ret .= start_div();
			$ret .= img({-src=>"/strips/$comic/$file",-title=>($titles{it}//''),-alt=>($titles{ia}//'')});
			$ret .= end_div();
			if ($nfile) {
				$ret .= start_div();
				$ret .= img({-src=>"/strips/$comic/$nfile",-title=>(''),-alt=>('')});
				$ret .= end_div();
			}
		}
	}
	elsif (dbstrps($comic,'id'=>$strip,'surl')) {
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
	$ret .= end_div;
	
	$ret .=start_div({-class=>"navigation"});
	
	$ret .= a({-href=>"/pages/$comic/$prev",
			-title=>'previous strip',-accesskey=>'v'},'&lt;&lt; ') if $prev;
	$ret .= span({-id=>'pagecount'},dbstrps($comic,'id'=>$strip,'number')."/".$last);
	$ret .= a({-href=>"/pages/$comic/$next",
			-title=>'next strip',-accesskey=>'n'},'&gt;&gt; ') if $next;

	$ret .= br;
	if ($next) {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=r",
				-accesskey=>'d',-title=>'pause reading this comic'},"pause");
		$ret .= ' - ';
	}
	elsif (flags($comic)->{c}) {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=rcf",
				-accesskey=>'d',-title=>'finish reading this comic'},"finish ");	
		$ret .= ' - ';
	}
	else {
		$ret .= a({-href=>"/tools/cataflag/$comic?bookmark=$strip&addflag=r",
				-accesskey=>'d',-title=>'pause reading this comic'},"pause ");	
		$ret .= ' - ';
	}
	$ret .= a({-href=>"/pages/$comic/$strip?fav=1",
		-accesskey=>'s',-title=>'save this strip as favourite'},"fav ");	
	$ret .= ' - ';
	$ret .= a({-href=>"/front/$comic",
			-accesskey=>'f',-title=>'frontpage'},"front ");	
			
	my $purl = dbstrps($comic,'id'=>$strip,'purl');
	$ret .= ' - ' if $purl;
	$ret .= a({-href=>$purl, -accesskey=>'s',-title=>'homepage of the strip'},"site ") if $purl;	

	$ret .= end_div;
	
	return $ret . end_html;

}

1;
