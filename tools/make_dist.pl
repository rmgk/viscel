use 5.010;
use strict;
use warnings;
use lib '../lib';
use File::Copy;
use File::Copy::Recursive qw(dircopy);
use Cwd 'abs_path';
use File::Remove 'remove';
use pp;


remove(\1,abs_path('dist')) or die("$!\n") if -e 'dist'; 
remove(\1,abs_path('build')) or die("$!\n") if -e 'build'; 

mkdir 'dist' or die("$!\n") unless -e 'dist';
mkdir 'dist/lib' or die("$!\n") unless -e 'dist/lib';
mkdir 'build' or die("$!\n") unless -e 'build';

dircopy(abs_path('../lib/'),abs_path('dist/lib/')) or die("$!\n");

copy(abs_path('../comic3.pl'),abs_path('dist/')) or die("$!\n");
copy(abs_path('../httpserver.pl'),abs_path('dist/')) or die("$!\n");
copy(abs_path('../comic.ini'),abs_path('dist/')) or die("$!\n");
copy(abs_path('../default.css'),abs_path('dist/')) or die("$!\n");


$ENV{'PAR_GLOBAL_TEMP'} = abs_path('./build/');
$ENV{'PP_OPTS'} = '-o '.abs_path('./dist/') .'/c3.exe '. abs_path('../') .'/comic3.pl';
pp->go();