use 5.012;
use warnings;

use Test::More tests => 13;

use Collection;
use Element;

my $dbname = 'test.db';
unlink $dbname if (-e $dbname);
Collection::init($dbname);

is(Collection->list(), 0, "no initial collections");

my $col = Collection->get('Test_Collection');
is(Collection->list(), 1, 'get creates collection');

my @elem = map { Element->new({position => $_, state => 's'.$_, cid => 'Test_Collection', sha => $_ x40}) } 1..5;
ok($col->store($_), 'store element') for (@elem);
my $elem = $col->fetch(3);
ok( ! $elem->differs($elem[2]) , 'fetching works as expected');
ok( ! $col->store($elem) , "storing already stored value fails");
is( $col->last() , 5 , 'last works');
$col->delete(5);
is( $col->last() , 4 , 'delete deletes');
is( $col->fetch(5), undef , 'delete deletes 2');

$col->purge();
is(Collection->list(), 0, "no more collections after purge");

Collection->deinit();

unlink $dbname;
