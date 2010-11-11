#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package UserPrefs v1.1.0;

use 5.012;
use warnings;

use Log;
use Globals;

my $l = Log->new();
my %data;
my $changed = 0;
my $dir;
my $default_file;

#initialises the preferences
sub init {
	$l->trace('initialise config');
	$dir = Globals::datadir();
	$default_file = Globals::userprefsfile();
	unless (-e $dir or mkdir $dir) {
		$l->error('could not create cache dir ' , $dir);
		return undef;
	}
	%data = %{parse_file($default_file)};
	return 1;
}

#$file -> \%cfg;
sub parse_file {
	my ($file) = @_;
	$file =~ s/\W+/_/g;
	$l->trace("parse config file ",$file);
	$file = $dir.$file.'.ini';
	unless (-e $file) {
		$l->trace("$file not found create new config");
		return {};
	}
	my $block;
	my %cfg;
	open (my $fh, '<:encoding(UTF-8)', $file);
	while (my $line = <$fh>) {
		chomp($line);
		if ($line =~ /^\s*\[\s*(?<block>[\w:]+)\s*\]\s*$/) {
			$block = $+{block};
			next;
		}
		next if $line =~ /^\s*\[;#]/;
		next if $line =~ /^\s*$/;
		next if length $line == 0;
		
		my ($what,$is) = split(/=/, $line, 2);
		$what =~ s/^\s*//g;
		$what =~ s/\s*$//g;
		#keeping value exactly as is, with whitespaces
		#$is =~ s/^\s*//g;
		#$is =~ s/\s*$//g;
		
		$cfg{$block}->{$what} = $is;
	}
	close ($fh);
	return \%cfg;
}

#$class, $sect -> $self
#returns the handle to a section
sub section {
	my ($class,$sect) = @_;
	$sect ||= caller;
	$l->trace("userprefs handle for ", $sect); 
	unless ($data{$sect}) {
		$l->debug("create $sect");
		$data{$sect} = {};
	}
	return bless \$sect, $class;
}

#$self -> @keys
#returns the list of keys in this section
sub list {
	my ($s) = @_;
	return keys %{$data{$$s}};
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
	$l->trace("set sect: ", $$s, " key: $key to value: ",$value);
	$data{$$s}->{$key} = $value;
	$changed = 1;
	return $value;
}

#saves the config
sub save {
	return unless $changed;
	save_file($default_file,\%data);
	$changed = 0;
}

#$file,$cfg 
#saves $cfg to $file
sub save_file {
	my ($file,$cfg) = @_;
	$file =~ s/\W+/_/g;
	$l->trace('save config to ', $file);
	$file = $dir.$file.'.ini';
	if (open (my $fh, '>:encoding(UTF-8)',$file)) {
		print $fh as_string($cfg);
		close $fh;
		return 1;
	}
	$l->warn('could not open ', $file);
	return undef;
}

#returns the %data as a string
sub as_string {
	my ($cfg,$sect) = @_;
	$sect = $$sect if ref $sect;
	if ($sect) {
		return "[$sect]\n\t" . 
			join "\n\t",
				map { $_ . '=' . $cfg->{$sect}->{$_} }
					sort grep {defined $cfg->{$sect}->{$_} and $cfg->{$sect}->{$_} ne ''} keys %{$cfg->{$sect}};
	}
	else {
		return join "\n",
			map { as_string($cfg,$_) }
				 sort grep {keys %{$cfg->{$_}}} keys %$cfg;
	}
}

1;
