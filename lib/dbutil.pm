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
$VERSION = '18';

my @strips_columns = 	qw(file prev next number surl purl title time sha1); 
my @config_columns =	qw(update_intervall filter processing time);
my @comics_columns =	qw(url_current archive_current current first last bookmark strip_count last_update last_save flags tags);
my @favourites_columns = qw(comic file id sha1 number surl purl title);


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
		when ('favourites') {return &favourites($dbh)}
		when (/^_\w+/) {return &strips($dbh,$table)}
		when (ref($_) eq 'ARRAY') {return all($dbh,$table)}
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
		$dbh->do("ALTER TABLE comics ADD COLUMN $column");
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

sub favourites {
	my $dbh = shift;
	unless($dbh->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name='favourites'")) {
		$dbh->do("CREATE TABLE favourites ( " . join(",",@favourites_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("SELECT sql FROM sqlite_master WHERE type='table' AND name='favourites'");
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@favourites_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("ALTER TABLE favourites ADD COLUMN $column");
	}
	return 2;
}

sub strips {
	my $dbh = shift;
	my $table = shift;
	unless($dbh->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name= ?",undef, $table)) {
		$dbh->do("CREATE TABLE $table ( id INTEGER PRIMARY KEY ASC , " . join(",",@strips_columns) . ")");
		return 1;
	};
	my @sql = $dbh->selectrow_array("SELECT sql FROM sqlite_master WHERE type='table' AND name= ?",undef, $table);
	$sql[0] =~ /\(([\w,\s]+)\)/s;
	my $col_having = $1;
	my @missing_column;
	foreach my $column (@strips_columns) {
		push(@missing_column,$column) unless $col_having =~ /\b$column\b/is;
	}
	foreach my $column (@missing_column) {
		$dbh->do("ALTER TABLE $table ADD COLUMN $column");
	}
	return 2;
}

sub all {
	my $dbh = shift;
	my $cmc_list = shift;
	my $tables = $dbh->selectall_hashref("SELECT name FROM sqlite_master WHERE type='table'",'name');
	
	foreach my $comic (@$cmc_list) {
		if (!$tables->{"_$comic"}) {
			strips($dbh,"_$comic")
		}
	}
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
	
	my @allowed = qw(url_start	heur_strip_url	regex_next	regex_prev	regex_not_goto	regex_never_goto	regex_strip_url
					ignore_warning	broken	substitute_strip_url	rename_depth	rename_substitute	regex_title	use_home_only
					regex_ignore_strip	flags	tags	archive_url	archive_regex	archive_reverse	archive_regex_deeper
					list_url_regex	list_url_insert list_chap_regex list_page_regex list_chap_reverse	referer	regex_embed);
	my $allowed = join('|',@allowed);
	$allowed = qr/$allowed/;
	
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

		die "unknown entry: $what" unless ($what =~ m/^($allowed)$/);

		$data->{$block}->{$what} = $is;
	}
	close (FILE);
	return $data;
}

1;
