#!/dbcmc/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009
package Comic;

use 5.010;
use strict;
use warnings;


=head1 NAME

Comic.pm 

=head1 DESCRIPTION

This package is used to navigate inside the comic and mange the list of pages.

=cut

use Page;
use dbutil;
use dlutil;

#use Data::Dumper;
 
use URI;
use DBI;

our $VERSION;
$VERSION = '36';

=head1	General Methods

=head2 get_comic

	Comic::get_comic($hashref);

I<$hashref> is optional and will be directly passed to L</"new">

creates a new comic object, L</get_all> the new pages, L</release_pages>, maybe commits 
and disconnects the database handle and prints the log array to the log file.

returns: comic object on success C<0> otherwise.

=cut

sub get_comic {
	my $s = Comic->new(@_);
	if ($s) {
		$s->get_all;
		$s->release_pages;
		$s->dbh->commit unless $s->{autocommit};
		$s->dbh->disconnect unless $s->{dbh_no_disconnect};
		#flush out the log
		open(LOG,">>".$s->{path_log});
		print LOG join("\n",@{$s->{LOG}}) . "\n"; 
		close LOG;
		return $s;
	}
	else {
		return 0;
	}
}

=head2 new

	Comic->new($hashref)
	
I<$hashref> is optional and can contain the following keys:

=over 4

=item * C<DB> - path to the sqlite database. default: C<comics.db>

=item * C<path_strips> - path to the strips folder. default: C<./strips/>

=item * C<path_log> - path to the log file. default: C<log.txt>

=item * C<path_err> - path to the error log. default: C<err.txt>

=item * C<_CONF> - path to the config file. default: C<comic.ini>

=item * C<LOG> - arrayreference to store logged lines. default: emtpy arrayref

=item * C<dbh> - database hande object. default: creates a new database handle

=item * C<dbh_no_disconnect> - C<bool> database will not be disconnected at the end of get_comic if this is true. default: always true if C<dbh> is set undefined otherwise

=item * C<autocommit> - sets autocommit option if a new dbh is created. default: undefined

=item * C<config> - arrayref to comic config. default: loads config from file specified with C<_CONF>

=back

C<new> will make sure that the strip folder exists and L<dbutil/check_table> of the comic

returns: the comic object

=cut

sub new {
	my $class = shift;
	my $s = shift || {};
	bless $s,$class;
	
	$s->{DB} //= 'comics.db';
	$s->{path_strips} //= "./strips/";
	$s->{path_log} //= "log.txt";
	$s->{path_err} //= "err.txt";
	$s->{_CONF} //= 'comic.ini';
	$s->{time_to_stop} //= time + 60*60;
	
	$s->{LOG} = [];
	
	if ($s->{dbh}) {
		$s->{dbh_no_disconnect} = 1;
	}
	else {
		$s->{dbh} = DBI->connect("dbi:SQLite:dbname=".$s->{DB},"","",{AutoCommit => $s->{autocommit},PrintError => 1});
	}
	
	$s->status("-" x (10) . $s->name . "-" x (25 - length($s->name)),'UINFO');

	unless (-e $s->{path_strips} . $s->name) {
		mkdir $s->{path_strips} unless (-e $s->{path_strips} );
		mkdir($s->{path_strips} . $s->name);
		$s->status("WRITE: " . $s->{path_strips} . $s->name ,'OUT');
	}
	
	#dbutil::check_table($s->dbh,"_".$s->name);
	unless ($s->dbcmc('comic')) {
		$s->dbh->do('INSERT INTO comics (comic) VALUES (?)',undef,$s->name);
	}
	if (!$s->dbcmc('first') and !$s->dbcmc('url_current')) {
		$s->dbh->do('UPDATE comics  SET first = ? WHERE comic = ?',undef,$s->curr->strip(0)->id,$s->name);
	}
	
	return $s;
}

=head2 get_all

	$s->get_all();
	
get L<page/all_strips> of the L</curr> page.
checks for termination.
when not terminated will update the I<last_update> time.

returns: nothing (useful).

=cut

sub get_all {
	my $s = shift;
	$s->status("START: get_all",'DEBUG');
	my $last_strip = undef;
	while (!$::TERM) {
		$last_strip = $s->curr->all_strips($last_strip);
		return undef unless $last_strip;
		$s->write_url_current();
		if (time > $s->{time_to_stop}) {
			$s->status("STOPPED - timelimit reached",'UINFO');
			last;
		}
		last if $::TERM;
		last unless $s->get_next();
		$s->{acnt}++>30?(say$s->name())&&($s->{acnt}=0):undef; #announcing comic name every 30 lines
	};
	$s->dbcmc('last_update',time) unless $::TERM;
	$s->status("DONE: get_all",'DEBUG');
}

=head2 get_next

	$s->get_next();
	
tries L</goto_next> if that fails and we have 
I<archive_url> set in the config tries L</url_next_archive> and goes to the returned url.

returns: 1 if C<goto_next>; 2 if C<url_next_archive>; 0 otherwise

=cut

sub get_next {
	my $s = shift;
	if ($s->curr->url_next()) {
		if ($s->goto_next()) {
			return 1;
		}
		return 0;
	}
	elsif($s->ini("archive_url")) {
		my $url_archive = $s->url_next_archive();
		if ($url_archive) {
			return 2 if $s->goto_next($url_archive);
		}
	}
	return 0;
}

=head2 goto_next

	$s->goto_next($page_object_or_url);
	
I<$page_object_or_url> will be directly passed to L</next>.

sets L</prev> to L</curr>, aborts unless we have a next with body, sets L</curr> to L</next> and delets L</next>
the now current strip and the last strip are linked.
tries to set L<url_current>

returns: L<url_current> if set to the new url or C<1>

=cut

sub goto_next {
	my $s = shift;
	
	$s->prev($s->curr);
	return 0 unless ($s->next(@_) and $s->next->body());
	$s->curr($s->next());	#next page becomes current
	delete $s->{next};		#we delete original after copying
	
	# unless ($s->curr->strip(0)->id == $s->prev->strip(-1)->id) { #connecting last strip of previous page with first strip of current page
		# $s->curr->strip(0)->prev($s->prev->strip(-1));
		# $s->prev->strip(-1)->next($s->curr->strip(0));
	# }
	
	return ($s->url_current($s->curr->url()) or 1); #we return the url if it was set as current or true
}

=head1 page accessing Methods

=cut 

{ #page accessors

=head2 curr

	$s->curr($new_page_object_or_url)

I<$new_page_object_or_url> changes curr to given page object or creates new object with given url.

creates new page object with L<url_current> as url if undefined.

returns: current page object

=cut 

sub curr {
	my ($s,$ref) = @_;
	if ($ref) {
		if (ref $s->{curr}) {
			$s->{curr}->release_strips;
		}
		if (ref($ref) eq "Page") {
			$s->{curr} = $ref;
		}
		else {
			$s->{curr} = Page->new({"cmc" => $s,'url' => $ref});
		}
	}
	$s->{curr} //= Page->new({"cmc" => $s, "url" => $s->url_current});
	return $s->{curr};
}

=head2 prev

	$s->prev($new_page_object_or_url)

I<$new_page_object_or_url> changes prev to given page object or creates new object with given url.

creates new page object with current L<page/url_prev> as url if undefined.

returns: previous page object

=cut 

sub prev { 
	my ($s,$ref) = @_;
	if ($ref) {
		if (ref $s->{prev}) {
			$s->{prev}->release_strips;
		}
		if (ref($ref) eq "Page") {
			$s->{prev} = $ref ;
		} else {
			$s->{prev} = Page->new({"cmc" => $s,'url' => $ref});}
	}
	return $s->{prev} if $s->{prev};

	my ($url) =  $s->curr->url_prev();
	if ($url) { $s->{prev} = Page->new({"cmc" => $s,'url' => $url}); }
	else { $s->status("FEHLER kein prev: " . $s->curr->url,'ERR'); }
	return $s->{prev};
}

=head2 next

	$s->next($new_page_object_or_url)

I<$new_page_object_or_url> changes next to given page object or creates new object with given url.

gets new page with L</get_next_page> if no parameters given.

returns: next page object

=cut 

sub next {
	my ($s,$ref) = @_;
	if ($ref) {
		if (ref $s->{next}) {
			$s->{next}->release_strips;
		}
		(ref($ref) eq "Page") ? 
			$s->{next} = $ref :
			$s->{next} = Page->new({"cmc" => $s,'url' => $ref});
	}
	return $s->{next} if $s->{next};
	
	$s->{next} = $s->get_next_page();
	return $s->{next};
}

=head2 get_next_page

	$s->get_next_page();

gets urls with L<curr/url_next> uses some logic and returns the next page object.

returns: next page object

database access: READ _I<comic>

=cut 

sub get_next_page {
	my ($s) = shift;
	my @urls = $s->curr->url_next();
	return unless @urls;
	{ #lets do some counting!
	my %count;
	my @tmp_urls;
	#my ($maxname, $maxvalue) = ('',0);
	foreach my $url (@urls) {
		$count{$url} ++;
		push (@tmp_urls,$url)if ($count{$url} == 1); #we need the url just once
		#if ($maxvalue < $count{$url}) {
		#	$maxvalue = $count{$url};
		#	$maxname = $url;
		#}
	}#maxname should now be the url we have seen most and maxcount the number of times seen.
	#unshift(@urls,$maxname) if ($maxvalue > 1) # we add the most common value again, but this time at the beginning!
	@urls = sort {$count{$a} <=> $count{$b}} @tmp_urls; #we sort the array according to the number of counts
	}
	my $first_nondummy_page;
	url:foreach my $url (@urls) {
		if ($s->{visited_urls}->{$url}) {
			next;
		}
		#we check if there already is a strip with the next url. if so we check if the current url contains the previous strip of the next page. 
		#short: we check if that next has this curr as that prev.
		if (my $next_exists = $s->dbh->selectall_arrayref("SELECT id,prev,next FROM _" . $s->name . ' WHERE purl = "' . $url . '"',{Slice => {}})) { 
		if (@{$next_exists}) { #we dont need to check anything if the next page has no strips!
			my $back_link = 0;
			my $has_no_next = 0;
			foreach my $n_data (@{$next_exists}) {
				if ($n_data->{next}) {
					if ($n_data->{prev}) {
						if ($n_data->{prev} == $s->curr->strip(-1)->id) {
							$back_link = 1;
						}
					}
					else {
						my $first = $s->dbcmc('first');
						if ($first == $n_data->{id}) {
							next(url);
						}
						$back_link = 1; #no prev is a joker
					}
				}
				else {
					$has_no_next=1;
				}
			}
			if ($has_no_next) {
				$s->status("WARNING: found next ($url) without next, that might be okay? we could also delete it!",'WARN');
				#$s->dbh->do('DELETE FROM _'.$s->name . ' WHERE purl = ? ',undef,$url);
			}
			elsif (!$back_link) {
				$s->status("WARNING: found next ($url) that already exists and does not link back!",'WARN');
				next(url);
			}
		}
		}
		my $tmp_page = Page->new({"cmc" => $s,'url' => $url});
		$first_nondummy_page //= $tmp_page unless ($tmp_page->dummy());
		my @tmp_prev = $tmp_page->url_prev();
		foreach my $prev (@tmp_prev) {
			if ($prev eq $s->curr->url()) { #if the page has a link back to the current page its a good bet that its realy the next page!
				$s->{visited_urls}->{$url} = 1;
				return $tmp_page;
			}
		}
		$tmp_page->release_strips();#we dont want to leak memory
	}
	#so we had no previous matches huh? what now? maybe we just return a page where we found a strip!
	if ($first_nondummy_page) { #we can only do that if such a page exists!
		$s->status("no next with matching prev link found! using page with strip found!",'WARN'); # but we emit a warning first!
		$s->{visited_urls}->{$first_nondummy_page->url()} = 1; #and we also set the url as visited!
		return $first_nondummy_page;
	}
	return undef; 
}


}

=head1 archive Methods

=cut

{ #archive wrapper

=head2 url_next_archive

gets C<u_get_next_archive> 

returns: next page url

=cut

sub url_next_archive {
	my ($s) = @_;
	my $next_archive = $s->u_get_next_archive();
	return 0 unless $next_archive;
	$s->status("NEXT ARCHIVE: " . $next_archive , 'UINFO');
	$s->dbcmc('archive_current',$next_archive);
	$next_archive =~ s!([^&])&amp;|&#038;!$1&!gs;
	my $url_arch = URI->new($next_archive)->abs($s->ini("archive_url"))->as_string;
	my $reg_deeper = $s->ini('archive_regex_deeper');
	unless ($reg_deeper) {
		return $url_arch;
	}
	$url_arch .= '/' unless $url_arch =~ m#/$|\.\w{3,4}$#; #ugly fix | workaround warning
	$s->status("NEXT ARCHIVE deeper, get body: ". $url_arch, 'UINFO');
	my $res = dlutil::get($url_arch);
	if ( $res->is_success() ) {
		my $body = $res->content;
		if ($body =~ m#$reg_deeper#is) {
			my $deep_url = URI->new($+{url} // $1)->abs($url_arch)->as_string;
			$s->status("NEXT ARCHIVE deeper: " .$deep_url, 'UINFO');
			return $deep_url;
		}
	}
	else {
		$s->status('ERR: could not get body ' . $url_arch . ' for archive deeper: ' . $res->status_line() , 'ERR' );
		return 0;
	}
	return undef;
}

=head2 u_get_next_archive

gets C<ar_get_archives>
returns: next url found in archive

=cut

sub u_get_next_archive {
	my $s = shift;
	my @archives = @{$s->ar_get_archives()};
	$s->status("ARCHIVE count: " . scalar(@archives),"UINFO") unless $s->{info_arch_count};
	$s->{info_arch_count} = 1;
	return 0 unless @archives;
	my $arch_curr = $s->dbcmc('archive_current');
	return $archives[1] unless ($arch_curr);
	for (my $i = 0;$i <= $#archives;$i++) {
		if ($archives[$i] eq $arch_curr) {
			return $archives[$i + 1];
		}
	}	
	return 0;
}

=head2 ar_get_archives

gets body from C<$s->ini('archive_url')> matches aganinst C<$s->ini('archive_regex')> reverses list if C<$s->ini('archive_reverse')>

returns: list of archives as arrayref

=cut

sub ar_get_archives {
	my $s = shift;
	return $s->{archives} if $s->{archives};
	my $res = dlutil::get($s->ini('archive_url'));
	if ($res->is_error) {
		$s->status('ERROR: could not get body '. $s->ini('archive_url') . ' ' . $res->status_line(), 'ERR');
		return undef;
	}
	my $body = $res->content();
	my $regex = $s->ini('archive_regex');
	my @archives;
	while ($body =~ m#$regex#gis) {
		push(@archives,$+{url} // $1) unless ($s->ini('archive_reverse'));
		unshift(@archives,$+{url} // $1) if ($s->ini('archive_reverse'));
	}
	$s->{archives} = \@archives;
	return $s->{archives};
}

}

=head1 accessor and utility Methods

=cut

{ #accessors and utilitys

=head2 dbh 
	
	$s->dbh();
	
returns: the database handle

=cut

sub dbh {
	my $s = shift;
	return $s->{dbh};
}

=head2 ini 
	
	$s->ini($key,$value);
	
loads config and sets defaults (L<class_change>)

sets C<$key> to C<$value> if C<$value>

returns: value of C<$key>

=cut

sub ini { #gibt die ini des aktuellen comics aus # hier sollten nur nicht veränderliche informationen die zum download der comics benötigt werden drinstehen
	my $s = shift;
	my ($key,$value) = @_;
	
	unless ($s->{config}) {
		die "no config file ". $s->{_CONF} unless(-e $s->{_CONF});
		my $config = dbutil::readINI($s->{_CONF});
		die "no config for ". $s->name ." in ". $s->{_CONF} unless $config->{$s->name};
		$s->status("LOAD: config: ".$s->{_CONF} ,'IN');
		$s->{config} = $config->{$s->name};
	}
	
	$s->class_change;
	
	$s->{config}->{$key} = $value if $value;
	return $s->{config}->{$key};
}

=head2 class_change 
	
	$s->class_change();

sets default config values for known hosts
	
returns: noting (useful)

database access: READ _CONF, WRITE _CONF

=cut

sub class_change {
	my $s = shift;
	return if $s->{class_change};
	$s->{class_change} = 1;
	
	given ($s->{config}->{url_start}) {
		when (m#^http://www.anymanga.com/#) {$s->{config}->{class} //= "anymanga"};
		when (m#^http://www.mangafox.com/#) {$s->{config}->{class} //= "mangafox"};
		when (m#^http://manga.animea.net/#) {$s->{config}->{class} //= "animea"};
		when (m#^http://www.onemanga.com/#) {$s->{config}->{class} //= "onemanga"};
		when (m#^http://(www.)?cartooniverse.\w+.co.uk/#) {$s->{config}->{class} //= "cartooniverse"};
		when (m#^http://\w+.comicgenesis.com/#) {$s->{config}->{class} //= "comicgenesis"};
	}
	
	if ($s->{config}->{class}) {
		if ($s->{config}->{class} eq "onemanga") {
			$s->{config}->{regex_next} //= q#if \(keycode == 39\) {\s+window.location = '([^']+)'#;
			$s->{config}->{regex_prev} //= q#if \(keycode == 37\) {\s+window.location = '([^']+)'#;
			$s->{config}->{regex_strip_url} //= q#<img class="manga-page" src="([^"]+)"#;
			$s->{config}->{rename} //= q"strip_url#(\d+)/([^/]+)\.#01";
			$s->{config}->{rename_depth} //= 2; 
			$s->{config}->{worker} //= 0;
		}
		if ($s->{config}->{class} eq "animea") {
			$s->{config}->{regex_next} //= q#value="Next"\s*onClick="javascript:window.location='([^']+)'" />#;
			$s->{config}->{regex_prev} //= q#value="Previous"\s*onClick="javascript:window.location='([^']+)'" />#;
			$s->{config}->{heur_strip_url} //= "/tobemoved/|/data/";
		}
		if ($s->{config}->{class} eq "cartooniverse") {
			$s->{config}->{regex_next} //= q#<input value="next" onclick="javascript:window.location='([^']+)';" type="button">#;
			$s->{config}->{regex_prev} //= q#<input value="back" onclick="javascript:window.location='([^']+)';" type="button">#;
			$s->{config}->{heur_strip_url} //= q#img.cartooniverse#;
			$s->{config}->{rename} //= q"strip_url#(\d+)/([^/]+)\.#01";
			$s->{config}->{rename_depth} //= 2;
			$s->{config}->{referer} //= q#http://www.cartooniverse.co.uk/#;
			$s->{config}->{worker} //= 0;
		}
		if ($s->{config}->{class} eq "mangafox") {
			#my $url_insert = $s->{config}->{url_start};
			#my $str = q"/chapter.{{chap}}/page.{{page}}/";
			#$url_insert =~ s#/chapter\..+$#$str#egi;
			#$s->{config}->{list_url_regex} //= q#/chapter.(?<chap>\d+)/page.(?<page>\d+)/#;
			#$s->{config}->{list_url_insert} //= $url_insert;
			#$s->{config}->{list_chap_regex} //= q#<option value="(\d+)"\s*(?:selected="?selected"?)?>\s*[^<]+(?:vol|ch)[^<]+</option>#;
			#$s->{config}->{list_page_regex} //= q#<option value="(\d+)"\s*(?:selected="?selected"?)?>\d+</option>#;
			my $archive_url = $s->{config}->{url_start};
			if ($archive_url =~ m#^http://www.mangafox.com/page/manga/read/\d+/#i) {
				$archive_url =~ s#^(http://www.mangafox.com/)page/(manga)/read/\d+(/[^/]+/).*$#$1$2$3?no_warning=1#i;
			}
			elsif ($archive_url =~ m#^http://www.mangafox.com/manga/#i) {
				$archive_url =~ s#^(http://www.mangafox.com/manga/[^/]+/).*$#$1?no_warning=1#i;
			}
			$s->{config}->{archive_url} //= $archive_url;
			$s->{config}->{archive_regex} //= q#edit</a>\s+<a href="(?<url>[^"]+)" class="chico">#;
			$s->{config}->{archive_reverse} //= 1;
			$s->{config}->{heur_strip_url} //= q#compressed#;
			$s->{config}->{worker} //= 0;
			$s->{config}->{referer} //= '';
			$s->{config}->{rename} //= q'url_only#^\D+$|^(\d\d_)?\d\d\.\w{3,4}$#/chapter\.(\d+)/page\.(\d+)/#';
			$s->{config}->{regex_never_goto} //= q#/end/#;
		}
		if ($s->{config}->{class} eq "anymanga") {
			$s->{config}->{heur_strip_url} //= '/manga/[^/]+/\d+/\d+';
			$s->{config}->{rename} //= q"strip_url#manga/([\w-]+)/(\d+)/(\d+)/([^\.]+)\.\w{3,4}#0123";
			$s->{config}->{rename_depth} //= 4;
			$s->{config}->{regex_prev} //= q"var url_back = '([^']+)';";
			$s->{config}->{regex_next} //= q"var url_next = '([^']+)';";
		}
		if ($s->{config}->{class} eq "comicgenesis") {
			$s->{config}->{worker} //= 0;
			$s->{config}->{url_start} =~ m#(^http://\w+.comicgenesis.com/)#;
			$s->{config}->{referer} //= $1;
		}
	}
}

=head2 dbcmc 
	
	$s->dbcmc($key,$value);

accesses the C<comics> table

if C<$value> is C<defined> updates C<$value>
	
returns: value of C<$key>

database access: READ comics, WRITE comics

=cut

sub dbcmc { #gibt die aktuellen einstellungen des comics aus # hier gehören die veränderlichen informationen rein, die der nutzer auch selbst bearbeiten kann
	my $s = shift;
	my ($key,$value) = @_;
	if (defined $value) {
		my $sth = $s->dbh->prepare("UPDATE comics SET $key = ? WHERE comic=?");
		$sth->execute($value,$s->name);
	}
	my $sth = $s->dbh->prepare("SELECT $key FROM comics WHERE comic=?");
	$sth->execute($s->name);
	return $sth->fetchrow_array();
}

=head2 dbstrps  
	
	$s->dbstrps($get,$key,$select,$value);

accesses the C<_I<comic>> table

where column C<$get> equals C<$key>:
if C<$value> is C<defined> updates C<$select> to C<$value>
	
returns: value of C<$select> where column C<$get> equals C<$key>

database access: READ _I<comic>, WRITE _I<comic>

=cut

sub dbstrps { #gibt die dat und die dazugehörige configuration des comics aus # hier werden alle informationen zu den strips gespeichert
	my $s = shift;
	my ($get,$key,$select,$value) = @_;
	my $c = $s->name;

	if (defined $value) {
		my $sth = $s->dbh->prepare(qq(UPDATE _$c SET $select = ? WHERE $get = ?));
		$sth->execute($value,$key);
	}
	my $sth = $s->dbh->prepare(qq(SELECT $select FROM _$c WHERE $get = ?));
	$sth->execute($key);
	return $sth->fetchrow_array();
}

=head2 name  
	
	$s->name();

returns: name of the comic

=cut

sub name {
	my $s = shift;
	return $s->{name};
}

=head2 url_home  
	
	$s->url_home();
	
extracts C<url_home> from C<url_start>

returns: C<url_home>

=cut

sub url_home {
	my $s = shift;
	unless($s->ini('url_home')) {
		my $uri = URI->new($s->ini('url_start'));
		my $p = $uri->path_query;
		my $u = $uri->as_string;
		$u =~ m#(.+)\Q$p\E#; #removes path from url
		$s->ini('url_home',$1."/");
		$s->status("DEF: url_home: " . $1 . "/", 'DEBUG');
	}
	return $s->ini('url_home');
}

=head2 url_current  
	
	$s->url_current($url);
	
C<$url> sets C<url_current> to C<$url> but makes sure that the formatting is correct 
and we are not setting it to something we dont want to go to (not/never_gotos, main/index pages or pages without strips
extracts C<url_current> from C<url_start> if necessesary
	
returns: C<url_current>

=cut

sub url_current {
	my ($s,$url) = @_;
	if ($url) {
		my ($curl) = ($url =~ m#https?://[^/]+(/.*)$#i);
		if ($curl) {
			my $regex_not_goto = $s->ini("regex_not_goto");
			$s->{not_goto} = 1 if ( 
				($regex_not_goto and ($curl =~ m#$regex_not_goto#i)) or 
				($curl =~ m#(index|main)\.(php|html?)$#i) or 
				($curl =~ m:#$:) or
				($curl =~ m:^/$:)
			);
			unless ($s->{not_goto} or $s->curr->dummy) {
				$s->{last_url_current} = $url;
				#$s->status("URL_CURRENT: ". $url ,'DEBUG');
			}
			$s->{url_current} = $url;
		}
	}
	
	return $s->{url_current} if $s->{url_current};
	
	
	$s->{url_current} = $s->dbcmc('url_current') || $s->ini('url_start');
	$s->status("SET: url_current: " . $s->{url_current}, 'DEF');
	
	return $s->{url_current};
}

sub write_url_current {
	my $s = shift;
	$s->dbcmc('url_current',$s->{last_url_current}) if $s->{last_url_current};
}

=head2 status

	$s->status($status,$type,$addinfo);
	
C<$status> text to print to screen/log

C<$typ> can be ERR (printed to error file log and screen), WARN, DEF, UINFO (screen and log) or DEBUG (log only)

C<$addinfo> depreceated

returns: C<1>

database access: none

=cut

sub status {
	my $s = shift;
	my $status = shift;
	my $type = shift;
	my $addinfo = shift;
	
	push (@{$s->{LOG}}, $status . ($addinfo ? " -- >". $addinfo : "") . " -->". $type);
	
	if ($type =~ m/ERR|WARN|DEF|UINFO/i) {
		print $status . "\n";
	}
	
	if ($type  =~ m/ERR/i) {
		open(ERR,">>".$s->{path_err});
		my @time = localtime();
		print ERR $s->name() . sprintf(":%04d-%02d-%02d",$time[5]+1900,$time[4]+1,$time[3]) .">$type: " . $status ." -- >". ($addinfo // ""). "\n";
		close ERR;
	}
	return 1;
}

=head2 release_pages

	$s->release_pages();
	
deletes prev, curr and next (memory management)

returns: nothing (useful) 

=cut

sub release_pages {
	my $s = shift;
	$s->{prev}->release_strips if $s->{prev};
	$s->{curr}->release_strips if $s->{curr};
	$s->{next}->release_strips if $s->{next};
	delete $s->{prev};
	delete $s->{curr};
	delete $s->{next};
	sleep(1);
}

sub DESTROY {
	my $s = shift;
	$s->status('DESTROYED: '. $s->name,'DEBUG');
}

}

1;
