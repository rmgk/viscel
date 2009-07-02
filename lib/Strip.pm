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

use Digest::SHA qw(sha1_hex);


our $VERSION;
$VERSION = '1';

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
	$s->status("NEW STRIP: ".$s->url,'DEBUG');
	return $s;
}

sub check_id {
	my $s = shift;
	my $eid = $s->dbstrps('surl' => $s->url , 'id');
	if ($eid) {
		my $db_strip = $s->dbh->selectrow_hashref('SELECT * FROM _'.$s->name.' WHERE id = ?',undef,$eid);
		my $epurl =	$db_strip->{purl};
		my $purl = $s->page->url;
		$epurl =~ s/\?.+$//; # removing scripts and such
		$purl =~ s/\?.+$//;
		
		if ($epurl eq $purl) {
			$s->{id} = $eid;
			$s->prev($db_strip->{prev});
			$s->prev($db_strip->{next});
			$s->file_name($db_strip->{file});
		}
	}
	else {
		$s->dbh->do('INSERT INTO _'. $s->name .' (surl) VALUES (?)');
		$s->{id} = $s->dbh->last_insert_id(undef,undef,'_'.$s->name,'id');
		$s->dbh->commit;
	}
}

sub prev {
	my $s = shift;
	my ($o) = @_;
	$s->{prev} = $o->id if $o;
	return $s->{prev}
}

sub next {
	my $s = shift;
	my ($o) = @_;
	$s->{next} = $o->id if $o;
	return $s->{next}
}

sub get_data {
	my $s = shift;
	$s->download();
	$s->title();
}

sub save_to_disk {
	my $s = shift;
	if (!$s->{need_save}) {
		$s->status("no save needed: ".$s->url,'DEBUG');
		return 2;
	}
	elsif (!$s->{file_blob}) {
		$s->status("ERROR: no file blob",'ERR');
		return undef;
	}
	elsif (open(my $fh,'>'.$s->file_path)) {
		binmode $fh;
		print $fh $s->{file_blob};
		say " (".int((-s $fh)/($s->{need_save})) ." kb/s)";
		close $fh;
	}
	else {
		$s->status("ERROR: could not open ". $s->file_path,'ERR');
		return undef
	}
	$s->page->cmc->{sqlstr_title_update} //= $s->dbh->prepare('UPDATE _'.$s->name .' SET title=?,purl=?,surl=?,time=?,file=? where id == ?');
	$s->page->cmc->{sqlstr_title_update}->execute($s->title,$s->page->url,$s->url,time,$s->file_name,$s->id);
	$s->dbh->commit;
	return 1;
}


=head2 download

	Strip->download();

does some voodoo TODO

returns: undef on error, 1 if successful, 2 if exists

=cut

sub download {
	my $s = shift;
	#return -1 if ($s->dummy); TODO
	if ($s->sha1) {
		$s->status("EXISTS: ".$s->file_name,'UINFO');
		return 2;
	}
	unless ($s->file_name) {
		$s->status('ERROR could not get filename: ' . $s->{gfn_error} , 'ERR');
		return undef;
	}
	my $osurl = $s->dbstrps(file=>$s->file_name,'surl');
	if ((defined $osurl) && ($osurl ne $s->url)) {

		$s->status("WARN: file name '".$s->file_name."' already used",'ERR');

		my $new_depth = $s->handle_equal_filenames($osurl,$s->url);
		unless ($new_depth) {
			$s->status('ERROR: could not find new filename depth','ERR');
			return undef;
		}
		$s->file_name($s->get_file_name($s->url,$new_depth));
		$s->status("WARN: new depth $new_depth filename: " . $s->file_name ,'ERR');
	}
	
	if (-e $s->file_path) {
		$s->status("EXISTS on disk: ".$s->file_name,'UINFO');
		return 2;
	}
	
	my $home = $s->page->cmc->url_home();
	$s->page->url =~ m#(?:$home)?(.+)#;
	my $se_url = $1;
	local $| = 1; #dont wait for newline to print the text
	print "GET: " . $se_url . " => " . $s->file_name;
	$s->status("GET: ".$s->file_name." URL: " . $s->page->url ." SURL: " .$s->url,"DEBUG");
	return $s->_download($s->url,$s->file_name);
}

sub handle_equal_filenames {
	my ($s, $sA , $sB) = @_;
	
	my $depth = $s->{filename_depth};
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
	my ($s,$surl,$file_name) = @_;
	my $u_referer = $s->ini('referer');
	my $time = Time::HiRes::time;
	my $img_res = dlutil::get($surl,$u_referer);
	$time = Time::HiRes::time - $time;
	if ($img_res->is_error) {
		$s->status("ERROR downloading $file_name code: " .$img_res->status_line(),"ERR");
		return 0;
	}
	$s->{file_blob} = $img_res->content();
	$s->sha1(sha1_hex($s->{file_blob}));
	$s->{need_save} = $time*1000;
	return 1;
}

sub file_name {
	my $s = shift;
	($s->{file_name}) = @_ if @_;
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
	my ($s,$surl,$depth) = @_;
	$s->{gfn_error} = undef;
	unless ($surl) {
		$s->{gfn_error} = "no url";
		return undef;
	}
	#return $surl if ($s->dummy); TODO
 	$depth ||= $s->{filename_depth} || 1;
	$surl =~ s#^\w+://##; #remove protokoll

	my @surl = split( '/' , $surl); 
	
	if ($depth > @surl) {	
		$s->{gfn_error}="depth overflow";
		return undef;
	}
	
	my $filename;
	
	my $bc = '/?&=#';
	
	my $part = pop @surl;
	
	if ($part =~ m#(?:^|.*[$bc])([^$bc]+\.(jpe?g|gif|png|bmp))#i) { # line start or invalid char til filetype
		$filename = $1;
	}
	else {
		my $header_res = dlutil::gethead($surl,$s->ini('referer'));
		unless ($header_res->is_success()) {
			$s->{gfn_error} = "header failure " .  $header_res->status_line();
			return undef;
		}
		my $filetype = $header_res->header('Content-Type');
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
	$ut //= ''; $st //= '';	$it //= '';	$ia //= '';	$h1 //= '';	$dt //= ''; $sl //= '';
	my $title_string = "{ut=>q($ut),st=>q($st),it=>q($it),ia=>q($ia),h1=>q($h1),dt=>q($dt),sl=>q($sl)}";

	# $s->page->cmc->{sqlstr_title_update} //= $s->page->cmc->dbh->prepare('UPDATE _'.$s->name .' SET title=?,url=?,surl=?,c_version=?,time=? where strip == ?');
	# if($s->page->cmc->{sqlstr_title_update}->execute($title_string,$s->url,$surl,$main::VERSION,time,$file) < 1) {
		# $s->page->cmc->{sqlstr_title_insert} //= $s->page->cmc->dbh->prepare('insert into _'.$s->name .' (title,url,surl,c_version,time,strip) values (?,?,?,?,?,?)');
		# $s->page->cmc->{sqlstr_title_insert}->execute($title_string,$s->url,$surl,$main::VERSION,time,$file);
	# }
	# $s->dat($file,'title',$title_string);
	# $s->dat($file,'url',$s->url);
	# $s->dat($file,'surl',$surl);
	# $s->dat($file,'c_version',$main::VERSION);
	# $s->dat($file,'time',time);
	
	$s->status("TITLE $file: " . $title_string,'DEBUG');
	$s->{title} = $title_string;
	return $title_string; 
}



{ #utils and accessor

sub id {
	my $s = shift;
	return $s->{id} if defined $s->{id};
	$s->status("ERROR: ID not defined: " . $s->url, 'ERR');
	return undef;
}

sub file_path {
	my $s = shift;
	return "./strips/".$s->page->name."/".$s->file_name;
}

sub sha1 {
	my $s = shift;
	$s->{sha1} = @_ if @_;
	return $s->{sha1};
}

sub url {
	my $s = shift; 
	return $s->{url};
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
	$s->status('DESTROYED: '. $s->url,'DEBUG');
}

}

1;