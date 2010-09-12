#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Inverloch;

use 5.012;
use strict;
use warnings;

use Log::Log4perl qw(get_logger);

my $l = get_logger();

sub new {
	my ($class,$state) = @_;
	my $self = {state => $state//1};
	$l->debug('new core ' , $class);
	bless $self, $class;
	return $self;
}

sub state {
	my ($s) = @_;
	return $s->{state};
}

sub id {
	return 'Inverloch';
}

sub fetch {
	my ($s,$get) = @_;
	$l->debug('fetching file');
	$get->('http://inverloch.seraph-inn.com/pages/'.$s->state().'.jpg',sub {$s->file_callback(shift)});
}

sub file_callback {
	my ($s,$res) = @_;
	$l->debug('file callback');
	$s->{file} = $res->content();
}

sub get {
	my ($s) = @_;
	return $s->{file};
}

1;