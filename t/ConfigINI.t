use 5.012;
use warnings;

use Test::More tests => 2;
use ConfigINI;

my $config = {
	section1 => {
		key1 => "value 1",
		key2 => "some unicode περλ",
		},
	section2 => {
		key3 => " some value with spaces around ",
		key4 => "	tabs	everywhere	",
		},
	section3 => {
		key => "test for case",
		KEY => "this time in uppercase",
		},
	};

ConfigINI::save_file('','test',$config);
my $read = ConfigINI::parse_file('','test');

is_deeply($config,$read,'read and original config match');

ok(-e "test.ini", 'ini file was correctly created');
unlink('test.ini');
