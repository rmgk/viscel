#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
#22:12 11.04.2009
package dbutil;

use 5.010;
use strict;
use warnings;

=head1 NAME

dbutil - database utility

=head1 DESCRIPTION

provides some database utility functions

=cut

use DBI;

our($VERSION);
$VERSION = '14';

my @strips_columns = 	qw(file prev next number surl purl title time sha1); 
my @config_columns =	qw(update_intervall filter processing time);
my @comics_columns =	qw(url_current archive_current current first last bookmark strip_count last_update last_save server_update flags tags);


=head1 Functions

=head2 check table

	dbutil::check_table($dbh,$table_name);
	
given a I<$dbh> and a I<$table_name> will check if that table is existent and has all the required columns

returns: C<1> if table was created or C<2> if it exists

=cut

sub check_table {
	my $dbh = shift;
	my $table = shift;
	given ($table) {
		when ('comics') {return &comics($dbh)}
#		when ('CONFIG') {return &config($dbh)}
		when (/^_\w+/) {return &strips($dbh,$table)}
		default { warn "incorrect table name: $table\n" }
	}
}

sub comics {
	my $dbh = shift;
	unless($dbh->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name='comics'")) {
		$dbh->do("CREATE TABLE comics ( comic PRIMARY KEY," . join(",",@comics_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("SELECT sql FROM sqlite_master WHERE type='table' AND name='comics'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@comics_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("ALTER TABLE comics ADD COLUMN " . $column);
	}
	return 2;
}

# sub config {
	# my $dbh = shift;
	# my $table = shift;
	# unless($dbh->selectrow_array("select name from sqlite_master where type='table' and name='CONFIG'")) {
		# $dbh->do("create table CONFIG (" . join(",",@config_columns) . ")");
		# return 1;
	# };
	# my @sql = $dbh->selectrow_array("select sql from sqlite_master where type='table' and name='CONFIG'");
	# $sql[0] =~ /\(([\w,\s]+)\)/s;
	# my $col_having = $1;
	# my @missing_column;
	# foreach my $column (@config_columns) {
		# push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	# }
	# foreach my $column (@missing_column) {
		# $dbh->do("alter table CONFIG add column " . $column);
	# }
	# return 2;
# }

sub strips {
	my $dbh = shift;
	my $table = shift;
	unless($dbh->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name='" . $table ."'")) {
		$dbh->do("CREATE TABLE " .  $table . " ( id INTEGER PRIMARY KEY ASC , " . join(",",@strips_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("SELECT sql FROM sqlite_master WHERE type='table' AND name='" . $table ."'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@strips_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("alter table " . $table . " add column " . $column);
	}
	return 2;
}

=head2 readINI

	dbutil::readINI($filename);
	
will read I<$filename> and load it into a hashref

returns: hashref with parsed file content

=cut

sub readINI {
	my ($file) = @_;
	return unless defined $file;
	return unless -e $file;
	my $data = {};
	my $block = 'default';
	open (FILE, $file);
	while (my $line = <FILE>) {
		if ($line =~ /^\s*\[(.*?)\]\s*$/) {
			$block = $1;
			next;
		}
		next if $line =~ /^\s*\;/;
		next if $line =~ /^\s*\#/;
		next if $line =~ /^\s*$/;
		next if length $line == 0;
		
		my ($what,$is) = split(/=/, $line, 2);
		$what = "url_start" unless $what;
		$what =~ s/^\s*//g;
		$what =~ s/\s*$//g;
		$is =~ s/^\s*//g;
		$is =~ s/\s*$//g;

		$data->{$block}->{$what} = $is;
	}
	close (FILE);
	return $data;
}

1;
