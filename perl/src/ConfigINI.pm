#!/usr/bin/env perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package ConfigINI v1.3.0;

use 5.012;
use warnings;
use utf8;
use autodie;

use Log;

#$dir,$file -> \%cfg;
#reads %cfg from $file in $dir
sub parse_file {
	my ($dir,$file) = @_;
	$file =~ s/\W+/_/g;
	Log->trace("parse config file ",$file);
	$file = $dir.$file.'.ini';
	unless (-e $file) {
		Log->trace("$file not found create new config");
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
		
		$cfg{$block}->{$what} = $is;
	}
	close ($fh);
	return \%cfg;
}


#$dir,$file,$cfg 
#saves $cfg to $file in $dir
sub save_file {
	my ($dir,$file,$cfg) = @_;
	$file =~ s/\W+/_/g;
	Log->trace('save config to ', $file);
	$file = $dir.$file.'.ini';
	if (open (my $fh, '>:encoding(UTF-8)',$file)) {
		print $fh as_string($cfg);
		close $fh;
		return 1;
	}
	Log->warn('could not open ', $file);
	return;
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
