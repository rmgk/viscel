use 5.012;
use warnings;

use Test::More tests => 14;

use Element;

my $elem = new_ok(Element => [{position => 1, state => 'somestate', cid => 'Test_Comic'}]);
is( Element->new(), undef, 'creating elements with incorrect parameters fails' );
is( $elem->position, 1, 'position accessor');
is( $elem->state, 'somestate', 'state accessor');
is( $elem->cid, 'Test_Comic', 'cid accessor');

ok( ! $elem->differs($elem) , 'does not differ from itself' );
my $elem2 = new_ok(Element => [{position => 2, state => 'someotherstate', cid => 'Test_Comic'}]);
ok( $elem->differs($elem2) , 'different elements are different' );
my $elem3 = new_ok(Element => [{position => 1, state => 'somestate', cid => 'Test_Comic',width => 300}]);
ok( $elem->differs($elem3) , 'additional information changes' );
my $elem4 = new_ok(Element => [{position => 1, state => 'somestate', cid => 'Test_Comic',sha1 => 'a'x40, type => 'image/jpeg'}]);
ok( ! $elem->differs($elem4) , 'type and sha1 may differ if one side is undefined' );
my $elem5 = new_ok(Element => [{position => 1, state => 'somestate', cid => 'Test_Comic',sha1 => 'b'x40, type => 'image/gif'}]);
ok( $elem5->differs($elem4) , 'type and sha1 may not differ if both are defined' );
