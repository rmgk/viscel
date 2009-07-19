#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009
package Strip;

use 5.010;
use strict;
use warnings;


=head1 NAME

Strip.pm 

=head1 DESCRIPTION

This package is used to organize files and dates of a strip.

=cut

use dlutil;

use Digest::SHA;
use Scalar::Util;
use Time::HiRes;


our $VERSION;
$VERSION = '20';

our $Strips = 0;

our $SHA = Digest::SHA->new();

=head1 general Methods

=head2 new

	Strip->new($hashref);

I<$hashref> needs the following keys:

=over 4

=item * C<page> - the page object. required

=item * C<url> - the url of the strip. required

=back

returns: the newly blessed strip object

=cut

sub new {
	my $class = shift;
	my $s = shift;
	bless $s,$class;
	$s->check_id;
	$Strip::Strips++;
	$s->status("NEW STRIP: ($Strip::Strips) ".($s->dummy?'dummy'.$s->id:$s->url),'DEBUG');
	Scalar::Util::weaken($s->{page});
	return $s;
}

sub check_id {
	my $s = shift;
	if ($s->dummy) {
		$s->dbh->{AutoCommit} = 0;
		$s->dbh->do('INSERT INTO _'. $s->name .' DEFAULT VALUES');
		$s->{id} = $s->dbh->last_insert_id(undef,undef,'_'.$s->name,'id');
		$s->dbh->{AutoCommit} = 1;
		return -1;
	}
	my $strip_path = $s->url_path;
	$strip_path = '%'. $strip_path;
	my $eid = $s->dbh->selectrow_array('SELECT id FROM _'.$s->name.' WHERE surl like ?',undef,$strip_path);
	if ($eid) {
		my $db_strip = $s->dbh->selectrow_hashref('SELECT * FROM _'.$s->name.' WHERE id = ?',undef,$eid);
		my $epurl =	$s->url_path($db_strip->{purl});
		my $purl = $s->url_path($s->page->url);
		$epurl =~ s/\?.+$//; # removing scripts and such
		$purl =~ s/\?.+$//;
		
		if ($epurl eq $purl) {
			$s->{id} = $db_strip->{id}; 
			$s->{prev} = $db_strip->{prev}; 
			$s->{next} = $db_strip->{next}; 
			$s->{number} = $db_strip->{number}; 
			$s->{title} = $db_strip->{title}; 
			$s->{sha1} = $db_strip->{sha1}; 
			$s->{file_name} = $db_strip->{file};
			$s->status('Loaded ' .$s->{file_name} . ' info from database','UINFO' );
			
			return 2;
		}
	}
	$s->dbh->{AutoCommit} = 0;
	$s->dbh->do('INSERT INTO _'. $s->name .' DEFAULT VALUES');
	$s->{id} = $s->dbh->last_insert_id(undef,undef,'_'.$s->name,'id');
	$s->dbh->{AutoCommit} = 1;
	return 1
}

sub prev {
	my $s = shift;
	my ($o) = @_;
	if ($o) {
		if (ref $o) {
			$o = $o->id;
		}
		if ($o == $s->id) {
			$s->status("ERROR: tried to set prev of (".$s->url.") to itself at". join(" ",caller) , 'ERR');
			return $s->{prev};
		}
		if ($s->{prev}) {
			if ($s->{prev} != $o) {
				$s->status("ERROR: tried to set prev of (".$s->url.") to $o but it is already " .$s->{prev} . " at ". join(" ",caller) , 'ERR');
				return $s->{prev};
			}
		}
		else {
			$s->{prev} = $o;
			if ($s->{is_commited}) { 
				$s->dbstrps(id=>$s->id,prev=>$s->{prev});
				$s->status("COMMITED prev $o , ".($s->url//$s->id),'DEBUG');
			}
		}
	}
	return $s->{prev}
}

sub next {
	my $s = shift;
	my ($o) = @_;
	if ($o) {
		if (ref $o) {
			$o = $o->id;
		}
		if ($o == $s->id) {
			$s->status("ERROR: tried to set next of (".$s->url.") to itself at". join(" ",caller) , 'ERR');
			return $s->{next};
		}
		if ($s->{next}) {
			if ($s->{next} != $o) {
				if ($s->dbstrps(id=>$s->{next},'next')) { #next of next is also set everythings fine
					$s->status("ERROR: tried to set next of (".$s->url.") to $o but it is already " .$s->{next} . " at ". join(" ",caller) , 'ERR');
					return $s->{next};
				}
				else { #next of next is not set so next is last so we can change this and delete the next
					$s->status("WARNING: changed next of prev of last, delete last ". $s->{next}, 'DEBUG');
					$s->dbh->do('DELETE FROM _'.$s->page->name . ' WHERE id = ?',undef,$s->{next});
					$s->{next} = $o;
				}
			}
		}
		else {
			$s->{next} = $o;
			if ($s->{is_commited}) { 
				$s->dbstrps(id=>$s->id,next=>$s->{next});
				$s->status("COMMITED next $o to ".($s->url//$s->id),'DEBUG');
			}
		}
	}
	
	return $s->{next}
}

sub get_data {
	my $s = shift;
	return -1 if $s->dummy(); 
	return 0 unless $s->download();
	$s->title();
	return 1;
}

sub commit_info {
	my $s = shift;
	if ($s->{is_commited}) {
		$s->status("ERROR: already commited: ".$s->url,'ERR');
		return 0;
	}
	if ($s->dummy()) {
		my $sth =  $s->dbh->prepare('UPDATE _'.$s->name .' SET purl=?,next=?,prev=?,number=? where id == ?');
		$sth->execute($s->page->url,$s->next,$s->prev,$s->number,$s->id);
		$s->{is_commited} = 1;
		return -1;
	}
	$s->page->cmc->{sqlstr_title_update} //= $s->dbh->prepare('UPDATE _'.$s->name .' SET title=?,purl=?,surl=?,next=?,prev=?,number=?,time=?,file=?,sha1=? where id == ?');
	$s->page->cmc->{sqlstr_title_update}->execute($s->title,$s->page->url,$s->url,$s->next,$s->prev,$s->number,time,$s->file_name,$s->sha1,$s->id);
	#$s->dbh->commit;
	$s->{is_commited} = 1;
	return 1;
}


=head2 download

	Strip->download();

does some voodoo TODO

returns: undef on error, 1 if successful, 2 if exists

=cut

sub download {
	my $s = shift;
	return -1 if (!$s->url);
	if ($s->sha1) {
		$s->status("EXISTS: ".$s->file_name,'UINFO');
		return 2;
	}
	unless ($s->file_name) {
		$s->status('ERROR could not get filename: ' . $s->{gfn_error} , 'ERR');
		return undef;
	}
	my $osurl = $s->dbstrps(file=>$s->file_name,'surl');
	if ((defined $osurl) && ($s->url_path($osurl) ne $s->url_path)) {

		$s->status("WARN: file name '".$s->file_name."' already used ($osurl)",'WARN');

		my $new_depth = $s->handle_equal_filenames($osurl,$s->url);
		unless ($new_depth) {
			$s->status('ERROR: could not find new filename depth '. $s->url,'ERR');
			return undef;
		}
		$s->file_name($s->get_file_name($s->url,$new_depth));
		$s->status("WARN: new depth $new_depth filename: " . $s->file_name ,'WARN');
		$s->filename_depth($new_depth);
		return $s->download();
	}
	
	if (-e $s->file_path) {
		$s->status("EXISTS on disk: ".$s->file_name,'UINFO');
		unless ($s->sha1) {
			$s->sha1($SHA->addfile($s->file_path,"b")->hexdigest);
		}
		return 2;
	}
	
	my $home = $s->page->cmc->url_home();
	$s->page->url =~ m#(?:$home)?(.+)#;
	my $se_url = $1;
	local $| = 1; #dont wait for newline to print the text
	print "GET: " . $se_url . " => " . $s->file_name;
	$s->status("GET: ".$s->file_name." URL: " . $s->page->url ." SURL: " .$s->url,"DEBUG");
	return $s->_download();
}

sub handle_equal_filenames {
	my ($s, $sA , $sB) = @_;
	
	my $depth = $s->filename_depth;
	my $diff_names = 0;
	my ($fnA , $fnB);
	do {
		$depth++;
		$fnA = $s->get_file_name($sA,$depth);
		return undef if $s->{gfn_error};
		$fnB = $s->get_file_name($sB,$depth);
		return undef if $s->{gfn_error};
	} while ($fnA eq $fnB);
	
	return $depth;
}

=head2 _download

	Page->_download($surl,$file_name);

C<$surl> is the url of the file to download. required
C<$file_name> is the name the file will be named on disk. required

downloads the file with L<dlutil/getref>
creates md5 and sha1 hashes and saves the file to disk

returns: 0 if the download threw an error and 1 if the download was successful

=cut

sub _download {
	my ($s) = @_;
	my $time = Time::HiRes::time;
	my $img_res = dlutil::get($s->url,$s->page->url);
	$time = Time::HiRes::time - $time;
	if ($img_res->is_error) {
		say " error"; #were waiting for speed and newline
		$s->status("ERROR downloading ".$s->file_name." code: " .$img_res->status_line(),"ERR");
		$s->{error_download} = $img_res->status_line();
		if ($img_res->code() == 404) {
			return -2;
		}
		return 0;
	}
	if (open(my $fh,'>'.$s->file_path)) {
		binmode $fh;
		my $img = \$img_res->decoded_content(raise_error=>1);
		$s->sha1($SHA->add($$img)->hexdigest());
		print $fh $$img;
		say " (".int((-s $fh)/($time*1000)) ." kb/s)";
		close $fh;
		$s->dbcmc('last_save',time);
	}
	else {
		say " error"; #were waiting for speed and newline
		$s->status("ERROR: could not open ". $s->file_path,'ERR');
		return undef
	}
	return 1;
}

sub file_name {
	my $s = shift;
	my $nfn = shift;
	$s->{file_name} = $nfn if $nfn;
	return $s->{file_name} if $s->{file_name};
	$s->{file_name} = $s->get_file_name($s->url);
	return $s->{file_name} ;
}

=head2 get_file_name

	$s->get_file_name($strip_url,$depth);
	
takes I<$strip_url> and extracts the file name. 
I<$depth> is optional ans specifies how much of the url is part of the filename.
	
returns: file name

database access: READ ini (not always)

=cut

sub get_file_name {
	my ($s,$url,$depth) = @_;
	$s->{gfn_error} = undef;
	unless ($url) {
		$s->{gfn_error} = "no url";
		return undef;
	}
 	$depth ||= $s->filename_depth;
	my $surl = $url;
	$surl =~ s#^\w+://##; #remove protokoll

	my @surl = split( '/' , $surl); 
	
	if ($depth > @surl) {	
		$s->{gfn_error}="depth overflow";
		return undef;
	}
	
	my $filename;
	
	my $bc = '/?&=#'; #bad characters
	
	my $part = pop @surl;
	
	if ($part =~ m#(?:^|.*[$bc])([^$bc]+\.(jpe?g|gif|png|bmp|swf|dcr))#i) { # line start or invalid char til filetype
		$filename = $1;
	}
	else {
		my $header_res = dlutil::gethead($url,$s->page->url);
		unless ($header_res->is_success()) {
			$s->{gfn_error} = "header failure " .  $header_res->status_line();
			return undef;
		}
		my $filetype = $header_res->header('Content-Type');
		if ($filetype =~ m#^image/(\w+)$#) {
			$filetype = '.' . $1;
		}
		else {
			$s->{gfn_error} = "could not get image format from header: " . $header_res->header('Content-Type');
			if ($part =~ m#\b(jpe?g|gif|png|bmp|swf|dcr)\b#i) {
				$filetype = '.' . $1;
				$s->status("WARN: guessed filetype $1 for $part",'DEBUG');
			}
			else{
				$s->status("WARN: could not find filetype for $part",'WARN');
			}
		}
		if ($part =~ m#^[^$bc]+$#) {
			$filename = $part . $filetype;
		}
		elsif ($part =~ m#=(\d{4,})(\D|$)#) {
			$filename = $1 . $filetype;
		}
		elsif ($part =~ m#=(\d+)(\D|$)#) {
			$filename = $1 . $filetype;
		}
		else {
			$s->{gfn_error} = "no match";
			return undef;
		}
	}
	while(--$depth>0) {
		$part = pop @surl;
		#$part =~ s#[^/?&=]##g; #removing invalid chars
		$filename = $part . $filename;
	}
	
	if ($s->ini('rename_substitute')) {
		my ($re,$sub) =  split(/#/, $s->ini('rename_substitute'), 2);
		$filename =~ s#$re#$sub#gi;
	}
	
	return $filename;
}

=head2 title

	Page->title($surl);

C<$surl> is the url of the strip. required

uses L<get_file_name> and L<url> and L<body> to get information about the comic to store in the database.

returns: the created title string or undef if there is no body

database access: READ ini, WRITE _comic

=cut

sub title {
	my $s = shift;
	return $s->{title} if $s->{title};
	my %titles;
	my $surl = $s->url;
	my $file = $s->file_name;
	my $url = $s->page->url();
	my $body = $s->page->body();
	return unless $body;
	my ($urlpart) = ($surl =~ m#.*/(.*)#);
	
	my $regex_title = $s->ini('regex_title');
	my @ut = ($body =~ m#$regex_title#gis) if ($regex_title);
	$body =~ m#<title>([^<]*?)</title>#is;
	my $st = $1;
	

	my $it;
	my $ia;
	if ($urlpart) {
		my $img;
		if ($body =~ m#(<img[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
			$img = $1;
		}
		if ($img) {
			if ($img =~ m#title=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
				$it = $1;
			}
			if ($img =~ m#alt=["']?((:?[^"']*?(?:\w'\w)?)+)(?:[^\w]'[^\w]|"|>)#is) {
				$ia = $1;
			}
		}
	}

	my $ew;
	my $eh;
	if (my $re_em = $s->ini('regex_embed')) {
		if ($body =~ m#$re_em#is) {
			$ew = $+{width};
			$eh = $+{height};
			$titles{et} = $+{type};
		}
	}
	elsif ($urlpart) {
		my $embed;
		if ($body =~ m#(<embed[^>]*?src=["']?[^"']*?$urlpart(?:['"\s][^>]*?>|>))#is) {
			$embed = $1;
		}
		if ($embed) {
			if ($embed =~ m#width=["']?\s*(\d+)#is) {
				$ew = $1;
			}
			if ($embed =~ m#height=["']?\s*(\d+)#is) {
				$eh = $1;
			}
			if ($embed =~ m#type\s*=\s*["']\s*([^"']+?)\s*["']#is) {
				$titles{et} = $1;
			}
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
	
	$titles{ut} = $ut; $titles{st} = $st; $titles{it} = $it; $titles{ia} = $ia; 
	$titles{h1} = $h1; $titles{dt} = $dt; $titles{sl} = $sl; $titles{ew} = $ew; $titles{eh} = $eh; 
	
	my $title_string = '{' . (join ',' , map {"$_=>q($titles{$_})"} grep {defined $titles{$_} } keys %titles) .'}';
	#my $title_string = "{ut=>q($ut),st=>q($st),it=>q($it),ia=>q($ia),h1=>q($h1),dt=>q($dt),sl=>q($sl)}";
	#ut - user title; st - site title; it - image title; ia - image alt; h1 - head 1; dt - div title ; sl - selected title;
	
	$s->status("TITLE $file: " . $title_string,'DEBUG');
	$s->{title} = $title_string;
	return $title_string; 
}



{ #utils and accessor

sub id {
	my $s = shift;
	return $s->{id} if defined $s->{id};
	$s->status("ERROR: ID not defined: ".join(":",caller)." " . $s->url, 'ERR');
	return undef;
}

sub number {
	my $s = shift;
	unless ($s->{number}) {
		if ($s->prev) {
			my $pnum = $s->dbstrps(id=>$s->prev,'number');
			$s->{number} = $pnum + 1;
		}
		else {
			$s->{number} = 1;
		}
	}
	return $s->{number} ;
}

sub file_path {
	my $s = shift;
	return "./strips/".$s->page->name."/".$s->file_name;
}

sub url_path {
	my $s = shift;
	my $url = shift // $s->url;
	if ($url =~ m#^https?://[^/]+(/.*)$#i) {
		return $1;
	}
	else {
		return $url;
	}
}

sub sha1 {
	my $s = shift;
	my ($sha) = @_;
	if ($sha) {
		if ($s->{sha1} and ($s->{sha1} != $sha)) {
			$s->status("ERROR: tried to set existing sha1 hash do different value ". $s->url,'ERR')
		}
		else {
			$s->{sha1} = $sha;
		}
	}
	return $s->{sha1};
}

sub filename_depth {
	my $s = shift;
	$s->{filename_depth} = $_[0] if @_;
	return $s->{filename_depth} || $s->ini('rename_depth') || 1;
	
}

sub url {
	my $s = shift; 
	return $s->{url};
}

sub dummy {
	my $s = shift; 
	$s->{dummy} = $_[0] if @_;
	return $s->{dummy};
}


sub status {
	my $s = shift;
	$s->page->status(@_);
}

sub page {
	my $s = shift;
	return $s->{page};
}

sub ini {
	my $s = shift;
	return $s->page->ini(@_);
}

sub dbstrps {
	my $s = shift;
	return $s->page->dbstrps(@_);
}

sub dbcmc {
	my $s = shift;
	return $s->page->dbcmc(@_);
}

sub name {
	my $s = shift;
	return $s->page->name;
}

sub dbh {
	my $s = shift;
	return $s->page->cmc->dbh;
}

sub DESTROY {
	my $s = shift;
	
	my $db = $s->dbh->selectrow_hashref('SELECT * FROM _'.$s->name.' WHERE id = ?',undef,$s->id);
	unless (((defined $db->{file}) + (defined $db->{next}) + (defined $db->{prev}))>1) {
		$s->dbh->do('DELETE FROM _' . $s->name . ' WHERE id = ?' , undef,$s->id);
		#$s->dbh->commit();
	}
	$Strip::Strips--;
	$s->status("DESTROYED: ($Strip::Strips) ". ($s->dummy?'dummy'.$s->id:$s->url),'DEBUG');
}

}

1;