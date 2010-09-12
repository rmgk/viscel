#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Entity;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;

my $l = Log->new();

#$class, \%data -> \%data
sub new {
	my ($class,$self) = @_;
	$l->trace('creating new entity');
	foreach my $needed (qw(blob sha1 filename cid)) {
		unless (defined $self->{$needed}) {
			$l->debug($needed . ' not defined');
			return undef;
		} 
	}
	bless $self, $class;
	return $self;
}

1;
