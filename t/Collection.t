use 5.012;
use warnings;
use utf8;

use Test::More tests => 16;

use Collection;
use Element;

my $dbname = 'test.db';
unlink $dbname if (-e $dbname);
Collection::init($dbname);

is(Collection->list(), 0, "no initial collections");

my $col = Collection->get('Test_Collection');
is(Collection->list(), 1, 'get creates collection');

my @elem = map { Element->new({position => $_, state => 's'.$_, cid => 'Test_Collection', sha1 => $_ x40}) } 1..5;
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

$col = Collection->get('Test_CollectionUTF');

my $utf8elem = Element->new({position => 1, state => 'utf8testπτ', cid => 'Test_CollectionUTF', sha1 => 'f' x40, alt => 'Chapter 1 – Kindled Eye : Page 5', title => "\x{2013}"});
ok($col->store($utf8elem), 'store utf8 element');
my $fetchedutf8 = $col->fetch(1);
ok($fetchedutf8, 'fetch utf8');
ok(!$utf8elem->differs($fetchedutf8),'fetch utf8 matches');

Collection->deinit();

unlink $dbname;
