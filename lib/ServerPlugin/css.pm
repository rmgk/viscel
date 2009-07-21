#!/usr/bin/perl
#this program is free software it may be redistributed under the same terms as perl itself
package ServerPlugin::css;

use 5.010;
use strict;
use warnings;

use ServerPlugin;
our @ISA = qw(ServerPlugin);

our $VERSION = '0.9.0';

my $css;
load_css();


sub get_content {
	my ($plugin,@arguments) = @_;
	return ccss();
}

sub get_response {
	HTTP::Response->new( 200, 'success', ['Content-Type','text/css; charset=iso-8859-1']);
}

=head2 custom style sheets

you can create a I<overwrite.css> next to your I<default.css>. the overwrite will be appended to de default 
so any changes you make there will overwrite the default settings. 

but be careful it is possible to execute perl code from within the style sheet so dont load styles from untrusted sources

=cut
	
sub ccss {
	my $ret_css = '';
	$ret_css = eval('qq<'.$css.'>');
	return $ret_css;
}

sub load_css{
	local $/ = undef;
	open(CSS,"<default.css");
	$css =  <CSS>;
	close(CSS);
	if (-e "overwrite.css") {
		$css .= "\n";
		open(CSS2,"<overwrite.css") ;
		$css .= <CSS2>;
		close(CSS2);
	}

}

1;
