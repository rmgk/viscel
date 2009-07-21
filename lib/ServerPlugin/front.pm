#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::front;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *div);

use ServerPlugin qw(make_head dbstrps dbcmcs tags cache_strps);
our @ISA = qw(ServerPlugin);

our $VERSION = '0.9.0';

sub get_content {
	my ($plugin,@arguments) = @_;
	cache_strps($arguments[0]);
	return (cfront($arguments[0]));
}


=head2 Frontpage

here you see the first, (the L<bookmarked|/"Cataflag">) and the last strip. 
you can click them to start reading.

you can also click the B<stripslist> which is basically just a list of all strips,
and you can L<categorize|/"Cataflag"> the comic.

=cut 

sub cfront {
	my $comic = shift;
	my $first =  dbcmcs($comic,'first');
	my $last = dbcmcs($comic,'last' );
	my $bookmark = dbcmcs($comic,'bookmark' );
	my $ret = make_head($comic . " Frontpage",0,0,
					$first ?"/pages/$comic/$first" :"0",
					$last  ?"/pages/$comic/$last " :"0",
					);
					
	$ret .= start_div({-class=>'frontpage'});
	$ret .=		h2($comic);
	
	if($bookmark) {	#if a bookmark is set we display 3 strips at once.
		$ret .= a({-href=>"/pages/$comic/$first",-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front3",-id=>'first',-src=>"/strips/$comic/".$first,-alt=>"first"}));
		$ret .= a({-href=>"/pages/$comic/$last",-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front3",-id=>'last',-src=>"/strips/$comic/".$last,-alt=>"last"}));
		$ret .= a({-href=>"/pages/$comic/$bookmark",-accesskey=>'n',-title=>'bookmarked strip'},
				img({-class=>"front3",-id=>'bookmark',-src=>"/strips/$comic/".$bookmark,-alt=>"bookmark"}));
	} 
	else { #if not we just display two
		$ret .= a({-href=>"/pages/$comic/$first",-accesskey=>'f',-title=>'first strip'},
				img({-class=>"front2",-id=>'first',-src=>"/strips/$comic/".$first,-alt=>"first"}));
		$ret .= a({-href=>"/pages/$comic/$last",-accesskey=>'l',-title=>'last strip'},
				img({-class=>"front2",-id=>'last',-src=>"/strips/$comic/".$last,-alt=>"last"}));
	};
					
	$ret .=		start_div({-class=>"navigation"});
	
	$ret .=		a({-href=>"/index",-accesskey=>'i',-title=>'Index'},"Index").' '.
				a({-href=>"/pages/$comic",-accesskey=>'s',-title=>'striplist'},"Striplist").' '.
				a({href=>"/tools/cataflag/$comic",-accesskey=>'c',-title=>'categorize'},'Categorize').
				br;
	$ret .=		'Strips: ' . dbstrps($comic,'id'=>$last,'number');
	

	$ret .= " tags: " . join(" ",tags($comic)) if tags($comic);
	
	$ret .=		end_div;
	$ret .=		end_div();
	
	return $ret . end_html;
}

1;