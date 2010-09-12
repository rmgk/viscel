#!perl
#this program is free software it may be redistributed under the same terms as perl itself
package Core::Comcol;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Entity;

my $l = Log->new();
my $DBH;
my $DIR = 'F:\\Comics\\';

#initialises the database connection
sub init {
	$l->trace('initialising Core::Comcol');
	$l->warn('database handle already initialised, reinitialising') if defined $DBH;
	$DBH = DBI->connect("dbi:SQLite:dbname=".$DIR.'comics.db',"","",{AutoCommit => 0,PrintError => 1, PrintWarn => 1 });
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
	$l->warn('failed environment checks');
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
		$l->debug('$DBH undefined call init');
		return 0;
	}
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	if (my $last = $DBH->selectrow_array('SELECT last FROM comics WHERE comic = ?', undef, $comic)) {
		if ($last < $s->{position}) {
			$l->debug('invalid position, last was '.$last);
			return 0;
		}
	}
	else {
		$l->debug('invalid id');
		return 0;
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
		$l->debug('could not get file information');
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
	return 0 if $s->{fail};
	$l->trace('fetching object');
	my $object = {};
	my $comic = $s->{id};
	$comic =~ s/^.*_//;
	my $fh;
	unless(open ($fh, '<', $DIR."strips/$comic/".$s->{_data}->{file})) {
		$l->warn('failed to open' . $s->{_data}->{file});
		return undef;
	}
	binmode $fh;
	local $/ = undef;
	$object->{blob} = <$fh>;
	close $fh;
	$object->{filename} = $s->{_data}->{file};
	$object->{sha1} = $s->{_data}->{sha1};
	$object->{url} = $s->{_data}->{surl};
	$object->{page_url} = $s->{_data}->{purl};
	$object->{cid} = $s->{id};
	#here be dragons
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

1;
