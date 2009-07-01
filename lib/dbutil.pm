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
$VERSION = '12';

my @comic_columns = 	qw(strip c_version md5 sha1 prev next surl time title url number); 
my @config_columns =	qw(update_intervall filter processing time);
my @user_columns =		qw(comic url_current first last last_save strip_count strips_counted kategorie aktuell bookmark last_update server_update flags iflags tags itags archive_current filename_depth);


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
		when ('USER') {&user($dbh)}
		when ('CONFIG') {&config($dbh)}
		when (/^_\w+/) {&comic($dbh,$table)}
		default { warn "incorrect table name: $table\n" }
	}
}

sub user {
	my $dbh = shift;
	unless($dbh->selectrow_array("select name from sqlite_master where type='table' and name='USER'")) {
		$dbh->do("create table USER (" . join(",",@user_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("select sql from sqlite_master where type='table' and name='USER'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@user_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("alter table USER add column " . $column);
	}
	return 2;
}

sub config {
	my $dbh = shift;
	my $table = shift;
	unless($dbh->selectrow_array("select name from sqlite_master where type='table' and name='CONFIG'")) {
		$dbh->do("create table CONFIG (" . join(",",@config_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("select sql from sqlite_master where type='table' and name='CONFIG'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@config_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("alter table CONFIG add column " . $column);
	}
	return 2;
}

sub comic {
	my $dbh = shift;
	my $table = shift;
	unless($dbh->selectrow_array("select name from sqlite_master where type='table' and name='" . $table ."'")) {
		$dbh->do("create table " .  $table . " (" . join(",",@comic_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("select sql from sqlite_master where type='table' and name='" . $table ."'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@comic_columns) {
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
