#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::tools;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *div *table);
use Data::Dumper;

use ServerPlugin qw(dbh make_head dbstrps dbcmcs is_broken tags flags cache_strps get_title);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.0.4';

my %rand_seen;

sub get_content {
	my ($plugin,@arguments) = @_;
	return (ctools($arguments[0],$arguments[1]));
}


=head1 Tools

=cut

sub ctools {
	my $tool = shift;
	my $comic = shift;
	if (!$tool) {
		return make_head('Eroor') . 'called tools without tool';
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
		elsif (param('st')) { tags($comic,''); }
		my $flags = join ('',param('flags'));
		if ($flags) {flags($comic,$flags)}
		elsif (param('sf')) { flags($comic,''); }
		my $addflag = param('addflag');
		flags($comic,"+$addflag") if $addflag;
		if (param('bookmark')) {
			my $bookmark = param('bookmark');
			dbcmcs($comic,'bookmark',$bookmark );
			my $lnum = dbstrps($comic,'id'=>dbcmcs($comic,'last'),'number');
			my $bnum = dbstrps($comic,'id'=>$bookmark,'number');
			$lnum //= 0; $bnum //=0;
			dbcmcs($comic,'strip_count',$lnum-$bnum);
		}
		#if (param()) {
			#return undef; # this causes to redirect back to this page without parameters
		#}
		my $res = make_head($comic." tags and flags");
		$res .= start_div({-class=>'tools'});
		$res .= h1('Tags');
		$res .= start_form({-method=>'GET',-action=>"/tools/cataflag/$comic",-name=>'setTags'});
		$res .= hidden('st',1+rand(1000)); #it smells like hack! its needed for the redirect not to be saved just leave it as it is :) 
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
		$res .= hidden('sf',1+rand(1000));#it smells like hack! its needed for the redirect not to be saved just leave it as it is :) 
		$res .= checkbox_group(-name=>'flags',
								 -onclick=>'document.setFlags.submit()',
	                             -values=>[qw(c r f s l w)],
								 -default=>[ keys %{flags($comic)}],
	                             -linebreak=>'true',
								 -disabled=>[qw(l w)],
								 -labels=>{c=>'this comic is complete',r=>'you are reading this comic',
								 f=>'you finished reading this comic (needs completed)',s=>'you stopped reading this comic',
								 l=>'this comic has a loop',w=>'database error warning'});
		#$res .= br . submit('ok');
		$res .= end_form;
		# $res .= br . (flags($comic)->{c} ?a({-href=>"/tools/cataflag/$comic?addflag=rf"},"this comic is complete and i have finished reading it")
					# : a({href=>"/tools/cataflag/$comic?addflag=c"},'this comic is complete'));
		# $res .= br . a({href=>"/tools/cataflag/$comic?addflag=s"},'stop reading this comic');
		$res .= br. a({-href=>"/tools/comics/$comic"},"advanced") .br;
		$res .= a({href=>"/tools/comment/$comic"},'comment').br;
		
		$res .= a({href=>"/tools/datalyzer/$comic",-accesskey=>'d',-title=>'datalyzer'},'datalyzer').br;
		$res .= br. a({-href=>"/tools/cataflag/$comic"},"reload") .br. a({-href=>"/front/$comic"},"Frontpage").br. a({-href=>"/index"},"Index");
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
		$d{no_backlink_next}->{n} = 0;
		$d{no_backlink_prev}->{n} = 0;
		my $strips = dbh->selectall_hashref("SELECT * FROM _$comic","id");
		foreach my $strp (keys %{$strips}) {
			$d{count}->{$d{count}->{n}} = $strp;
			$d{count}->{n}++;
			$d{strps}->{$strp} = $strips->{$strp};
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
			if ($next and !($strips->{$next}->{prev} == $strp)) { #if prev of next is not self
				$d{no_backlink_next}->{$d{no_backlink_next}->{n}} = $strp;
				$d{no_backlink_next}->{n}++;
			}
			my $prev = $strips->{$strp}->{prev};
			if ($prev and !($strips->{$prev}->{next} == $strp)) { #if next of prev is not self
				$d{no_backlink_prev}->{$d{no_backlink_prev}->{n}} = $strp;
				$d{no_backlink_prev}->{n}++;
			}
			
		}
		
		my $res = make_head("Datalyzer");
		$res .= start_div({-class=>'tools'});
		my $sec = param('section') ;
		
		if ($sec and ($sec eq 'strps')) {
			$res .= table(Tr([map {td([ #creating table with key : value pairs via map
					#if it is prev or next make it a link; else just print out the information
					$_,":",	($_ =~ m/prev|next/)	?	a({href=>"/tools/datalyzer/$comic?section=strps&strip=".$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					#make links klickable
					($_ =~ m/url/)	?	 a({href=>$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					($_ =~ m/^id$/)	?	 a({href=>"/pages/$comic/".$d{$sec}->{param('strip')}->{$_}},$d{$sec}->{param('strip')}->{$_})	:
					$d{$sec}->{param('strip')}->{$_}
					])} grep {$_ ne 'n'} keys %{$d{$sec}->{param('strip')}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools/datalyzer/$comic"},"datalyzer main");
		}
		elsif ($sec) {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools/datalyzer/$comic?section=strps&strip=" . $d{$sec}->{$_}},$d{$sec}->{$_})
					])} sort {$d{$sec}->{$a} <=> $d{$sec}->{$b}} grep {$_ ne 'n'} keys %{$d{$sec}}]));	#getting all keys 
			$res .= br . a({-href=>"/tools/datalyzer/$comic"},"datalyzer main");
		}
		else {
			$res .= table(Tr([map {td([	#creating table with key : value pairs via map
					a({-href=>"/tools/datalyzer/$comic?section=$_"},$_) , ':' , $d{$_}->{n}
					])} grep {$_ ne 'strps'} sort keys %d]));	#getting all keys 
		}
		$res .= br . a({-href=>"/tools/comics/$comic"},"comic overview") .br;
		$res .= br.a({-href=>"/tools/cataflag/$comic"},"cataflag")  . br ;
		return $res .= br . a({-href=>"/index"},"Index") . end_div.end_html;
	}

	
=head2 Database View

here you can view the comics table directly. this is just for debugging purposes.

=cut

	if ($tool eq 'comics') {
		my $res = make_head('comics');
		if ($comic) {
			cache_strps($comic);
			$res .= start_div({-class=>'tools'});
			my $user = dbh->selectrow_hashref("SELECT * FROM comics WHERE comic = ?",undef,$comic);
			$res .= start_table;
			foreach my $key (keys %{$user}) {
				$res .=  Tr(td("$key :"),td( 
					$key =~ /^first$|^last$|^bookmark$/
					? a({href=>"/tools/datalyzer/$comic?section=strps&strip=".dbcmcs($comic,$key)},dbcmcs($comic,$key))
					: dbcmcs($comic,$key)
					));
			}
			$res .= end_table . br . br;
			$res .= a({-href=>"/tools/datalyzer/$comic"},"datalyzer main"). br.br;
			$res .= a({-href=>"/tools/strips/$comic"},"strips").br.br;
			$res .= a({-href=>"/tools/comics/$comic"},"reload") .br.a({-href=>"/tools/cataflag/$comic"},"cataflag")  . br ;
			$res .= a({-href=>"/index"},"Index") . end_div.end_html;
			return $res;
		}
		my $user = dbh->selectall_hashref("SELECT * FROM comics","comic");
		$res .= start_table;
		my $h = 0;
		foreach my $cmc (sort{uc($a) cmp uc($b)} (keys %{$user})) {

			$res .=  Tr(td('name'),td([keys %{$user->{$cmc}}])) if !$h;
			$h = 1;
			$res .=  Tr(td(a({-href=>"/tools/comics/$cmc"},$cmc)),td([map {textfield(-name=>$_, -default=>dbcmcs($cmc,$_))} keys %{$user->{$cmc}}]));
		}
		return $res . end_table . br . br .  a({-href=>"/index"},"Index") . end_html;
	}
	
	if ($tool eq 'strips') {
		my $res = make_head('strips');
		#$res .= start_div({-class=>'tools'});
		if ($comic) {
			my $user = dbh->selectall_hashref("SELECT * FROM _$comic",'id');
			$res .= start_table;
			my $h = 0;
			foreach my $key (keys %{$user}) {
				$res .=  Tr(td([keys %{$user->{$key}}])) if !$h;
				$h = 1;
				$res .=  Tr(td([map {textfield(-name=>$_, -default=>$user->{$key}->{$_})} keys %{$user->{$key}}]));
			}
			return $res . end_table . br . br .a({-href=>"/tools/strips/$comic"},"reload") .br.a({-href=>"/tools/comics/$comic"},"back")  . br . a({-href=>"/index"},"Index") . end_html;
		}
	}
	
	
=head2 Custom Query

input a sqlite query which will be executed in the longer input field.
if you input a column in the second field, the output becomes more readable

=cut
	
	if ($tool eq 'query') {
		my $res = make_head('Query');
		$res .= start_div({-class=>'tools'});
		if (param('query')) {
			if (param('hashkey')) {
				$res .= pre(Dumper(dbh->selectall_hashref(param('query'),param('hashkey'))));
			}
			else {
				$res .= pre(Dumper(dbh->selectall_arrayref(param('query'),{Slice=>{}})));
			}
			return $res . br . br . a({-href=>"/tools/query"},"Back") . br .  a({-href=>"/index"},"Index") . end_div.end_html;
		}
		$res .= start_form("GET","/tools/query");
		$res .= 'enter sql query string here'.br;
		$res .= textarea(-name=>"query", -rows=>4,-columns=>80) . br;
		#$res .= 'select hash key'.br;
		#$res .= textfield(-name=>"hashkey", -size=>"20");
		$res .= br . submit('ok');
		return $res . br . br .  a({-href=>"/index"},"Index") . end_div.end_html;
	}
	
=head2 Random

get a random comic frontpage. you dont get comics that you are reading, have completed or stopped. no doubles in one session.
 you can reload the page to get a new random comic .. and another one ... and another one ... have .. to .. stop .. reloading ...

=cut
	
	if ($tool eq 'random') {
		my $firsts = dbh->selectall_hashref('SELECT comic,first FROM comics WHERE (flags NOT LIKE "%r%" AND flags NOT LIKE "%f%" AND flags NOT LIKE "%s%") OR flags IS NULL' , 'comic');
		my @comics = keys %{$firsts};
		my $comic;
		while($comic = splice(@comics,rand(int @comics),1)) {
			next if is_broken($comic);
			next if $rand_seen{$comic};
			$rand_seen{$comic} = 1;
			require ServerPlugin::front;
			return ServerPlugin::front::cfront($comic);
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
		my $res = make_head('Filter');
		$res .= start_form("GET","/tools/filter");
		$res .= start_table;
		foreach my $filter (&filter) {
			if (defined param($filter) and param($filter) ne '') {
				filter($filter,param($filter));
			}
			$res .=  Tr(td($filter),td(filter($filter),td(a({-href=>"/tools/filter&delete=$filter"},'delete'))));
		}
		$res .=  Tr(td(textfield(-name=>"new_filter_name", -size=>"20")),td(textfield(-name=>"new_filter", -size=>"100")));
		return $res . end_table . submit('ok'). br . br . a({-href=>"/tools/filter"},"reload") .br. a({-href=>"/index"},"Index") . end_html;
	}

=head2 Favourites

this is a straight forward list of all you favourites. click the fav link of any strip to make it a favourite

=cut
	
	if ($tool eq 'favourites') {		
		my $list;
		my $dat = dbh->selectall_arrayref("SELECT id,title,file,comic FROM favourites WHERE id IS NOT NULL ORDER BY comic,file",{Slice => {}});
		
		$list = make_head('favourites');		
		$list .= preview_head();
		
		#we save this string cause we need it really often, so we can save some processing time :)
		my $strip_str = a({-href=>"/pages/%s/%s",-onmouseout=>"hideIMG();",-onmouseover=>"showImg('/strips/%s/%s')",}, "%s") .br .br;
		
		my $i = 0;
		my $comic = '';
		for my $fav (@$dat) {
			if ($comic ne $fav->{comic}) {
				$list .= end_div() if $comic;
				$list .= start_div({-class=>"favlist"});
				$comic = $fav->{comic};
				$list .= h3($comic);
			}
			$i ++;
			my %titles = get_title($fav->{comic},$fav->{id},$fav->{title});
			$list .= sprintf $strip_str,
				$fav->{comic},
				$fav->{id}, #strip page url
				$fav->{comic},
				$fav->{file}, #direct img url
				"$i : $fav->{file} : " .join(' - ',grep { $_ } values(%titles) ); #strip title
		}
		$list .= end_div().div({-class=>"favlist"},a({-href=>"/index"},"Index")) .end_html;
		return $list;

	}
	
	if ($tool eq  'checkallcomics') {
		my $delnone = 0;
		if ($comic and $comic eq 'delete_none') { #comic is not actually the comic but the second parameter i the address (e.g. /tools/checkallcomics/delete_none
			$delnone = 1;
		}
		my $ret = make_head('checkall');
		$ret .= start_div({-class=>'tools'});
		$ret .= start_table();
		my $comics = dbh->selectcol_arrayref("SELECT comic FROM comics ORDER BY comic");
		foreach my $comic (@$comics) {
			my ($prev) = dbh->selectrow_array("SELECT COUNT(*) FROM _$comic WHERE prev IS NOT NULL AND next IS NULL");
			my ($next) = dbh->selectrow_array("SELECT COUNT(*) FROM _$comic WHERE next IS NOT NULL AND prev IS NULL");
			my ($none) = dbh->selectrow_array("SELECT COUNT(*) FROM _$comic WHERE next IS NULL AND prev IS NULL");
			if ($none and $delnone) {
				dbh->do("DELETE FROM _$comic WHERE next IS NULL AND prev IS NULL");
				($none) = dbh->selectrow_array("SELECT COUNT(*) FROM _$comic WHERE next IS NULL AND prev IS NULL");
			}
			
			if (($prev != 1) || ($next != 1) || ($none  != 0)) {
				$ret .= Tr(
					td([a({-href=>"/tools/datalyzer/$comic"},"$comic"),
					($prev != 1)?"prev":'',
					($next != 1)?"next":'',
					($none != 0)?"none":'' 
					]));
			}
		}
		$ret .= end_table();
		return $ret . end_div . end_html;
	}
	
=head2 Comment

add some comment for the comic

=cut
		
		if ($tool eq 'comment') {
			my $res = make_head('Comment');
			$res .= start_div({-class=>'tools'});
			my $comment = param('comment') // dbh->selectrow_array("SELECT title FROM favourites WHERE comic=? AND file='comment' AND id IS NULL",undef,$comic);
			if (param('comment')) {
				unless (0 < dbh->do("UPDATE favourites SET title = ? WHERE comic=? AND file='comment' AND id IS NULL",undef,param('comment'),$comic)) {
					dbh->do("INSERT INTO favourites (title,comic,file) VALUES (?,?,'comment')",undef,param('comment'),$comic);
				}
			}
			$res .= start_form("GET","/tools/comment/$comic");
			$res .= 'enter comment here'.br;
			$res .= textarea(-name=>"comment", -rows=>4,-columns=>80,-default=>$comment) . br;
			#$res .= 'select hash key'.br;
			#$res .= textfield(-name=>"hashkey", -size=>"20");
			$res .= br . submit('ok');
			return $res . br . br .  a({-href=>"/index"},"Index") . end_div.end_html;
		}
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
