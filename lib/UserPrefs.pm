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
	unless (-e $FILE) {
		%data = ();
		return;
	}
	my $block;
	open (my $fh, '<', $FILE);
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
}

#$class, $block -> $self
#returns the handle to a block
sub block {
	my ($class,$block) = @_;
	$l->trace("creating preferences handle for $block"); 
	unless ($data{$block}) {
		$l->debug("create $block");
		$data{$block} = {};
	}
	return bless \$block, $class;
}

#$self|$block,$key -> $value
#returns the stored value
sub get {
	my ($block,$key) = @_;
	$block = $$block if ref $block;
	my $value = $data{$block}->{$key};
	#$l->trace("get block: $block, key: $key, value: $value");
	
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
	open (my $fh, '>',$FILE);
	print $fh as_string();
	close $fh;
	$changed = 0;
}

#returns the %data as a string
sub as_string {
	my $block = shift;
	if ($block) {
		return "[$block]\n\t" . 
			join "\n\t",
				map { $_ . '=' .$data{$block}->{$_} }
					sort keys %{$data{$block}};
	}
	else {
		return join "\n",
			map { as_string($_) }
				 sort grep {keys %{$data{$_}}} keys %data;
	}
}

1;
