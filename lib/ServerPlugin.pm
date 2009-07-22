#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *table :html3 *div gradient);
use DBI;

use dbutil;

use Exporter qw(import);

our @EXPORT = qw();
our @EXPORT_OK = qw(make_head tags flags dbcmcs dbstrps cache_strps dbh is_broken);


our $VERSION = '1.0.0';

my $dbh = DBI->connect("dbi:SQLite:dbname=comics.db","","",{AutoCommit => 1,PrintError => 1});
$dbh->func(300000,'busy_timeout');

my %broken; #we save all the comics marked as broken in comic.ini here

=head1 Main Methods

=head2 C<handle_request>

	handle_request($plugin,$connection,$request,@arguments);
	
calls C<get_response> and C<get_content> to send a response. 
you should overwrite it if you dont want to send a normal response.
(you want to redirect or send a file)

=cut

sub handle_request {
	my ($plugin,$connection,$request,@arguments) = @_;
	restore_parameters($request->url->query);
	my $res = $plugin->get_response();
	$res->request($request);
	$res->content($plugin->get_content(@arguments));
	$connection->send_response($res);
	#$connection->close;
}


=head2 C<get_response>

	$response = get_response($plugin);
	
returns the HTTP::Response object for the content;

=cut

sub get_response {
	return HTTP::Response->new( 200, 'OK', ['Content-Type','text/html; charset=iso-8859-1']); #our main response
}


=head2 C<get_content>

	$content = get_content($plugin,@arguments);
	
returnse the page content. you have to overwrite this in you plugin

=cut

sub get_content {
	my ($plugin,@arguments) = @_;
	return undef;
}

=head1 Utility Methods

theses methods are useful to most plugins

=head2 C<make_head>

	$head = make_head($title,$prev,$next,$first,$last,$prefetch,$onload);
	
creates a head with some default values, all parameters except $title are optional.
returns the html string with the head and the start of the body.

=cut

sub make_head {
	my $title = shift;
	my $prev = shift;
	my $next = shift;
	my $first = shift;
	my $last = shift;
	my $prefetch = shift;
	my $javascript = shift;
	
	return start_html(	-title=>$title. " - ComCol ht $VERSION" ,
						-style=>"/css/",
						-head=>[
									#Link({-rel=>"stylesheet", -type=>"text/css", -href=>}),
									Link({-rel=>'index',	-href=>"/index"	})			,
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


=head2 C<tags>

	@tags = tags($comic,$new_tags)

C<$new_tags> is a list of space separated lower case word characters and sets the tags to the given list.
if C<$new_tags> stars with a + or - the list given is added to or removed from the list of tags

returns tags of C<$comic> or a sorted list of all tags;

=cut

sub tags {
	my $comic = shift;
	my $new = shift;
	
	if ($comic) {
		my $otags = dbcmcs($comic,'tags') // '';
		
		my $otag = {};
		
		$otag->{lc $_} = 1 for(split(/\W+/,$otags));
		
		if (defined $new) {
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
			elsif($new eq '') {
				$dbh->do('UPDATE comics SET tags = NULL where comic = ?',undef,$comic );
				return undef;
			}
			
			my $ot = join(' ',keys %{$otag});
			if ($ot) { 
				dbcmcs($comic,'tags',$ot) ;
			}
			else {
				$dbh->do('UPDATE comics SET tags = NULL where comic = ?',undef,$comic );
				return undef;
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
		return sort {$a cmp $b} (keys %taglist);
	}
}

=head2 C<flags>

	$flags_hashref = flags($comic,$new_flags)

C<$new_flags> is the list of flags.
if C<$new_flags> stars with a + or - the list given is added to or removed from the list of tags

returns hashref with flags of C<$comic> as keys;

=cut

sub flags {
	my $comic = shift;
	my $new = shift;
	return 0 unless $comic;
	
	my $oflags = dbcmcs($comic,'flags') // '';
	
	my $oflag = {};
	
	$oflag->{$_} = 1 for(split(//,$oflags));
	
	if (defined $new) {
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
		elsif ($new eq '') {
			$dbh->do('UPDATE comics SET flags = NULL where comic = ?',undef,$comic );
			return undef;
		}
		my $of = join('',keys %$oflag);
		
		if ($of) {
			dbcmcs($comic,'flags',$of) 
		}
		else {
			$dbh->do('UPDATE comics SET flags = NULL where comic = ?',undef,$comic);
			return undef;
		}

	}
	return $oflag;
}


=head2 Database Accessors

	$data = dbcmcs($comic,$key,$value);
	$data = dbstrps($comic,$get,$key,$select,$value);
	
returns the requested database entry, emptys the cache if C<$value> was set

=cut

my %strpscache;

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

=head2 C<cache_strps>

	cache_strps($comic);
	
caches the database for C<$comic> unless it is cached. this should be done if there will be a lot of request for a single comic

=cut

sub cache_strps {
	my ($comic) = @_;
	return if $strpscache{comic} and ($comic eq $strpscache{comic});
	say "caching $comic";
	%strpscache = %{$dbh->selectall_hashref("SELECT * FROM _$comic",'id')};
	my $cmc = $dbh->selectrow_hashref("SELECT * FROM comics WHERE comic = ?",undef,$comic);
	$strpscache{$_} = $cmc->{$_} for keys %$cmc; 

}

sub dbh {
	return $dbh;
}

sub get_broken {
	my $comini = dbutil::readINI('comic.ini');

	foreach my $name (keys %{$comini}) {
		if ($comini->{$name}->{broken}) {
			$broken{$name} = 1;
		}
	}
}

sub is_broken {
	get_broken() unless %broken;
	return $broken{$_[0]};
}

1;
