#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package UserPrefs;

use 5.012;
use warnings;

our $VERSION = v1;

use Log;

my $l = Log->new();
our $FILE = 'userprefs.ini';
my %data;
my $changed = 0;

#initialises the preferences
sub init {
	$l->trace('initialising userprefs');
	unless (-e $main::DIRDATA.$FILE) {
		%data = ();
		return 1;
	}
	my $block;
	open (my $fh, '<', $main::DIRDATA.$FILE);
	while (my $line = <$fh>) {
		if ($line =~ /^\s*\[\s*(?<block>\w+)\s*\]\s*$/) {
			$block = $+{block};
			next;
		}
		next if $line =~ /^\s*\[;#]/;
		next if $line =~ /^\s*$/;
		next if length $line == 0;
		
		my ($what,$is) = split(/=/, $line, 2);
		$what =~ s/^\s*//g;
		$what =~ s/\s*$//g;
		$is =~ s/^\s*//g;
		$is =~ s/\s*$//g;
		
		$data{$block}->{$what} = $is;
	}
	close ($fh);
	return 1;
}

#$class, $sect -> $self
#returns the handle to a section
sub section {
	my ($class,$sect) = @_;
	$l->trace("userprefs handle for $sect"); 
	$sect //= caller; #/ padre display bug
	unless ($data{$sect}) {
		$l->debug("create $sect");
		$data{$sect} = {};
	}
	return bless \$sect, $class;
}

#$self|$section,$key -> $value
#returns the stored value
sub get {
	my ($sect,$key) = @_;
	$sect = $$sect if ref $sect;
	my $value = $data{$sect}->{$key};
	return $value;
}

#$self,$key,$value
#sets the value of key
sub set {
	my ($s,$key,$value) = @_;
	$l->trace("set key: $key to value: $value");
	$data{$$s}->{$key} = $value;
	$changed = 1;
	return $value;
}

#saves the config
sub save {
	return unless $changed;
	$l->trace('saving');
	open (my $fh, '>',$main::DIRDATA.$FILE);
	print $fh as_string();
	close $fh;
	$changed = 0;
}

#returns the %data as a string
sub as_string {
	my $sect = shift;
	$sect = $$sect if ref $sect;
	if ($sect) {
		return "[$sect]\n\t" . 
			join "\n\t",
				map { $_ . '=' . $data{$sect}->{$_} }
					sort grep {defined $data{$sect}->{$_} and $data{$sect}->{$_} ne ''} keys %{$data{$sect}};
	}
	else {
		return join "\n",
			map { as_string($_) }
				 sort grep {keys %{$data{$_}}} keys %data;
	}
}

1;
