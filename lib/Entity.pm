#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Entity;

use 5.012;
use warnings;

our $VERSION = v1;

use CGI qw(img);

use Log;


my $l = Log->new();
my %attributes = ( 	position => 'INTEGER PRIMARY KEY', 
					sha1 => 'CHAR',
					type => 'CHAR',
					filename => 'CHAR',
					title => 'CHAR',
					alt => 'CHAR',
					src => 'CHAR',
					width => 'INTEGER',
					height => 'INTEGER'
					);

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

#returns the string of columns
sub create_table_column_string {
	return join ',', map {$_ . ' '. $attributes{$_}} sort keys %attributes;
}

#returns ordered list of attributes
sub attribute_list_string {
	return join ',' , sort keys %attributes;
}

#returns array of attributes
sub attribute_list_array {
	my $s = shift;
	return @$s{sort keys %attributes};
}

#-> $html
#returns html representation of the entity
sub html {
	my $s = shift;
	if ($s->type ~~ m'^image/'i) {
		my $html .= img({	src	=>	"/blob/". $s->sha1,#.'/'.$s->filename,
							alt => $s->alt,
							title => $s->title,
							width => $s->width,
							height => $s->height
						});
		return $html;
	}
	return undef;
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
