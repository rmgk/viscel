#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Spot::Template v1.3.0;

use 5.012;
use warnings;

use Log;
use Element;
use DlUtil;
use Digest::SHA;

my $SHA = Digest::SHA->new();

#$class, \%self -> \%self
#creates a new collection instance of $id at position $pos
sub new {
	my ($class,$self) = @_;
	Log->trace('new ',$class,' instance');
	$self->{fail} = 'not mounted';
	bless $self, $class;
	return $self;
}

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	$s->{page_url} = $s->{state};
	Log->trace('mount ' . $s->{id} .' '. $s->{page_url});
	my ($tree,$page) = DlUtil::get_tree($s->{page_url});
	
	unless ($s->_mount_parse($$tree)) {
		Log->error("failed to parse page ". $s->{page_url});
		$s->{fail} = 'mount parse returned failure';
		die ['mount parse failed', $s, $page]
	}
	Log->trace(join "\n\t\t\t\t", map {"$_: " .($s->{$_}//'')} qw(src next));
	$s->{fail} = undef;
	return 1;
}

#not implemented
sub _mount_parse {
	Log->fatal('mount parse not implemented');
	die['mount parse not implemented'];
}


#-> \%element
#returns the element
sub fetch {
	my ($s) = @_;
	if ($s->{fail}) {
		Log->error('fail is set: ' . $s->{fail});
		return;
	}
	Log->trace('fetch object');

	my $file = DlUtil::get($s->{src},$s->{page_url});
	if (!$file->is_success() or !$file->header('Content-Length')) {
		Log->error('error get ' . $s->{src});
		$s->{fail} = 'could not fetch object';
		die ['fetch element', $s, $file];
	}
	my $blob = $file->decoded_content();

	$s->{type} = $file->header('Content-Type');
	$s->{sha1} = $SHA->add($blob)->hexdigest();

	return \$blob;
}

#-> \%element
#returns the element
sub element {
	my ($s) = @_;
	if ($s->{fail}) {
		Log->error('fail is set: ' . $s->{fail});
		die 'cant get element of failed page';
	}
	my $object = {};
	$object->{cid} = $s->{id};
	$object->{$_} = $s->{$_} for grep {defined $s->{$_}} Element::attribute_list_array();
	return Element->new($object);
}

#returns the next spot
sub next {
	my ($s) = @_;
	Log->trace('create next');
	if ($s->{fail}) {
		Log->error('fail is set: ' . $s->{fail});
		die 'cant get next of failed page';
	}
	unless ($s->{next}) {
		Log->error('no next was found');
		return;
	}
	my $next = {id => $s->{id}, position => $s->{position} + 1, state => $s->{next} };
	$next = ref($s)->new($next);
	return $next;
}

#accessors:
sub id { return $_[0]->{id} }
sub position { return $_[0]->{position} }

1;
