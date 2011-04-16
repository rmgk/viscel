use 5.012;
use warnings;
use utf8;

use Test::More tests => 5;
use Globals;
use UserPrefs;

UserPrefs::init(undef,'test');
UserPrefs::save();
ok(! -e Globals::userdir . 'test.ini', 'no changes, no save');

my $sect = UserPrefs->section('section1');
isa_ok($sect,'UserPrefs','section created');

$sect->set('key1','value1');
$sect->set('key2','value2');

is($sect->list, 2 , 'correct number of sections');
is($sect->get('key2'), 'value2', 'get works');

UserPrefs::save();
ok(-e Globals::userdir . 'test.ini', 'save if changes');
unlink Globals::userdir . 'test.ini';
