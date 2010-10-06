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
					state => 'CHAR UNIQUE',
					chapter => 'CHAR',
					sha1 => 'CHAR',
					type => 'CHAR',
					filename => 'CHAR',
					page_url => 'CHAR',
					src => 'CHAR',
					title => 'CHAR',
					alt => 'CHAR',
					width => 'INTEGER',
					height => 'INTEGER'
					);

#$class, \%data -> \%data
sub new {
	my ($class,$self) = @_;
	$l->trace('create new entity');
	foreach my $needed (qw(position state sha1 type cid)) {
		unless (defined $self->{$needed}) {
			$l->debug($needed . ' not defined');
			return undef;
		} 
	}
	foreach my $want (keys %attributes) {
		unless (exists $self->{$want}) {
			$l->warn($want . ' does not exist');
		} 
	}
	foreach my $has (keys %$self) {
		unless (exists $attributes{$has} or $has ~~ m/^(blob|cid)$/i) {
			$l->warn("unknown attribute $has delete");
			delete $self->{$has};
		}
	}
	bless $self, $class;
	return $self;
}

#creates the Core at the position of the entity
sub create_spot {
	my ($s) = @_;
	my $core = Cores::new($s->cid);
	return $core->create($s->position,$s->state);
}

#returns the string of columns
sub create_table_column_string {
	return join ',', map {$_ . ' '. $attributes{$_}} sort keys %attributes;
}

#returns ordered list of attributes
sub attribute_list_string {
	return join ',' , sort keys %attributes;
}

#returns ordered list of attributes
sub attribute_list_array {
	return sort keys %attributes;
}

#returns array of attribute values
sub attribute_values_array {
	my $s = shift;
	return @$s{sort keys %attributes};
}

#-> $html
#returns html representation of the entity
sub html {
	my $s = shift;
	#if ($s->type ~~ m'^image/'i) {
		my $html .= img({	src	=>	"/b/". $s->sha1,#.'/'.$s->filename,
							alt => $s->alt,
							title => $s->title,
							width => $s->width,
							height => $s->height
						});
		return $html;
	#}
	return undef;
}

#accessors:
sub position { $_[0]->{position}; }
sub state { $_[0]->{state}; }
sub chapter { $_[0]->{chapter}; }
sub sha1 { $_[0]->{sha1}; }
sub type { $_[0]->{type}; }
sub filename { $_[0]->{filename}; }
sub page_url { $_[0]->{page_url}; }
sub cid { $_[0]->{cid}; }
sub title { $_[0]->{title}; }
sub alt { $_[0]->{alt}; }
sub src { $_[0]->{src}; }
sub width { $_[0]->{width}; }
sub height { $_[0]->{height}; }

1;
