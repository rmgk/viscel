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
	foreach my $needed (qw(position sha1 type filename cid)) {
		unless (defined $self->{$needed}) {
			$l->debug($needed . ' not defined');
			return undef;
		} 
	}
	bless $self, $class;
	return $self;
}

#accessors:
sub position { my $s = shift; return $s->{position}; }
sub sha1 { my $s = shift; return $s->{sha1}; }
sub type { my $s = shift; return $s->{type}; }
sub filename { my $s = shift; return $s->{filename}; }
sub cid { my $s = shift; return $s->{cid}; }
sub title { my $s = shift; return $s->{title}; }
sub alt { my $s = shift; return $s->{alt}; }
sub src { my $s = shift; return $s->{src}; }
sub width { my $s = shift; return $s->{width}; }
sub height { my $s = shift; return $s->{height}; }

1;
