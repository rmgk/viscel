#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Comcol;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Entity;
use HTML::Entities;

my $l = Log->new();
my $DBH;
my $DIR;

#initialises the database connection
sub init {
	$l->trace('initialising Core::Comcol');
	$DIR = UserPrefs->block('folders')->get('Comcol') || '';
	unless (-e $DIR) {
		$l->warn("Comcol directory dir ($DIR) does not exists correct preferences");
		UserPrefs->block('folders')->set('Comcol','');
		return undef;
	}
	$l->warn('database handle already initialised, reinitialising') if defined $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=".$DIR.'comics.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	unless ($DBH) {
		$l->warn('could not connect to database');
		return undef;
	}
	return 1;
}

#->\@id_list
#lists the known ids
sub list {
	unless ($DBH) {
		$l->error('$DBH undefined call init');
		return undef;
	}
	my $cmcs;
	unless ($cmcs = $DBH->selectcol_arrayref('SELECT comic FROM comics')) {
		$l->error('could not get info from database');
		return undef;
	}
	my %cmcs = map {'Comcol_' . $_ , {name => $_}} @$cmcs;
	return \%cmcs
	
}

#$class,$id -> \%self
#returns the first spot
sub first {
	my ($class,$id) = @_;
	$l->trace('creating first');
	return $class->create($id,1,1);
}

#$class, $id, $pos -> \%self
#creates a new spot of $id at position $pos
sub create {
	my ($class,$id,$pos) = @_;
	my $self = {id => $id, position => $pos};
	$l->debug('creating new core ' , $class, ' id: ', $id, ,' position: ', $pos);
	$class->new($self);
	if ($self->check()) {
		return $self;
	}
	$l->error('failed environment checks');
	return undef;
}

#$class, \%self -> \%self
#creates a new collection instance of $id at position $pos
sub new {
	my ($class,$self) = @_;
	$l->trace('new ',$class,' instance');
	$self->{fail} = 'not mounted';
	bless $self, $class;
	return $self;
}


#$bool
#checks if environment is set correctly
sub check {
	my ($s) = @_;
	$l->trace('checking environment');
	unless (defined $DBH) {
		$l->error('$DBH undefined call init');
		return undef;
	}
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	if (my $last = $DBH->selectrow_array('SELECT last FROM comics WHERE comic = ?', undef, $comic)) {
		if ($last < $s->{position}) {
			$l->error('invalid position, last was '.$last);
			return undef;
		}
	}
	else {
		$l->error('invalid id');
		return undef;
	}
	return 1;
}

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	$l->trace('mounting ' . $s->{id} .' '. $s->{position});
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	my $entry;
	unless ( $entry = $DBH->selectrow_hashref('SELECT file,surl,purl,sha1,title FROM _'.$comic.' WHERE number = ?', undef, $s->{position})) {
		$l->error('could not get file information');
		$s->{fail} = 'no db entry found';
		return undef;
	}
	$s->{_data} = $entry;
	$s->{fail} = undef;
	return 1;
}

#-> \%entity
#returns the entity
sub fetch {
	my ($s) = @_;
	return undef if $s->{fail};
	$l->trace('fetching object');
	my $object = {};
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	my $fh;
	unless(open ($fh, '<', $DIR."strips/$comic/".$s->{_data}->{file})) {
		$l->error('failed to open: ' . $s->{_data}->{file});
		return undef;
	}
	binmode $fh;
	local $/ = undef;
	$object->{blob} = <$fh>;
	close $fh;
	$object->{filename} = $s->{_data}->{file};
	my ($ext) = $object->{filename} ~~ m/.*\.(\w{3,4})$/;
	$ext = $ext eq 'jpg' ? 'jpeg' : $ext;
	$object->{type} = "image/$ext"; #its a goood enough guess as the archives contain very little non image files
	$object->{sha1} = $s->{_data}->{sha1};
	$object->{src} = $s->{_data}->{surl};
	$object->{page_url} = $s->{_data}->{purl};
	$object->{cid} = $s->{id};
	$object->{position} = $s->{position};
	$object->{state} = $s->{position};
	my %titles = get_title($s->{_data}->{title});
	$object->{title} = $titles{it} ? decode_entities($titles{it}) : undef;
	$object->{alt} = $titles{ia} ? decode_entities($titles{ia}) : undef;
	$s->{entity} = Entity->new($object);
	return $s->{entity};
}

#returns the next spot
sub next {
	my ($s) = @_;
	$l->trace('creating next');
	my $next = {id => $s->{id}, position => $s->{position} + 1 };
	$next = Core::Comcol->new($next);
	return $next;
}

#gets strip titles
sub get_title {
	my ($title) = @_;
	return undef unless $title;
	my %titles = ();
	if ($title =~ /^\{.*\}$/) {
		while($title =~ m#(?<id>\w+)=>q\((?<text>.*?)\)[,}]#g) {
			foreach (0..$#{$-{id}}) {
				$titles{$-{id}->[$_]} = $-{text}->[$_];
			}
		}
		#say "$title\n";
		#%titles = %{eval($title)} if $title ;
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
