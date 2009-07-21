#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::pod;

use 5.010;
use strict;
use warnings;

use CGI qw(:standard *div);
use Pod::Simple::HTML;

use ServerPlugin qw(make_head);
our @ISA = qw(ServerPlugin);

our $VERSION = '1.0.0';

my %rand_seen;

sub get_content {
	my ($plugin,@arguments) = @_;
	return (cpod($arguments[0],$arguments[0]));
}

sub cpod {
	my $file = shift;
	my $plugin = shift;
	my $path;
	given (lc $file) {
		when(/comic/) {$path = "comic3.pl"}
		when(/comic/) {$path = "lib/Comic.pm"}
		when(/httpserver/) {$path = "httpserver.pl"}
		when(/page/) {$path = "lib/Page.pm"}
		when(/dbutil/) {$path = "lib/dbutil.pm"}
		when(/dlutil/) {$path = "lib/dlutil.pm"}
		when(/strip/) {$path = "lib/Strip.pm"}
		when(/serverplugin/) {$path = "lib/ServerPlugin/$plugin.pm"}
		default {$path = "httpserver.pl"};
	}
	my $ret = make_head('POD') . start_div({-class=>'pod'});
	my $parser = Pod::Simple::HTML->new();
	$parser->perldoc_url_prefix("/pod?file=");
	$parser->index(1);
	$parser->bare_output(1);
	$parser->output_string( \$ret );
	$parser->parse_file($path);
	return $ret . end_div() . end_html;
}

1;
