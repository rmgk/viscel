#!perl
#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Comcol;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Element;
use HTML::Entities;
use UserPrefs;

my $l = Log->new();
my $DBH;
my $DIR;

#class method
#initialises the database connection
sub init {
	my $pkg = shift;
	$l->trace('initialis Core::Comcol');
	my $cfg = UserPrefs::parse_file('Cores')->{$pkg};
	$DIR = $cfg->{'dir'} || '';
	unless (-e $DIR) {
		$l->warn("Comcol directory dir ($DIR) does not exists: correct preferences");
		return undef;
	}
	$l->warn('database handle already initialised, reinitialise') if defined $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=".$DIR.'comics.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
	unless ($DBH) {
		$l->warn('could not connect to database');
		return undef;
	}
	$l->debug('has ' .  scalar($pkg->list()) . ' collections');
	return 1;
}

#noop
sub update_list {
	$l->trace('comcol update list noop');
}

#->\@id_list
#class method
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
	return map {'Comcol_' . $_ , $_} @$cmcs;
	
}

#$query,$regex -> %list
#class method
#searches for a collection
sub search {
	my ($pkg,$filter,@re) = @_;
	$l->debug('search ', ref($pkg));
	return () if @$filter;
	my %cmcs = list();
	my %cap;
	return map {[$_,$cmcs{$_},$cap{$_}//$cmcs{$_}]} grep { my $id = $_; @re == grep {$cmcs{$id} ~~ $_ and defined($cap{$id} = $1) } @re } keys %cmcs; #/ padre display bug
}

#pkg, \%config -> \%config
#class method
#given a current config returns the configuration hash
sub config {
	my ($pkg,$cfg) = @_;
	return { dir => {	current => $cfg->{dir},
						default => undef,
						expected => qr/.*/,
						description => 'the directory containing the "comics.db"' 
					} 
			};
}

#$class,$id -> $self
#creates a new core instance for a given collection
sub new {
	my ($class,$id) = @_;
	$l->trace("create new core $id");
	return bless {id => $id}, $class;
}

#-> @info
#returns a list of infos
sub about {
	my ($s) = @_;
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	my $cmc;
	unless ($cmc = $DBH->selectrow_hashref('SELECT * FROM comics WHERE comic = ?',undef, $comic)) {
		$l->error('could not get info from database');
		return undef;
	}
	return map {"$_: " . $cmc->{$_}} grep {defined $cmc->{$_}} keys %$cmc;
}

#noop
sub fetch_info {
	$l->trace('comcol update list noop');
}

#$self -> $name
sub name {
	my ($s) = @_;
	my $id = $s->{id};
	$id =~ s/^\w+_//; 
	return $id;
}

#$class,$id -> \%self
#returns the first spot
sub first {
	my ($s) = @_;
	my $id = $s->{id};
	$l->trace('creat first spot of ', $id);
	return $s->create(1);
}

#$class, $id, $pos -> \%self
#creates a new spot of $id at position $pos
sub create {
	my ($s,$pos) = @_;
	my $class = ref($s) . '::Spot';
	my $spot = {id => $s->{id}, position => $pos};
	$l->debug('creat new spot ' , $class, ' id: ', $s->{id}, ,' position: ', $pos);
	$spot = $class->new($spot);
	if ($spot->check()) {
		return $spot;
	}
	$l->error('failed environment checks');
	return undef;
}



package Core::Comcol::Spot;

#$class, \%self -> \%self
#creates a new Spot
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
	$l->trace('check environment ',$s->{id});
	unless (defined $DBH) {
		$l->error('$DBH undefined call init');
		return undef;
	}
	my $comic = $s->{id};
	$comic =~ s/^[^_]*_//;
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
	$l->trace('mount ' . $s->{id} .' '. $s->{position});
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

#-> \$blob
#returns the blob and sets the sha1
sub fetch {
	my ($s) = @_;
	return undef if $s->{fail};
	$l->trace('fetch object');
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	my $fh;
	unless(open ($fh, '<', $DIR."strips/$comic/".$s->{_data}->{file})) {
		$l->error('failed to open: ' . $s->{_data}->{file});
		return undef;
	}
	binmode $fh;
	local $/ = undef;
	my $blob = <$fh>;
	close $fh;
	$s->{sha1} = $s->{_data}->{sha1};
	return \$blob;
}

#-> \%element
#returns the element
sub element {
	my ($s) = @_;
	return undef if $s->{fail};
	$l->trace('compose element');
	my $object = {};
	my ($ext) = $s->{_data}->{file} ~~ m/.*\.(\w{3,4})$/;
	$ext = $ext eq 'jpg' ? 'jpeg' : $ext;
	$object->{type} = "image/$ext"; #its a goood enough guess as the archives contain very little non image files
	$object->{sha1} = $s->{sha1}; 
	$object->{src} = $s->{_data}->{surl};
	$object->{page_url} = $s->{_data}->{purl};
	$object->{cid} = $s->{id};
	$object->{position} = $s->{position};
	$object->{state} = $s->{position};
	my %titles = get_title($s->{_data}->{title});
	$object->{title} = $titles{it} ? HTML::Entities::decode($titles{it}) : undef;
	$object->{alt} = $titles{ia} ? HTML::Entities::decode($titles{ia}) : undef;
	$s->{element} = Element->new($object);
	return $s->{element};
}

#returns the next spot
sub next {
	my ($s) = @_;
	return undef if $s->{fail};
	$l->trace('creat next');
	my $next = {id => $s->{id}, position => $s->{position} + 1 };
	$next = ref($s)->new($next);
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

#accessors:
sub id { return $_[0]->{id} }
sub position { return $_[0]->{position} }

1;
