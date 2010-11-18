#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Element v1.1.0;

use 5.012;
use warnings;

use CGI qw(img embed);
use Log;

my $l = Log->new();
my %attributes = ( 	position => 'INTEGER PRIMARY KEY', 
					state => 'CHAR UNIQUE',
					#chapter => 'CHAR',
					sha1 => 'CHAR',
					type => 'CHAR',
					#filename => 'CHAR',
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
	$l->trace('create new element');
	foreach my $needed (qw(position state cid)) {
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

#creates the spot at the position of the element
sub create_spot {
	my ($s) = @_;
	my $remote = Cores::new($s->cid);
	return $remote->create($s->position,$s->state);
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

#$element -> bool
sub differs {
	my ($s,$other) = @_;
	for my $a (attribute_list_array()) {
		next if $a eq 'sha1' or $a eq 'type'; #type and sha1 are not required so may also be not equal 
		return $a unless $s->{$a} ~~ $other->{$a};
	}
	return undef;
}

#-> $html
#returns html representation of the element
sub html {
	my $s = shift;
	if (!$s->sha1) {
		return 'placeholder';
	}
	given ($s->type) {
		when ('application/x-shockwave-flash') {
			my $html .= embed({	src	=>	"/b/". $s->sha1,
								alt => $s->alt,
								title => $s->title,
								width => $s->width,
								height => $s->height,
								class => 'element'
							});
			return $html;
		}
		default {
			my $html .= img({	src	=>	"/b/". $s->sha1,
								alt => $s->alt,
								title => $s->title,
								width => $s->width,
								height => $s->height,
								class => 'element'
							});
			return $html;
		}
	}
	return undef;
}

#accessors:
sub position { $_[0]->{position}; }
sub state { $_[0]->{state}; }
#sub chapter { $_[0]->{chapter}; }
sub sha1 { $_[0]->{sha1}; }
sub type { $_[0]->{type}; }
#sub filename { $_[0]->{filename}; }
sub page_url { $_[0]->{page_url}; }
sub cid { $_[0]->{cid}; }
sub title { $_[0]->{title}; }
sub alt { $_[0]->{alt}; }
sub src { $_[0]->{src}; }
sub width { $_[0]->{width}; }
sub height { $_[0]->{height}; }

1;
