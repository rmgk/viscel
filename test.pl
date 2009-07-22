print "enter comic: ";
chomp($comic = $ARGV[0]||<STDIN>);
$comic?`start http://127.0.0.1/front/$comic`:`start http://127.0.0.1/index`;
do 'httpserver.pl';