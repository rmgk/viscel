#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package UserPrefs v1.3.0;

use 5.012;
use warnings;
use utf8;

use Log;
use Globals;
use ConfigINI;

my %data;
my $changed = 0;
my $dir;
my $default_file;

#initialises the preferences
sub init {
	Log->trace('initialise config');
	$dir = shift // Globals::userdir();
	$default_file = shift // 'user';
	unless (-e $dir or mkdir $dir) {
		Log->error('could not create cache dir ' , $dir);
		return;
	}
	%data = %{ConfigINI::parse_file($dir,$default_file)};
	return 1;
}

#$class, $sect -> $self
#returns the handle to a section
sub section {
	my ($class,$sect) = @_;
	Log->trace("userprefs handle for ", $sect); 
	unless ($data{$sect}) {
		Log->debug("create $sect");
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
	Log->trace("set sect: ", $$s, " key: $key to value: ",$value);
	$data{$$s}->{$key} = $value;
	$changed = 1;
	return $value;
}

#$self|$section,$key -> $result
#removes the stored value
sub remove {
	my ($sect,$key) = @_;
	$sect = $$sect if ref $sect;
	Log->trace("delete sect: $sect key: $key");
	my $result = delete $data{$sect}->{$key};
	$changed = 1 if defined $result;
	return $result;
}

#saves the config
sub save {
	return unless $changed;
	ConfigINI::save_file($dir,$default_file,\%data);
	$changed = 0;
}

1;
