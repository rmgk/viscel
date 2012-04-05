use 5.012;
use warnings;
use utf8;

sub text_match {
	my $to_match = shift;
	return sub {$_[0]->as_text ~~ $to_match};
}
sub group {
	given($_[0]) {
		when(['zoom', 'pixie']) {
			return [id => 'comic'], [src => qr'/comics/'];
		}
	}
}

return
Zona => [
	'The challenges of Zona',
	'http://www.soulgeek.com/comics/zona/2005/08/01/page-01/',
	[_tag => 'div', id => 'comic']
],
Unstuffed => [
	'The Unstuffed',
	'http://www.plushandblood.com/Comic.php?strip_id=0',
	[_tag => 'div', id => 'ComicLayer']
],
PennyAndAggie => [
	'Penny And Aggie',
	'http://www.pennyandaggie.com/index.php?p=1',
	[class => 'comicImage']
],
Kukubiri => [
	'Kukubiri',
	'http://www.kukuburi.com/v2/2007/08/09/one/',
	[id => 'comic']	
],
KhaosKomic => [
	'Khaos Komik',
	'http://www.khaoskomix.com/cgi-bin/comic.cgi?chp=1',
	[id => 'currentcomic'] 
],
Catalyst => [
	'Catalyst',
	'http://catalyst.spiderforest.com/comic.php?comic_id=0',
	[src => qr/comics/]
],
EmergencyExit => [
	'Emergency Exit',
	'http://www.eecomics.net/?strip_id=0',
	[src => qr'comics/\d{6}\.']
],
Drowtales => [
	'Drowtales',
	'http://www.drowtales.com/mainarchive.php?order=chapters&id=0&overview=1&chibi=1&cover=1&extra=1&page=1&check=1',
	[src => qr'mainarchive//']
],
HilarityComics => [
	'Hilarity Comics', 
	'http://www.eegra.com/show/sub/do/browse/cat/comics/id/4',
	[src => qr'comics/\d{4}/']
],
Pronquest => [
	'Pronquest',
	'http://www.pronquest.com/1/hello-world/hentai',
	[id => 'comic']
],
PrincessPlanet => [
	'The Princess Planet',
	'http://www.theprincessplanet.com/?p=10',
	[id => 'comic']
],
GeekFetish => [
	'Geek Fetish',
	'http://akrobotics.com/comics/2007/02/07/bandit-the-wonder-dog',
	[id => 'comicPlate']
],
EverAfter => [
	'Ever After',
	'http://ea.snafu-comics.com/?comic_id=0',
	[src => qr'comics/\d{6}']
],
FunnyFarm => [
	'Funny Farm',
	'http://www.accurseddragon.com/site/index.php/archive/20090126b-4/',
	[class => qr/webcomic-object-full/],
],
AccursedDragon => [ #dead pages (161)
	'Accursed Dragon!',
	'http://www.accurseddragon.com/site/index.php/archive/0001aza/',
	[class => qr/webcomic-object-full/],
],
LaptopFerret => [
	'Laptop and Ferret',
	'http://www.accurseddragon.com/site/index.php/archive/0001-2/',
	[class => qr/webcomic-object-full/],
],
Banished => [
	'Banished!',
	'http://www.accurseddragon.com/site/index.php/archive/20100830a-2/',
	[class => qr/webcomic-object-full/],
],
BeaverAndSteve => [
	'Beaver and Steve',
	'http://www.beaverandsteve.com/index.php?comic=1',
	[src => qr'comics/'],
],
MuseAcademy => [
	'The Muse Academy',
	'http://www.themuseacademy.com/Archive/01Enrolment/01Enr_FC.html',
	[src => qr'images/Pages']
],
Marilith => [
	'Marilith',
	'http://www.marilith.com/archive.php?date=20041215',
	[src => qr'comics/\d{8}'],
],
GoodbyChains => [
	'Goodby Chains',
	'http://www.goodbyechains.com/index.php?page=1',
	[src => qr'files/pages/'],
],
OotS => [
	'The Order of the Stick',
	'http://www.giantitp.com/comics/oots0001.html',
	[src => qr'/comics/images/']
],
Building => [
	'Building 12',
	'http://www.building12.net/issue1/cover.htm',
	[src => qr'issue\d+/'],
],
Strays => [
	'Strays',
	'http://www.straysonline.com/comic/01.htm',
	[src => qr/^\d\d+/],
],
Twokinds => [
	'Twokinds',
	'http://twokindscomic.com/archive/?p=1',
	[id=>'cg_img']
],
Cheer => [
	'The Wotch: Cheer!',
	'http://www.cheercomic.com/?date=2005-08-09',
	[class=> 'comic2']
],
DevilBear => [
	'Devil Bear',
	'http://www.thedevilbear.com/?p=1',
	[id=>'cg_img'],
],
BoxjamsDoodle => [
	"Boxjam's Doodle",
	'http://boxjamsdoodle.com/d/19990422.html',
	[src => qr'/comics/bj\d{8}']
],
Yosh => [
	'Yosh!',
	'http://www.yoshcomic.com/latest.php?i=040321',
	[src => qr'strips/\d{6}'],
],
Ice => [
	'Ice',
	'http://www.faitherinhicks.com/ice/page1.html',
	[naturalsizeflag => 3],
],
KissingChaos => [
	"Kissing Chaos 'Til I Die", 
	'http://kissingchaos.com/tx/?p=4',
	[id => 'comic'],
],
DragonHeroes => [
	'Dragon Heroes',
	'http://dragonheroes.fanyart.com/comix-en.php?page=1',
	[id => 'pages']
],
NamirDeiter => [
	'Namir Deiter',
	'http://www.namirdeiter.com/comics/index.php?date=19991128',
	[src => qr'comics/\d{8}']
],
RealLife => [
	'Real Life',
	'http://reallifecomics.com/archive/991115.html',
	[class=>'comic_image']
],
PrepareToDie => [
	'Prepare To Die',
	'http://www.onyxsparrow.com/prepare_to_die/archive.cfm?issue=1&face=front',
	[src => qr'archive/Comic'],
],
AbstruseGoose => [
	'Abstruse Goose',
	'http://abstrusegoose.com/1',
	[_tag => 'img', class => 'aligncenter']
],
PhoenixRequiem => [
	'The Phoenix Requiem',
	'http://requiem.seraph-inn.com/viewcomic.php?page=1',
	[alt=> 'Page'],
],
Pixel => [
	'Pixel',
	'http://pixelcomic.net/000.shtml',
	[face => 'VERDANA']
],
Lackadaisy => [
	'Lackadaisy Cats',
	'http://lackadaisycats.com/comic.php?comicid=1',
	[id => 'content'],
],
VagabondLife => [
	'This Vagabond Life',
	'http://studio7manga.com/werks/tvl/webcomic/this-vagabond-life-chapter1-page1.html',
	[id=>'MainMid']
],
TalesOfPylea => [
	'Tales of Pylea',
	'http://top.talesofpylea.com/1/',
	[src => qr'istrip_files/strips/\d{8}']
],
DireDestiny => [
	'Dire Destiny',
	'http://www.diredestiny.com/index.php?option=com_rsgallery2&Itemid=0&page=inline&catid=1&id=454&limit=1&limitstart=0',
	[id => 'thumb1'],
],
CapesBabes => [
	'Capes & Babes',
	'http://www.capesnbabes.com/blog/the-debut-is-finally-here/',
	[id => 'comic']
],
MonkeyIsland => [
	'The Secret of Monkey Island',
	'http://www.worldofmi.com/features/comics/viewpage.php?id=1&comic_id=3',
	[src => qr'images/comics/']
],
GeistPanik => [
	'Geist Panik',
	'http://www.hookiedookiepanic.com/geist/comic.php?ID=1',
	[src => qr'^pics/'],
],
Diablito => [
	'Diablito del Ring',
	'http://www.diablitodelring.com/?id=1',
	[id => 'comicimg']
],
SophiaAwakening => [
	'Sophia Awakening',
	'http://parasitepublishing.com/wordpress/?webcomic_post=sophia01_v1b-e292316',
	[class=>qr'webcomic-object-full'],
],
DoctorVoluptua => [
	'Doctor Voluptua',
	'http://doctorvoluptua.com/go/1',
	[class => 'comicpage']
],
BearNuts => [
	'Bear Nuts',
	'http://www.bearnutscomic.com/2008/08/17/01-bear-nuts/',
	[id => 'comic']
],
HeroesOfLesserEarth => [
	'Heroes of Lesser Earth',
	'http://www.heroesoflesserearth.com/2005/12/pilot/',
	[class => 'alignnone size-full'],
],
OriginalLife => [
	'Original Life',
	'http://www.jaynaylor.com/originallife/archives/2009/06/001.html',
	[id => 'comicentry'],
],
WanderingOnes => [
	'The Wandering Ones',
	'http://wanderingones.com/story/1/1/',
	[alt => 'comic strip']
],
Goats => [
	'Goats',
	'http://www.goats.com/archive/970401.html',
	[id => 'stripcontainer'],
],
ScenesFromMultiverse => [
	'Scenes From A Multiverse',
	'http://amultiverse.com/2010/06/14/parenthood/',
	[id => 'comic-1']
],
FurWillFly => [
	'Fur Will Fly',
	'http://www.jadephoenix.org/fwf/comics/index.php?date=fur_fly1.jpg',
	[face => 'Arial,Helvetica']
],
goKC => [
	'goKC!',
	'http://www.gokc.tv/archived_pages/2003/20030616.htm',
	[src => qr'days/Strips/\d{4}/\d{8}']
],
SurvivingTheWorld => [
	'Surviving the World',
	'http://survivingtheworld.net/Lesson1.html',
	[src => qr/^Lesson\d/]
],
ZorphbertFred => [
	'Zorphbert & Fred',
	'http://www.zfcomics.com/comics/01082007/',
	[id => 'comic-1']
],
EternalVenture => [
	'Eternal Venture',
	'http://www.beaglespace.com/pulledpunches/venture/?p=3',
	[id => 'comic']
],
Galaxion => [
	'Galaxion',
	'http://galaxioncomics.com/1-comic/book-1/galaxion-volume-1-the-jump-cover/',
	[id => 'comic-1']
],
UnicornJelly => [
	'Unicorn Jelly',
	'http://unicornjelly.com/uni001.html',
	[hspace => 0, border => 0]
],
ToddAndPenguin => [
	'Todd and Penguin',
	'http://www.toddandpenguin.com/d/20010403.html',
	[alt => 'comic']
],
OhMyGods => [
	'Oh My Gods!',
	'http://ohmygods.co.uk/strips/2002-03-07',
	[class => 'omgs-strip']
],
PBF => [
	'The Perry Bible Fellowship',
	'http://www.pbfcomics.com/?cid=PBF001-Stiff_Breeze.gif',
	[id => 'topimg'],
	next => [_tag => 'a', text_match('Newer')],
],
ToyDivision => [
	'Toy Division',
	'http://toydivision.transplantcomics.com/daily.php?date=050410',
	[src => qr'images/comics']
],
DnDorks => [
	'Dungeons and Dorks',
	'http://dndorks.com/comics/10%2f15%2f2001.aspx',
	[src => qr'/images/comics/'],
],
OurHomeplanet => [
	'Our Home Planet',
	'http://www.ourhomeplanet.net/01.html',
	[src => qr'pages/comic'],
	next => [coords => '96,18,141,65']
],
Meta => [ #dead pages
	'The Everyday Adventures of Meta',
	'http://www.studio7manga.com/werks/meta/The_Everyday_Adventures_of_Meta_Ep_001_Technology_is_a_knife.html',
	[hspace => 60, vspace => 3],
	next => [src => qr'images/NAVIGATOR_05.png']
],
Altermeta => [
	'Altermeta',
	'http://altermeta.net/archive.php?comic=0&view=showfiller',
	[src => qr'comics/'],
	next => [src => qr'/forward.png'],
],
SarahZero => [
	'SarahZero',
	'http://sarahzero.com/sz_intro.html',
	[src => qr'^z_spreads'],
	next => [id => 'sz_07_nav']
],
Roza => [
	'Roza and the Horse Prince',
	'http://www.junglestudio.com/roza/?date=2007-05-01',
	[src=> qr'pages/'],
],
Afterstrife => [
	'Afterstrife',
	'http://afterstrife.com/?p=1',
	[id => 'comic'],
],
TodUndMaedchen => [
	'Der Tod und das Mädchen 2',
	'http://www.cartoontomb.de/deutsch/tod2.php?bild=00_01_01.jpg',
	[src => qr'images/tod/teil2/'],
	next => [_tag => 'a', text_match('weiter')],
],
Crowfeathers => [
	'Crowfeathers',
	'http://www.crowfeathers.net/crowfeatherscomic/index.php?p=1',
	[class => 'comicImage'],
	next => [alt => 'forward'],
],
Untitled => [
	'(Untitled)',
	'http://www.viruscomix.com/page199.html',
	[_tag => 'img']
],
Earthsong => [
	'Earthsong Saga',
	'http://www.earthsongsaga.com/vol1/vol1cover.html',
	[colspan => 3],
	next => [_tag => 'td', width => 71],
],
RodBarry => [
	'Rod & Barry',
	'http://www.rodandbarrycomic.com/2008/03/06/episode-4-superiority-complex/',
	[id => 'comic'],
],
Anatta => [
	'Anatta',
	'http://epidigm.net/anatta/?p=3',
	[id => 'comic'],
],
MyStupidLife => [
	'My Stupid Life',
	'http://www.mitchclem.com/mystupidlife/1/',
	[class => 'centeredImage']
],
Cherry => [
	'Magical Transvestite Cherry',
	'http://www.footloosecomic.com/cherry/cherry_main.php?page=1',
	[src => qr'/cherry/\d+']
],
MSFHigh => [
	'MSF High',
	'http://www.msfhigh.com/?date=2005-09-02',
	[class => 'comic2']
],
FurtiaHigh => [
	'Furtia High',
	'http://furthiahigh.concessioncomic.com/index.php?pid=20080128',
	[alt => 'Comic goes here.'],
],
Precocious => [
	'Precocious',
	'http://www.precociouscomic.com/comic.php?page=1',
	[src => qr'archive/strips/']
],
ByMoonAlone => [
	'By Moon Alone',
	'http://www.bymoonalone.com/comic.php?date=2008-01-01',
	[src => qr'comic/pages'],
	next => [src=> qr'forward01'],
],
CaptainStupendous => [
	'Captain Stupendous',
	'http://www.captainexcelsior.com/?id=1',
	[id => 'comic']
],
Keychain => [
	'Keychain of Creation',
	'http://keychain.patternspider.net/archive/koc0001.html',
	[id => 'Layer1'],
	next => [alt => 'forward'],
],
BuckGodot => [
	'Buck Godot',
	'http://www.airshipentertainment.com/buckcomic.php?date=20070111',
	[src => qr'buckcomic/strips/']
],
Sloan => [
	'Sloan - The Batttle of the Bands!',
	'http://www.viruscomix.com/sloan.html',
	[_tag => 'img'],
],
LoveInBlood => [
	'Love is in the Blood',
	'http://www.loveisintheblood.com/2008/04/29/welcome-to-love-is-in-the-blood/',
	[class => 'object']
],
UnlikeMinerva => [
	'Unlike Minerva',
	'http://www.unlikeminerva.com/comics/index.php?date=20010101',
	[src => qr'/comics/']
],
OneNineSevenSeven => [
	'1977 the comic',
	'http://www.1977thecomic.com/2008/01/02/01022008/',
	[id => 'comic-1'],
],
Cucuc => [
	'Cú cuC',
	'http://www.oghme.com/cucuc/post/001-Balor-doesn-t-like-Snails',
	[class=>'zestrip']
],
Masago => [
	'Masago',
	'http://masago.org/archive_page.php?comicID=1',
	[src => qr'episodes/\d{8}'],
],
OfTwoMinds => [
	'Of 2 Minds',
	'http://www.studioantithesis.com/of2minds/?strip_id=0',
	[src => qr'pages/'],
],
ZombieHunters => [
	'The Zombie Hunters',
	'http://thezombiehunters.com/tzh/index.php?strip_id=1',
	[id => 'comicdiv'],
],
PennyArcade => [ #dead pages
	'Penny Arcade',
	'http://www.penny-arcade.com/comic/1998/11/18',
	[class => 'body'],
],
DesperateAngels => [
	'Desperate Angels',
	'http://www.desperateangels.com/?p=6',
	[id => 'comic'],
],
AlaskaRobotics => [
	'Alaska Robotics',
	'http://akrobotics.com/comics/2007/01/29/launch-party',
	[id => 'comicPlate'],
],
Carzorthade => [
	'Carzorthade',
	'http://www.carzorthade.com/a/20021002.html',
	[src => qr'../jc/'],
],
GPF => [
	'General Protection Fault',
	'http://www.gpf-comics.com/d/19981102.html',
	[src => qr'/comics/gpf\d{8}'],
],
Blip => [
	'Blip',
	'http://blipcomic.com/1/',
	[class => 'stripborder'],
],
ChasingTheSunset => [
	'Chasing the Sunset',
	'http://www.fantasycomic.com/index.php?p=c1',
	[id => 'comic-img'],
	next => [id => 'comic-nav-right'],
],
SoapOnRope => [
	'Soap on a Rope',
	'http://www.soaprope.com/d/19970303.html',
	[alt => 'comic'],
],
InheritTheEarth => [
	'Inherit the Earth',
	'http://www.inherittheearth.net/comic.shtml?0001',
	[src => qr'/strips/\d{4}'],
],
MiracleOfScience => [
	'A Miracle of Science',
	'http://www.project-apollo.net/mos/mos000.html',
	[src => qr'manga/mos']
],
ClanOfCats => [
	'Clan of the Cats',
	'http://clanofthecats.com/cotc/cotc-cominghome/',
	[id => 'comic'],
],
SPQRBlues => [
	'SPQR Blues',
	'http://spqrblues.com/d/20051120.html',
	[alt => 'comic']
],
Nukees => [
	'Nukees',
	'http://www.nukees.com/d/19970121.html',
	[alt => 'comic'],
],
WapsiSquare => [
	'Wapsi Square',
	'http://wapsisquare.com/comic/09092001/',
	[id => 'comic-1'],
],
NeoEarthNENW => [
	'Neo-Earth N.E.N.W.',
	'http://www.neo-earth.com/NENW/index.php?date=2008-05-07',
	[src => qr'strips/\d{4}-\d\d-\d\d'],
],
Awakened => [
	'The Awakened',
	'http://www.theawakenedcomic.com/2011/01/09/chapter-one-page-one/',
	[id => 'comic-1'],
],
HaruSari => [
	'Haru-Sari',
	'http://haru-sari.com/by8w45/story/01/00.php',
	[id => 'content'],
],
EscapeFromTerra => [
	'Escape From Terrra',
	'http://www.bigheadpress.com/eft?page=1',
	[src => qr'/simages/eft/EFT\d\d-'],
],
WiguAdventures => [
	'Wigu Adventures',
	'http://www.wigucomics.com/adventures/index.php?comic=1',
	[src => qr'adventures/comics/'],
],
WiguFun => [
	"Wigu Havin' Fun",
	'http://www.wigucomics.com/fun/index.php?comic=1',
	[src => qr'fun/comics/'],
],
DelusionaryState => [
	'Delusionary State',
	'http://delusionarystate.com/index.php?date=2009-05-25',
	[src => qr'strips/\d{4}-\d\d-\d\d'],
],
Reiko => [
	'Reiko',
	'http://taintedink.com/?p=1274',
	[src => qr'/comics/'],
],
DirectionsOfDestiny => [
	'Directions of Destiny',
	'http://directionsofdestiny.com/archive/010/cover.htm',
	[id => 'pagebox'],
],
StarCrossedDesitiny => [
	'Star Crossed Destiny',
	'http://www.starcrossd.net/archives/00000001.html',
	[id => 'comic']
],
Whiteboard => [
	'The Whiteboeard',
	'http://www.the-whiteboard.com/autowb001.html',
	[src => qr'auto'],
],
Becky => [
	'Becky and the Masters of Gaming',
	'http://ray2kproduxions.com/comix/becky_comix/becky_comix_08-09/becky_page_0001.html',
	[class => 'comix_container']
],
TrueNuff => [
	'True Nuff',
	'http://comic.truenuff.com/comic.php?day=20010228',
	[src => qr'comics/truenuff\d{8}'],
],
GuiltyPleasures => [
	'Guilty Pleasures',
	'http://akrobotics.com/comics/2007/03/02/resolutions',
	[id => 'comicPlate'],
],
TwoGamerz => [
	'2Gamerz',
	'http://2gamerz.com/?p=47',
	[src => qr'/webcomic/'],
],
Jinxville => [
	'Welcome to Jinxville',
	'http://www.jinxville.com/welcome/2008/11/15/waddle-i-do/',
	[class => qr'comic-item'],
],
Gunnerkrigg => [
	'Gunnerkrigg Court',
	'http://www.gunnerkrigg.com/archive_page.php?comicID=1',
	[class => 'rss-id'],
],
Wondermark => [
	'Wondermark',
	'http://wondermark.com/001/',
	[id => 'comic'],
],
MNTG => [
	'Mutant Ninja Turtles Gaiden',
	'http://www.mntgaiden.com/en/?id=1',
	[id => 'comicimg'],
],
SandraAndWoo => [
	'Sandra and Woo',
	'http://www.sandraandwoo.com/2008/10/19/a-sly-raccoon/',
	[id => 'comic'],
],
SubCulture => [
	'Sub Culture',
	'http://subculturecomic.com/wp/?p=13',
	[id => 'comic'],
],
StarshipMoonhawk => [
	'Starship Moonhawk',
	'http://www.starshipmoonhawk.com/?p=383',
	[id => 'comic'],
],
LonelyPair => [
	'Lonely Pair',
	'http://www.coolfirebirdcomics.com/lpchapter1.htm',
	[border => 0, src => qr/^LP/],
	next => [src => 'arrowbackb2.png'],
],
DarkLegacy => [
	'Dark Legacy',
	'http://www.darklegacycomics.com/first.html',
	[_tag => 'td', rowspan => 2],
	next => [src => '6.gif'],
],
Bellen => [
	'Bellen',
	'http://boxbrown.com/?p=440',
	[id => 'comic'],
],
PunchAndPie => [
	"Punch an' Pie",
	'http://www.punchanpie.net/cgi-bin/autokeenlite.cgi?date=20070222',
	[src => qr'comics/pnp\d{8}'],
],
Comet => [
	'Comet 7',
	'http://www.comet7.com/archive_page.php?id=1',
	[src => qr'strips/\d{8}'],
],
TryingHuman => [
	'Trying Human',
	'http://tryinghuman.com/comic.php?strip_id=1',
	[src => qr'strips/'],
],
Kristy => [
	'Kristy versus the Zombie Army',
	'http://kristyvsthezombiearmy.com/strip_display.php?comicID=1',
	[src => qr'comics/\d{8}'],
],
FantasyRealms => [
	'Fantasy Realms',
	'http://www.fantasyrealmsonline.com/manga/001.php',
	[src => qr'^\d+\.'],
],
SpudComics => [
	'Spud Comics',
	'http://spudcomics.com/2008/11/19/cookie-monster/',
	[id => 'comic'],
],
RockPaperCynic => [
	'Rock Paper Cynic',
	'http://rockpapercynic.com/index.php?date=2008-10-31',
	[class => 'rss-content']
],
Buttersafe => [
	'Buttersafe',
	'http://buttersafe.com/2007/04/03/breakfast-sad-turtle/',
	[id => 'comic'],
],
DeathPiglet => [
	'Death Piglet',
	'http://chibi.nekrozin.com/index.php?strip_id=1',
	[src => qr'istrip_files/strips/\d{8}']
],
DarkReflections => [
	'Exiern: Dark Reflections',
	'http://darkreflections.exiern.com/index.php?strip_id=1',
	[src => qr'istrip_files/strips/\d{8}']
],
BladeBunny => [
	'Blade Bunny',
	'http://www.bladebunny.com/?p=20',
	[id => 'comic']
],
Sector => [
	'Sector 41',
	'http://www.viruscomix.com/page89.html',
	[_tag => 'img']
],
NewGoldDreams => [
	'New Gold Dreams',
	'http://www.newgolddreams.com/ngd1.shtml',
	[src => qr'arch/ngd']
],
TwoLumps => [
	'Two Lumps',
	'http://www.twolumps.net/d/20040316.html',
	[src => qr'/comics/']
],
Shortpacked => [
	'Shortpacked',
	'http://shortpacked.com/comic/book-1-brings-back-the-80s/01-just-a-toy-store/just-a-toy-store/',
	[id => 'comic-1'],
],
SuperEffective => [ #dead pages
	'Super Effective',
	'http://www.vgcats.com/super/?strip_id=0',
	[src => qr'^images/\d{6}'],
	next => [src => 'next.gif'],
],
AdventureLog => [
	'Adventure Log',
	'http://www.vgcats.com/ffxi/?strip_id=0',
	[src => qr'^images/\d{6}'],
	next => [src => 'next.gif'],
],
CollarSix => [
	'Collar Six',
	'http://collar6.com/archive/collar-6-1',
	[src => qr'webcomic/collar6/\d{4}-\d\d-\d\d'],
],
Cartridge => [
	'Cartridge',
	'http://www.cartridgecomics.com/archive_page.php?comicID=1',
	[src => qr'comikaze/comics/\d{8}']
],
AlienDice => [
	'Alien Dice',
	'http://aliendice.com/blog/2001/05/16/05162001/',
	[id => 'comic-1'],
],
Misteam => [
	'Misteam',
	'http://mste.illinois.edu/projects/misteam/index.php?id=1',
	[src => qr'images/misteam\d{4}']
],
Amazoness => [
	'Amazoness',
	'http://amazoness.co.uk/1.htm',
	[src => qr'^comics/'],
	next => [src => 'advance.gif']
],
ChocolateMilkmaid => [
	'Chocolate Milkmaid',
	'http://www.wlpcomics.com/adult/cm/001.html',
	[alt => 'Archived Comic']
],
DieselSweeties => [
	'Diesel Sweeties',
	'http://www.dieselsweeties.com/archive/1',
	[src=> qr'/hstrips/']
],
DMFA => [
	"Dan and Mab’s Furry Adventures",
	'http://www.missmab.com/Comics/Vol_001.php',
	[src => qr'Vol\d+'],
],
PerkiGoth => [
	'Perki Goth Candi Raver',
	'http://mutt.purrsia.com/main.php?date=03/24/2001',
	[src => qr'comics/\d{8}']
],
CrimsonDark => [
	'Crimson Dark',
	'http://www.davidcsimon.com/crimsondark/index.php?view=comic&strip_id=1',
	[src => qr'istrip_files']
],
SugarStars => [
	'Sugar Stars',
	'http://sugarstars.net/manga/view.php?p=1',
	[id => 'cg_img'],
],
JuathuurOne => [
	'Juathuur 1: One Way or Another',
	'http://www.oneway.juathuur.com/?id=1',
	[id => 'comicimg'],
],
Estar => [
	'Estar',
	'http://www.viruscomix.com/estarone.html',
	[_tag => 'img'],
],
KevinAndKell => [
	'Kevin and Kell',
	'http://www.kevinandkell.com/1995/kk0904.html',
	[id => 'comic'],
],
DaisyOwl => [
	'Daisy Owl',
	'http://www.daisyowl.com/comic/2008-07-03',
	[src => qr'/comic_images/'],
],
HiddenDisguises => [
	'Hidden Disguises',
	'http://www.hiddendisguises.com/strip1/',
	[class => 'object'],
],
EGSFiller => [
	'El Goonish Shive: Filler',
	'http://www.egscomics.com/sketchbook/?date=2002-07-01',
	[class => 'comic2'],
],
NickTriggerAceDick => [
	'Nick Trigger Ace Dick',
	'http://www.supafine.com/comics/ntd.php?comicID=1',
	[src => qr'comics/ntd/\d{8}'],
],
SomethingPositiveOld => [
	'Something Positive 1937',
	'http://www.classicallypositive.net/sp09071937.shtml',
	[src => qr'arch/sp'],
],
SecondShift=> [
	'2nd Shift',
	'http://2ndshiftcomic.com/index.php?action=comics&cid=2',
	[alt => '2nd Shift'],
],
LovesRaymond => [
	'Everybody loves Eric Raymond',
	'http://geekz.co.uk/lovesraymond/archive/slashdotted',
	[class => 'entry'],
	next => [_tag => 'div', class => 'navigation'] ,[_tag => 'div', class => 'alignright']
],
HardGraft => [
	'Hard Graft',
	'http://hard-graft.net/?p=33',
	[id => 'comic-1'],
],
GeekHero => [
	'Geek Hero',
	'http://www.geekherocomic.com/2008/03/03/03032008/',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
],
Krakow => [
	'Krakow',
	'http://www.krakowstudios.com/krakow/archive.php?date=20001005',
	[src => qr'comics/\d{8}'],
],
CustomersSuck => [
	'Customers Suck',
	'http://www.customerssuck.com/strip/index.php?date=2006-04-16',
	[src => qr'strips/'],
],
SanAntonioRockCity => [
	'San Antonio Rock City',
	'http://www.mitchclem.com/rockcity/index.php?comic=1',
	[alt => 'comic'],
	next => [alt => 'right_arrow'],
],
TheaterHopper => [
	'Theater Hopper',
	'http://www.theaterhopper.com/2002/08/05/rich-little-he-aint/',
	[id => 'comic-1'],
],
Ayane => [
	'Ayane',
	'http://ayane.tsunami-art.com/view.aspx?Rec=1',
	[id => 'ITD']
],
SuburbanTribe => [
	'Suburban Tribe',
	'http://www.pixelwhip.com/?p=83',
	[id => 'comic'],
],
Copper => [
	'Copper',
	'http://www.boltcity.com/copper/copper_001_rocketpackfantasy.htm',
	[src=> qr'copperstrips/copper'],
],
GamingGuardians => [
	'Gaming Guardians',
	'http://gamingguardians.com/2000/05/07/05072000/',
	[id => 'comic-1'],
],
CRFH => [
	'College Roomies from Hell',
	'http://www.crfh.net/d/19990101.html',
	[alt => 'click for next comic'],
],
GirlAndFed => [
	'A Girl and Her Fed',
	'http://agirlandherfed.com/comic/?0',
	[src => qr'/images/comics/'],
],
MildlyHotPeppers => [
	'Mildly Hot Peppers',
	'http://www.mildlyhotpeppers.com/comics/1/',
	[class => 'comic'],
],
YAFGC => [
	'Yet Another Fantasy Gamer Comic',
	'http://yafgc.net/?id=1',
	[id => 'comicimg'],
],
Fantasticness => [
	'Fantasticness',
	'http://www.supafine.com/comics/fant.php?comicID=1',
	[src => qr'/comics/fant/\d{8}'],
],
Crap => [
	'Crap I Drew On My Lunch Break',
	'http://crap.jinwicked.com/2003/07/30/jin-and-josh-decide-to-move/',
	[id => 'comic'],
],
Oriyan => [
	'Oriyan',
	'http://jadephoenix.org/oriyan/strip/cover1.html',
	[src => qr'oriyan/images/'],
],
Cemedity => [
	'Comedity',
	'http://www.comedity.com/index.php?strip_id=1',
	[name => 'comic'],
	next => [alt => 'Ensuing Strip'],
],
HolyBible => [
	'Holy Bible',
	'http://holybibble.net/latest.php?id=1',
	[src => qr'images/strips/'],
],
FungusGrotto => [
	'Fungus Grotto',
	'http://www.destiny-makers.net/fg/fg_cov.html',
	[src => qr'/fg/pages/']
],
Fera => [
	'Fera',
	'http://angelk.at/fera/chronicles/fera-title/',
	[id => 'comic']
],
StealThisComic => [
	'Steal This Comic',
	'http://www.stealthiscomic.com/2008/03/10/welcome-to-the-show/',
	[id => 'comic'],
],
Nodwick => [
	'Nodwick',
	'http://nodwick.humor.gamespy.com/gamespyarchive/index.php?date=2001-03-27',
	[src => qr'strips/\d{4}-\d\d-\d\d'],
	next => [src => 'forward.jpg'],
],
PursuitMandy => [
	'The Pursuit of Mandy',
	'http://thepursuitofmandy.com/?p=23',
	[id => 'comic']
],
GirlGenius => [
	'Girl Genius',
	'http://www.girlgeniusonline.com/comic.php?date=20021104',
	[alt => 'Comic'],
],
TheWotch => [
	'The Wotch',
	'http://www.thewotch.com/?epDate=2002-11-21',
	[title => qr'^Comic for'],
],
EnkersTale => [
	'Enkers Tale',
	'http://www.coloringdragons.com/enker/page001.htm',
	[src => qr'^images/Page'i]
],
Saturnalia => [
	'Saturnalia',
	'http://spacecoyote.com/comics/sat/comic.php?page=0101',
	[src => qr'/comics/sat/\d\d/\d{4}']
],
Jack => [
	'Jack',
	'http://www.pholph.com/strip.php?id=5&sid=363',
	[src => qr'^\./artwork/../.+/Jack\d{8}']
],
NerfNow => [
	'Nerf Now',
	'http://nerfnow.com/comic/4',
	[id => 'comic'],
	next => [id => 'nav_next'],
],
GottGaussDe => [
	'Gott Gauss (Deutsch)',
	'http://gottgauss.viviane.ch/gg-manga/de/',
	[src => qr'seiten/'],
],
GottGaussEn => [
	'Gott Gauss (English)',
	'http://gottgauss.viviane.ch/gg-manga/en/',
	[src => qr'seiten/'],
],
EinStueckWahnsinn => [
	'Ein Stück Wahnsinn',
	'http://wahnsinn.viviane.ch/wahnmanga/wahnmanga01.htm',
	[src => qr'manga/wahn'],
	next => [name => 'pfeil3']
],
Cubetoons => [
	'Cubetoons',
	'http://www.cubetoons.com/index.php?option=com_content&view=article&id=51%3Alevel-1',
	[src => qr'/images/stories/cubetoons/']
],
CaptainSNES => [
	'Captain SNES',
	'http://www.captainsnes.com/2001/07/10/the-mistake/',
	[src => qr'/comics/\d{4}-\d\d-\d\d']
],
Zap => [
	'Zap',
	'http://www.zapcomic.com/2003/07/20030713/',
	[class => qr'^comic-item'],
],
Yamara => [
	'Yamara',
	'http://www.yamara.com/yamaraclassic/index.php?date=2005-05-23',
	[src => qr'(strips|war)/\d{4}-\d\d-\d\d'],
	next => [title => qr'Next|On to The Working Title War!'],
],
TodayNothingHappened => [
	'Dear Diary, Today Nothing Happened',
	'http://www.shazzbaa.com/index.php?c=1',
	[id => 'comic'],
],
FamilyMan => [
	'Family Man',
	'http://www.lutherlevy.com/?p=3',
	[id => 'comic'],
],
PartiallyClips => [
	'Partially Clips',
	'http://www.partiallyclips.com/index.php?id=1054',
	[id => 'comic']
],
Thistledown => [
	'Tales of Thistledown',
	'http://talesofthistledown.com/?p=19',
	[id => 'comic'],
],
Aikida => [
	'Aikida',
	'http://www.aikida.net/2008/05/26/the-return-strip/',
	[id => 'comic'],
],
LukeSurl => [
	'Luke Surl',
	'http://www.lukesurl.com/archives/66',
	[id => 'comic']
],
DungeonCrawlInc => [
	'Dungeon Crawl Inc.',
	'http://www.dungeoncrawlinc.com/comic1.html',
	[src => qr'DCI_'],
],
#ProjectAusserdem => [
#	'Project Außerdem',
#	'http://rono64.com/?p=12',
#	[id => 'comic-1'],
#],
KrakowTwo => [
	'Krakow 2.0',
	'http://www.krakowstudios.com/krakow20/archive.php?date=20080121',
	[src => qr'comics/\d{8}'],
],
EmeraldWinter => [
	'Emerald Winter',
	'http://www.emeraldwinter.net/ew/cover001.php',
	[src => qr'pages/'],
],
VanHunter => [
	'Van von Hunter',
	'http://www.vanvonhunter.com/vvh1.html',
	[src => qr'/strips/vvh_'],
	next => [alt => 'Next Comic'],
],
IrregularWebcomic => [
	'Irregular Webcomic',
	'http://www.irregularwebcomic.net/1.html',
	[src => qr'/comics/irreg\d{4}'],
	next => [_tag => 'a', text_match('>') ],
],
OctopusPie => [
	'Octopus Pie',
	'http://www.octopuspie.com/index.php?date=2007-05-14',
	[src => qr'/strippy/\d{4}-\d\d-\d\d'],
],
George => [
	'George',
	'http://www.george-comics.com/2005/01/31/its-probably-a-water-buffalo/',
	[id => 'comic'],
],
BrawlInTheFamily => [
	'Brawl in the Family',
	'http://www.brawlinthefamily.com/comic001.html',
	[src => qr'Images/\d\d'i],
	url_hack => sub {$_[0] =~ s'/../'/'; $_[0]}
],
Stubble => [
	'Stubble',
	'http://www.stubblecomics.com/d/001.html',
	[src => qr'/comics/\d{3}'],
	next => [src => qr'^/fowardarrow.gif']
],
HarkVagrant => [
	'Hark! a Vagrant',
	'http://www.harkavagrant.com/index.php?id=1',
	[class => 'rss-content'],
],
DarkWings => [
	'Dark Wings',
	'http://www.flowerlarkstudios.com/dark-wings/archive.php?day=20080531',
	[class => 'comicPic_avail'],
],
ThreePanelSoul => [
	'Three Panel Soul',
	'http://www.threepanelsoul.com/view.php?date=2006-11-05',
	[class => 'rss-content'],
],
Concerned => [
	'Concerned',
	'http://www.hlcomic.com/index.php?date=2005-05-01',
	[src => qr'comics/concerned'],
],
Omegamanor => [
	'Omega Manor',
	'http://www.omegamanor.wildtwilight.com/0000.html',
	[src => qr'^maid \d{4}']
],
Doghouse => [
	'The Dog House Diaries',
	'http://www.thedoghousediaries.com/?p=34',
	[class => 'object'],
	next => [ class => 'next-comic-link' ],
],
LiliDeacon => [
	'The Legend of Lili Deacon',
	'http://www.cartoonfrolics.com/08/07/2008/page-thirty-six-know-when-yer-licked/',
	[id => 'comic-1'],
],
OutAtFive => [
	'Out at Five',
	'http://www.outatfive.com/?p=84',
	[id => 'comic'],
],
ParallelDementia => [
	'Parallel Dementia',
	'http://pd.milkinthepantry.com/?strip_id=0',
	[src => qr'comics/\d{6}'],
	next => [alt => 'Next'],
],
DresdenCodak => [
	'Dresden Codak',
	'http://dresdencodak.com/2005/06/08/the-tomorrow-man/',
	[id => 'comic'],
],
Megatokyo => [
	'Megatokyo',
	'http://megatokyo.com/strip/1',
	[id => 'strip-bl'],
	url_hack => sub { $_[0] =~ s'/strip/'/'; $_[0] }
],
DresdenCodak => [
	'Dresden Codak',
	'http://dresdencodak.com/2005/06/08/the-tomorrow-man/',
	[id => 'comic'],
],
RegisteredWeapon => [
	'Registered Weapon',
	'http://registered-weapon.com/2009/01/12/the-beginning/',
	[id => 'comic'],
],
Heliothaumatic => [
	'Heliothaumatic',
	'http://thaumic.net/2007/08/1-at-the-academy/',
	[id => 'comic'],
],
WTBDignity => [
	'Want To Buy Dignity',
	'http://www.hawkvspigeon.com/index.php/2008/10/24/0001/',
	[id => 'comic-1'],
],
ToSaveHer => [
	'To Save Her',
	'http://www.pasteldefender.com/to%20save%20her%20000.html',
	[src => qr'images/tsh'],
	next => [_tag => 'font', color => 'DARKSLATEGRAY', text_match(qr'forward'i) ],
],
CulricsChronicles => [
	"Culric's Chronicles",
	'http://www.culricschronicles.com/webcomic/comicpage1.html',
	[class => 'comicborder'],
],
SlightlyDamned => [
	'Slightly Damned',
	'http://www.sdamned.com/2004/03/03142004/',
	[id => 'comic'],
],
EdgeTheDevilhunter => [
	'Edge the Devilhunter',
	'http://www.edgethedevilhunter.com/comics/chapter-1-genesis-remix',
	[id => 'comic'],
],
DailyDinosaurComics => [
	'Daily Dinosaur Comics',
	'http://www.qwantz.com/archive/000001.html',
	[id => 'comic'],
],
twotwoonefour => [
	'2214',
	'http://www.nitrocosm.com/go/2214_classic/1/',
	[class => 'gallery_display'],
],
AntsOnline => [
	'Ants Online',
	'http://antsonline.co.nz/?p=6',
	[id => 'comic-1'],
],
RoosterTeethComics => [
	'Rooster Teeth Comics',
	'http://roosterteeth.com/comics/strip.php?id=188',
	[style => qr'max-width:575px;'],
	next => [src => 'http://images.roosterteeth.com/assets/style/images/comicbar/arrowRight.png'],
],
SlowWave => [
	'Slow Wave',
	'http://www.slowwave.com/index.php?date=04-01-01',
	[src => qr'^/Img/s\d']
],
Dreamer => [
	'The Dreamer',
	'http://thedreamercomic.com/comic.php?id=1',
	[src => qr'issues/issue_']
],
Catena => [
	'Catena',
	'http://catenamanor.com/2003/06/17/the-start-of-it-all/',
	[id => 'comic'],
],
JumpfourtwoDE => [
	'Jump 42 (deutsch)',
	'http://jump42.de/?p=13',
	[id => 'comic'],
],
JumpfourtwoEN => [
	'Jump 42',
	'http://jump42.net/?p=3',
	[id => 'comic'],
],
DraconiaChronicles => [
	'The Draconia Cronicles',
	'http://www.2wconline.net/draconia_001.html',
	[src => qr'/draconia\d+'],
],
LessonIsLearned => [
	'A Lesson Is Learned But The Damage Is Irreversible',
	'http://www.alessonislearned.com/index.php?comic=1',
	[src => qr'^cmx/lesson'],
],
TwoRooks => [
	'Two Rooks',
	'http://two-rooks.com/volume-i/',
	[id => 'comic'],
],
BadassMuthas => [
	'Badass Muthas',
	'http://badassmuthas.com/pages/comic.php?1',
	[src => qr'^/images/comicsissue'],
],
Kagerou => [
	'Kagerou',
	'http://www.electric-manga.com/01/kageroumenu.html',
	[_tag => 'img'],
	next =>[_tag => 'a', sub { $_[0]->as_text ~~ qr'\w+' or $_[0]->look_down(_tag=>'img')} ],
],
Vreakerz => [
	'Vreakerz',
	'http://vreakerz.angrykitten.nl/comic.php?comicNr=1',
	[src => qr'pages/vz_\d{8}'],
],
SuburbanJungleClassic => [
	'The Suburban Jungle',
	'http://suburbanjungleclassic.com/?p=10',
	[id => 'comic'],
],
SED => [
	'Serious Emotional Distrurbance',
	'http://sediverse.com/2008/02/02182008/',
	[id => 'comic'],
],
Templaraz => [
	'Templar, Arizona',
	'http://templaraz.com/?p=879',
	[id => 'comic']
],
ButtercupFestival => [
	'Buttercup Festival',
	'http://www.buttercupfestival.com/2-1.htm',
	[src => qr'\d+-\d+\.png'i],
],
KittyHawk => [
	'Kitty Hawk',
	'http://kittyhawkcomic.com/2008/07/29/kittyhawkcover/',
	[id => 'comic']
],
ChugworthAcademy => [
	'Chugworth Academy',
	'http://chugworth.com/archive/?strip_id=0',
	[src => qr'comics/\d{6}'],
],
Chugworth => [
	'Chugworth',
	'http://chugworth.com/?p=12',
	[id => 'comic'],
],
DuelingAnalogs => [
	'Dueling Analogs',
	'http://www.duelinganalogs.com/comic/2005/11/17/luigis-anal-adventure/',
	[id => 'comic'],
],
OmakeTheater => [
	'Omake Theater',
	'http://omaketheater.com/comic/1/',
	[id => 'comic'],
],
GreaterGood => [
	'A Path To Greater Good',
	'http://www.neorice.com/aptgg_1',
	[id => 'comic'],
],
WingsOfWishes => [
	'Wings of Wishes',
	'http://chrisschrossed.xepher.net/manga/v2pages/ch0/p1.html',
	[_tag => 'img']
],
WayToYourHeart => [
	'The Way To Your Heart',
	'http://emi-art.com/twtyh/1_cover.html',
	[_tag => 'img'],
],
BizarreUprising => [
	'Bizarre Uprising',
	'http://www.bizarreuprising.com/view/1/awakening-splash',
	[class => 'v_comiccontent'],
	url_hack => sub { $_[0] =~ s'/view/\d*/'/'; $_[0] }
],
AbleAndBaker => [
	'Able & Baker',
	'http://jimburgessdesign.com/comics/index.php?comic=1',
	[class => 'comic_content'],
],
EGSNewspaper => [
	'El Goonish Shive: Newspaper',
	'http://www.egscomics.com/egsnp/?date=2004-02-24',
	[class => 'comic2'],
],
TwilightLady => [
	'Twilight Lady',
	'http://www.twilightlady.com/2008/03/16/the-secret-of-cass-corridor-2/',
	[id => 'comic'],
],
Helpdesk => [
	'Helpdesk',
	'http://ubersoft.net/comic/hd/1996/03/alex-loss-words',
	[class => qr'imagefield-field_comic_image'],
],
NotIncluded => [
	'Not Included',
	'http://not-included.net/comics.php?id=1',
	[id => 'content'],
],
Landscraper => [
	'The Landscraper',
	'http://landscaper.visual-assault.net/2008/08/13/issue1-cover/',
	[id => 'comic'],
],
Inverloch => [
	'Inverloch',
	'http://inverloch.seraph-inn.com/viewcomic.php?page=1',
	[id => 'main'],[src => qr'^pages/'],
],
SMBC => [
	'Saturday Morning Breakfast Cereal',
	'http://www.smbc-comics.com/index.php?db=comics&id=1',
	[class => 'comicboxcenter'],[src => qr'/comics/\d{8}(?!after)'],
	next => [_tag => 'area', coords => "351,21,425,87"],
],
SSWestern => [
	'Forgiven Sins A Spaghetti Strap Western',
	'http://www.sswestern.com/page-0/',
	[id => 'comic'],
],
RobTheBot => [
	'Rob The Bot',
	'http://robthebot.com/mocha/',
	[id => 'comic'],
],
UnconsciousInk => [
	'Unconscious Ink',
	'http://artisticdoom.com/ui/bun-bun/',
	[id => 'comic-1'],
],
ZombieHunters => [
	'The Zombie Hunters',
	'http://www.thezombiehunters.com/index.php?strip_id=1',
	[id => 'comicdiv'],
],
AnhedoniaBlue => [
	'Anhedonia Blue',
	'http://abluecomic.com/chapter-1-kindled-eye-pg-1/',
	[id => 'comic-1'],
],
LAWLS => [
	'Large Air Whales Like Silence',
	'http://lawlscomic.com/whales-are-assholes/',
	[id => 'comic-1'],
],
NerfThis => [
	'Nerf This',
	'http://nerf-this.com/03232009/',
	[id => 'comic-1'],
],
ScoutCrossing => [
	'Scout Crossing',
	'http://scoutcrossing.net/000-scoutcrossing/',
	[id => 'comic-1'],
],
MotoKool => [
	'Motokool',
	'http://www.motokool.net/000/',
	[id => 'comic-1'],
],
SailorSin => [
	'Sailor Sun',
	'http://sailorsun.org/?p=21',
	[id => 'comic'],
],
Gigaville => [
	'Gigaville',
	'http://www.gigaville.com/comic.php?id=1',
	[src => qr'images/'],
],
JoyceAndWalky => [
	'Joyce and Walkie!',
	'http://www.joyceandwalky.com/d/19970908.html',
	[src => qr'/comics/\d{8}']
],
Candi => [
	'Candi',
	'http://www.candicomics.com/d/20040625.html',
	[src => qr'/comics/\d{8}'],
],
RoadWaffles => [
	'Road Waffles',
	'http://roadwaffles.com/index.php?c=1999-11-8',
	[src => qr'comics/rw\d{8}'],
],
LegendOfBill => [
	'Legend of Bill',
	'http://www.legendofbill.com/2008/05/27/the-legend-begins/',
	[id => 'comic'],
],
DogEatDoug => [
	'Dog Eat Doug',
	'http://dogeatdoug.com/2005/11/27/ded-back-online/',
	[id => 'comic'],
],
Ding => [
	'Ding!',
	'http://www.crispygamer.com/comics/ding/ding-2008-01-01.aspx',
	[src => qr'comics/ding/ding'],
	next => [class => 'comics-next'],
],
HaikuComics => [
	'Haiku Comics',
	'http://haikucomics.com/2009/01/14/haikuone/',
	[id => 'comic-1'],
],
SofterWorld => [
	'A Softer World',
	'http://www.asofterworld.com/index.php?id=1',
	[src => qr'clean/'],
],
DesertRocks => [
	'Desert Rocks',
	'http://dr.ungroup.net/chapter1/pages/page_1.php',
	[_tag => 'img', src => qr'^\.\./images/\d+\.'],
],
SchlockMercenary => [
	'Schlock Mercenary',
	'http://www.schlockmercenary.com/2000-06-12',
	[id => 'comic'],
],
GWS => [
	'Girls With Slingshots',
	'http://www.girlswithslingshots.com/comic/gws1/',
	[src => qr'\d{4}-\d\d-\d\d'],
],
Schism => [
	'Schism',
	'http://schism.lost-soleil.com/comic.php?ID=1',
	[id => 'page'],
],
BCK => [
	'Blue Crash Kit',
	'http://www.robhamm.com/bluecrashkit/comics/blue-crash-kit/2005-01-03',
	[class => qr'imagefield-field_comic_field'],
],
SluggyFreelance => [
	'Sluggy Freelance',
	'http://www.sluggy.com/daily.php?date=970825',
	[class => 'comic_content'],
],
FindersKeepers => [
	'Finders Keepers',
	'http://www.finderskeepers.gcgstudios.com/?p=comic&chap=0&cid=0',
	[id => 'FKsp'],
	next => [alt => qr'^Next', href => qr'^[^#]+$'],
],	
UpUpDownDown => [
	'Up Up Down Down',
	'http://upup-downdown.com/comics/2010/09/01/darkstrigers/',
	[id => 'comic-1'],
],
ProjectROL => [
	'Project ROL: Ratification of Lambs',
	'http://www.projectrol.com/1',
	[src => qr'comic/\d+'],
],
WereGeek => [
	'Were Geek',
	'http://www.weregeek.com/2006/11/27/',
	[id => 'comic'],
],
WhatsNew => [
	"What's New?",
	'http://www.airshipentertainment.com/growfcomic.php?date=20070107',
	[alt => 'Comic'],
],
Meek => [
	'The Meek',
	'http://www.meekcomic.com/2008/12/27/chapter-1-cover/',
	[id => 'comic'],
],
QuestionableContent => [
	'Questionable Content',
	'http://www.questionablecontent.net/view.php?comic=1',
	[id => 'strip'],
],
DreamkeepersPrelude => [
	'Dreamkeepers Prelude',
	'http://www.dreamkeeperscomic.com/Prelude.php?pg=1',
	[src => qr'images/PreludeNew/P\d+'],
	next => [src => qr'parrow_right.jpg'],
],
ICQ => [
	'Impala, Cat & Quagga',
	'http://www.icq-comic.com/archive.php?page=1',
	[alt => 'Comic Page'],
],
ErosInc => [
	'Eros Inc.',
	'http://www.commonnamefilms.com/erosinc/2008/07/01/the-nosy-mrs-spiegl/',
	[src => qr'erosinc/comics'],
],
Fantasty => [
	'Fantasty',
	'http://www.omgfantasty.com/01-01/',
	[src => qr'Episode'],
],
MoonTown => [
	'Moon Town',
	'http://moon-town.com/comic/episode-one/',
	[id => 'comic-1'],
],
Darken => [
	'Darken',
	'http://darkencomic.com/?webcomic_post=20031217',
	[class => qr'webcomic-object-full'],
],
BetterDays => [
	'Better Days',
	'http://www.jaynaylor.com/betterdays/archives/2003/04/post-2.html',
	[id => 'comicentry'],
],
ImagineThis => [
	'Imagine This',
	'http://imaginethiscomic.com/?p=5',
	[id => 'comic'],
],
Fallen => [
	'Fallen',
	'http://fallencomic.com/pages/part%201/cover-p1.htm',
	[src => qr'page/'],
],
QueenOfWands => [
	'Queen Of Wands',
	'http://www.queenofwands.net/d/20020722.html',
	[src => qr'/comics/\d{8}'],
],
MysticRevolution => [
	'Mystic Revolution',
	'http://www.mysticrev.com/index.php?cid=1',
	[id => 'comic'],
],
SisterClaire => [
	'Sister Claire',
	'http://www.sisterclaire.com/comic/chapter-1-comic/coming-soon/',
	[id => 'comic-1'],
],
Maliki => [
	'Maliki',
	'http://www.maliki.com/en/eng_strip.php?strip=68',
	[src => qr'Strips'],
],
GoGetARoomie => [
	'Go Get A Roomie',
	'http://gogetaroomie.chloe-art.com/2010/05/go-get-a-roomie-10/',
	[id => 'comic-1'],
],
BattleSuitGirls => [
	'Battle Suit Girls',
	'http://bsgcomic.free.fr/?p=1',
	[src => qr'/wp-content/uploads/'],
],
Brightest => [
	'Brightest',
	'http://www.brightestcomic.com/?p=46',
	[id => 'comic'],
],
Inkdolls => [
	'Inkdolls',
	'http://inkdolls.com/comic/001/',
	[src => qr'/strips/\d+-?[a-zA-Z]+'],
],
SlimyThief => [
	'Slimy Thief',
	'http://slimythief.com/intro/',
	[id => 'comic'],
],
ShipInABottle => [
	'Ship In A Bottle',
	'http://shipinbottle.pepsaga.com/comic/shiphrah-test/',
	[id => 'comic-1'],
],
Oglaf => [
	'Oglaf',
	'http://oglaf.com/cumsprite/',
	[id => 'strip'],
	next => [id => 'nx'], 
],
Annyseed => [
	'Annyseed',
	'http://www.colourofivy.com/annyseed_webcomic.htm',
	[_tag => 'body'], [_tag => 'table'], [_tag => 'tr'], [_tag => 'td'], [_tag => 'table'], [_tag => 'tr'], [_tag => 'td'], [_tag => 'img', src=> qr'Annyseed']
],
PepperminntSaga => [
	'Pepperminnt Saga',
	'http://www.pepsaga.com/comics/10202007/',
	[_tag => 'body'], [_tag => 'div'], [_tag => 'div'], [_tag => 'div'], [_tag => 'div'], [_tag => 'div'], [_tag => 'div'], [_tag => 'a'], [_tag => 'img']
],
RadioactivePanda => [
	'Radioactive Panda',
	'http://www.radioactivepanda.com/comic/1',
	[_tag => 'body', , ], [_tag => 'table', id => 'container', ], [_tag => 'tr', , ], [_tag => 'td', id => 'content', ], [_tag => 'div', id => 'comic', ], [_tag => 'div', , ], [_tag => 'div', , class => 'border'], [_tag => 'img', id => 'comicimg', ]
],
GhastlysGhastlyComic => [
	q{Ghastly's Ghastly Comic},
	q{http://www.ghastlycomic.com/d/20010509.html},
	[q{link},q{#b25c00},q{alink},q{#ff9c0f},q{vlink},q{#8c2b00},q{bgcolor},q{#ffe8cf},q{text},q{#000000},q{background},q{/images/bg2.gif},q{_tag},q{body}], [q{face},q{arial},q{_tag},q{font}], [q{_tag},q{center}], [q{_tag},q{font},q{size},q{5}], [q{_tag},q{font},q{size},q{3}], [q{_tag},q{p}], [q{cellspacing},q{4},q{_tag},q{table},q{cellpadding},q{4}], [q{_tag},q{tr}], [q{_tag},q{td}], [q{_tag},q{center}], [q{width},q{510},q{alt},q{Comic},q{_tag},q{img},q{height},q{1325},q{border},q{0}]
],
PostNuke => [
	'Post Nuke',
	'http://www.postnukecomic.com/comic_page.php?issue=1&page=0',
	[src => qr'/images/pn'],
	url_hack => sub { $_[0] =~ s'\.\./''; $_[0] }
],
Optipress => [
	'Optipress',
	'http://www.optipess.com/2008/12/01/jason-friend-of-the-butterflies/',
	[id => 'comic'],
],
FriendlyHostility => [
	'Friendly Hostility',
	'http://www.friendlyhostility.com/d/20040108.html',
	[id => 'comic'],
	next => [alt => 'Next'],
],
StrawberryDeathCake => [
	'Strawberry Death Cake',
	'http://rainchildstudios.com/strawberry/?p=19',
	[id => 'comic'],
],
Uncubed => [
	'Uncubed',
	'http://www.uncubedthecomic.com/2007/08/25/first-comic/',
	[id => 'comic'],
],
Chisuji => [
	'Chisuji',
	'http://www.chisuji.com/2009/05/02/chisujiposter01/',
	[id => 'comic'],
],
FalconTwin => [
	'Falcon Twin',
	'http://www.falcontwin.com/index.html?strip=0',
	[id => 'strip'],
],
NeoEarth => [
	'Neo Earth',
	'http://www.neo-earth.com/NE/index.php?date=2007-03-23',
	[src => qr'strips/'],
],
KernelPanic => [
	'Kernel Panic',
	'http://www.ubersoft.net/comic/kp/2002/09/alan-work',
	[class => 'imagefield imagefield-field_comic_image'],
],
UnderPower => [
	'Under Power',
	'http://underpower.non-essential.com/index.php?comic=20010822',
	[src => qr'comics/'],
],
Misfile => [
	'Misfile',
	'http://www.misfile.com/?page=1',
	[src => qr'pageCalled'],
],
Millennium => [
	'Millenium',
	'http://millennium.senshuu.com/pages/01cover.html',
	[class => qr'comic-item'],
],
Blastwave => [
	'Gone With The Blastwave',
	'http://www.blastwave-comic.com/index.php?p=comic&nro=1',
	[src => qr'/comics/'],
],
GreystoneInn => [
	'Greystone Inn',
	'http://greystoneinn.net/d/20000214.html',
	[src => qr'/comics/'],
],
Bunny => [
	'Bunny',
	'http://www.bunny-comic.com/1.html',
	[src => qr'strips/'],
	next => [src => 'resource/nav_next.png'],
],
TriangleDude => [
	'Triangle Dude',
	'http://www.tdcomics.com/viewcomic.php?id=1',
	[src => qr'Image/Comic/'],
],
RickyRay => [
	'The Ricky Ray Show',
	'http://ray2kproduxions.com/comix/ricky_comix/ricky_comix_hs/rr_episode_0031.html',
	[class => 'comix_container'],
],
Decorum => [
	'Decorum',
	'http://www.decorumcomics.com/comic.php?id=3',
	[class => 'comicimage'],
],
Angels => [
	'Angels 2200',
	'http://www.janahoffmann.com/angels/2005/11/11/part-1-title-comic/',
	[id => 'comic'],
],
Loserz => [
	'Loserz',
	'http://bukucomics.com/loserz/go/1',
	[class => 'comicpage'],
],
PlatinumGrit => [
	'Platinum Grit',
	'http://www.platinumgrit.com/issue01.htm',
	[src => qr'issue\d\d\.dcr'],
],
BitTheater => [
	'8 Bit Theater',
	'http://www.nuklearpower.com/2001/03/02/episode-001-were-going-where/',
	[id => 'comic'],
],
BrokenPlotDevice => [
	'Broken Plot Device',
	'http://www.brokenplotdevice.com/2008/06/05/first-comic/',
	[id => 'comic'],
],
YouSayItFirst => [
	'You Say it First',
	'http://www.yousayitfirst.com/comics/index.php?date=20040223',
	[src => qr'/comics/'],
],
TinyKittenTeeth => [
	'Tiny Kitten Teeth',
	'http://www.tinykittenteeth.com/2009/01/26/gene-kelly/',
	[id => 'comic'],
],
OccasionalComics => [
	'Occasional Comics Disorder',
	'http://occasionalcomics.com/79/latest-comic-2/',
	[id => 'comic'],
],
LetTheGameBegin => [
	'Let The Game Begin',
	'http://2gamerz.com/?webcomic_post=let-the-game-begin-chapter-1-cover',
	[src => qr'/webcomic/let-the-game-begin/'],
],
xkcd => [
	'xkcd',
	'http://xkcd.com/1/',
	[src => qr'/comics/'],
	next => [accesskey => 'n'],
],
LeastICouldDo => [
	'Least I Could Do',
	'http://leasticoulddo.com/comic/20030210',
	[id => 'comic'],
	next => [id => 'nav-next'],
],
Nichtlustig => [
	'Nichtlustig',
	'http://nichtlustig.de/toondb/000930.html',
	[src => qr'/comics/full/'],
	next => [src => qr'pfeil_rechts'],
],
Sinfest => [
	'Sinfest',
	'http://sinfest.net/archive_page.php?comicID=1',
	[src => qr'/comikaze/comics'],
],
LookingForGroup => [
	'Looking For Group',
	'http://www.lfgcomic.com/page/1',
	[id => 'comic'],
],
ForTheWicked => [
	'No Rest For The Wicked',
	'http://www.forthewicked.net/archive/01-01.html',
	[src => qr'pages/'],
],
MenageA3 => [
	'Ménage à 3',
	'http://www.menagea3.net/strips-ma3/room_for_two_more_(vol1)',
	[src => qr'/comics/'],
],
QuestionableContent => [
	'Questionable Content',
	'http://www.questionablecontent.net/view.php?comic=1',
	[id => 'strip'],
],
Flipside => [
	'Flipside',
	'http://www.flipsidecomics.com/comic.php?i=1',
	[src => qr'comic/'],
],
Flipside0 => [
	'Flipside 0',
	'http://www.flipsidecomics.com/comic/book0/fs01pg01.html',
	[src => qr'fs\d+'],
],
CaseyAndy => [
	'Casey & Andy',
	'http://galactanet.com/comic/view.php?strip=1',
	[src => qr'^Strip'],
],
PetProfessional => [
	'Pet Professional',
	'http://www.petprofessional.net/d/20050209.html',
	[src => qr'^/comics/pp\d{8}'],
],
UFO => [
	'u.f.o.',
	'http://mangowow.zamomo.com/index.php?strip_id=1',
	[src => qr'istrip_files/strips/\d{8}'],
],
Filibuster => [
	'Filibuster',
	'http://www.filibustercartoons.com/index.php/2001/06/28/poor-jean/',
	[id => 'comic'], [src => qr'/comics/\d{8}'],
],
ihooky => [
	'ihooky',
	'http://www.ihooky.com/comic.php?id=1',
	[class => 'comic'],[src => qr'comics/'],
],
IndustrialRevolutionary => [
	'Industrial Revolutionary',
	'http://irevolutionary.biz/index.php?p=3',
	[class => 'comicImage'],
],
Key => [
	'Key',
	'http://key.shadilyn.com/firstpage.html',
	[_tag => 'span', class => 'style4'],[src => qr'images/'],
],
EnchantedDreams => [
	'Enchanted Dreams',
	'http://www.enchanted-dreams.net/2008/01/26/volume1-cover-page/',
	[id => 'comic'],
],
Exiern => [
	'Exiern',
	'http://www.exiern.com/?p=7',
	[id => 'comic'],
],
CAD => [
	'CTRL+ALT+DEL',
	'http://www.cad-comic.com/cad/20021023',
	[id => 'content'], [src => qr'comics/'],
],
Kawaiinot => [
	'Kawaii Not',
	'http://www.kawaiinot.com/2005/06/18/the-cloud-and-the-fart/',
	[id => 'comic-1'],
],
Multiplex => [
	'Multiplex',
	'http://www.multiplexcomic.com/strip/1',
	[id => 'strip-code'],
],
CrookedGremlins => [
	'Crooked Gremlin',
	'http://www.crookedgremlins.com/04/01/2008/indiana-jones-and-the-spirit-crushing-sequel/',
	[id => 'comic'],
],
ShadowDragonExecutiveForce => [
	'Shadow Dragen Executive Force',
	'http://www.sdxf.net/d/20060926.html',
	[alt => 'comic'],
],
Rain => [
	'Rain',
	'http://www.whoisrain.com/?p=163',
	[id => 'comic'],
],
CourtingDisaster => [
	'Courting Disaster',
	'http://www.courting-disaster.com/archive/20050112.html',
	[src => qr'/comics/cd'],
],
DragonTails => [
	'Dragon Tails',
	'http://www.dragon-tails.com/comics/archive.php?date=990311',
	[src => qr'\d{6}'],
],
Tozo => [
	'The Public Servant Tozo',
	'http://tozocomic.com/2007/01/28/chapter-1/',
	[id => 'comic'],
],
Wayrift => [
	'Wayrift',
	'http://www.wayrift.com/introduction-pt-1-pg-1/',
	[id => 'comic'],
],
MyPrivateLittleHell => [
	'My Private Little Hell',
	'http://mutt.purrsia.com/mplh/?date=09/08/2004',
	[src => qr'comics/\d{8}'],
],
LittleDee => [
	'Little Dee',
	'http://www.littledee.net/?p=23',
	[id => 'comic-1'],
],
AppleGeeksLite => [
	'Apple Geeks Lite',
	'http://www.applegeeks.com/lite/index.php?aglitecomic=2006-04-19',
	[id => 'strip'],
],
Cealdian => [
	'Cealdian',
	'http://www.cealdiancomic.com/1/',
	[src => qr'istrip_files/strips'],
],
Sheldon => [
	'Sheldon',
	'http://www.sheldoncomics.com/archive/011130.html',
	[id => 'strip'],
],
ChickenWings => [
	'Chicken Wings',
	'http://www.chickenwingscomics.com/archives/archives.php?strip_id=1',
	[src => qr'content/strips/\d{8}'],
],
BookOfBiff => [
	'The Book of Biff',
	'http://www.thebookofbiff.com/2006/01/02/4/',
	[id => 'comic'],
],
FissionChicken => [
	'Fission Chicken',
	'http://www.fissionchicken.com/?webcomic_post=sock-suckers_2006-04-19',
	[class => qr'webcomic-object-full'],
],
Marooned => [
	'Marooned',
	'http://www.maroonedcomic.com/comic-strip/comic-for-3242008/',
	[id => 'comic-1'],
], 
ElsieHooper => [
	'Elsie Hooper',
	'http://www.elsiehooper.com/comics/comic000.shtml',
	[src => qr'^http://www.elsiehooper.com/images/|/comics/'],
],
MidnightMacabre => [
	'Midnight Macabre',
	'http://www.midnightmacabre.com/mm05231981.shtml',
	[src => qr'arch/mm\d{8}'],
],
SparePants => [
	'Spare Pants',
	'http://www.sparepartscomics.com/comics/index.php?date=20031022',
	[src => qr'comics/\d{8}'],
],
Undertow => [
	'Undertow',
	'http://undertow.dreamshards.org/0/u0_0.html',
	[src => qr'^up?\d+_'],
	next => [src => qr'saepoint'],
],
CyanideHappiness => [
	'Cyanide & Happiness',
	'http://www.explosm.net/comics/15/',
	[alt => 'Cyanide and Happiness, a daily webcomic'],
],
TodUndMaedchen1 => [
	'Der Tod und das Mädchen 1',
	'http://tod.cartoontomb.de/deutsch/k01/tod01_01.html',
	[src => qr'images/tod/teil1/'],
	next => [_tag => 'a', text_match('weiter') ],
],
DeathAndMaiden => [
	'Death and the Maiden 1',
	'http://deathandmaiden.com/2008/12/03/prologue-1/',
	[id => 'comic'],
],
DeathAndMaiden2 => [
	'Death and the Maiden 2',
	'http://two.deathandmaiden.com/2007/03/09/prologue-1/',
	[id => 'comic'],
],
DominicDeegan => [
	'Dominic Deegan, Oracle for Hire',
	'http://dominic-deegan.com/view.php?date=2002-05-21',
	[class => 'comic'],
],
ConScrew => [
	'ConScrew',
	'http://www.conscrew.com/index.php?strip_id=0',
	[src => qr'comics/\d{6}'],
],
DeadWinter => [
	'Dead Winter',
	'http://www.deadwinter.cc/page/001.htm',
	[id => 'comic'],
],
SilentKimbly => [
	'Silent Kimbly',
	'http://www.silentkimbly.com/2006/02/05/love-handles/',
	[id => 'comic'],
],
SamAndFuzzy => [
	'Sam & Fuzzy',
	'http://www.samandfuzzy.com/archive.php?comicID=1',
	[src => qr'comics/\d{8}'],
],
CyBoar => [
	'Cy-Boar',
	'http://cy-boar.com/index.php?date=2005-02-25',
	[src => qr'/images/\d{4}-\d\d-\d\d'],
],
BearVsZombie => [
	'Bear vs Zombies',
	'http://bearvszombies.com/?p=4',
	[id => 'comic'],
],
RobAndElliot => [
	'Rob & Elliot',
	'http://www.robandelliot.cycomics.com/archive.php?id=17',
	[id => 'contentcomic'],
],
OkashinaOkashi => [
	'Okashina Okashi, Strange Candy',
	'http://www.strangecandy.net/d/20010116.html',
	[id => 'comic'],
],
WastedTalent => [
	'Wasted Talent',
	'http://www.wastedtalent.ca/comic/anime-crack',
	[class => 'comic_content'],
],
ShadowGirls => [
	'Shadow Girls',
	'http://www.shadowgirlscomic.com/comics/book-1/chapter-1-broken-dreams/welcome/',
	[id => 'comic'],
],
OneQuestion => [
	'One Question',
	'http://www.onequestioncomic.com/comics/1/',
	[src => qr'istrip_files/strips/'],
],
AbominaleCharlesChristopher => [
	'The Abominable Charles Christopher',
	'http://www.abominable.cc/2007/06/20/episode-1/',
	[id => 'comic'],
],
VGCats => [
	'VG Cats',
	'http://www.vgcats.com/comics/?strip_id=0',
	[src => qr'images/\d{6}'],
],
WayfarersMoon => [
	'Wayfarer’s Moon',
	'http://www.wayfarersmoon.com/index.php?page=0',
	[class => 'comic'],
	next => [alt => 'forward button'],
],
HellHotel => [
	'Hell Hotel',
	'http://www.hell-hotel.com/2008/10/08/introduction/',
	[src => qr'comics/\d{4}/\d\d/'],
],
ExploitationNow => [
	'Exploitation Now',
	'http://www.exploitationnow.com/2000-07-07/9',
	[id => 'comic-1'],
],
Melonpool => [
	'The Adventures of Mayberry Melonpool',
	'http://www.melonpool.com/?p=41',
	[id => 'comic'],
],
ParadoxLost => [
	'Paradox Lost',
	'http://paradox-lost.com/?strip_id=0',
	[src => qr'comics/'],
],
NewWorld => [
	'New World',
	'http://www.tfsnewworld.com/2003/01/30/115/',
	[id => 'comic'],
],
SpiderWebs => [
	'Spider Webs',
	'http://www.tfsnewworld.com/2007/09/03/31/',
	[id => 'comic'],
],
ChainsawSuit => [
	'chainsawsuit',
	'http://chainsawsuit.com/2008/03/12/strip-338/',
	[id => 'comic'],
],
MathsForLaughs => [
	'Maths for Laughs',
	'http://mathsforlaughs.co.uk/?c=1',
	[id => 'comic'],
],
AlphaLuna => [
	'Alpha Luna',
	'http://www.alphaluna.net/issue-1/cover/',
	[id => 'comic'],
],
TaoOfGeek => [
	'The Tao of Geek',
	'http://www.taoofgeek.com/archive.php?date=20020610',
	[class => 'comicborder'],
],
CheckerboardNightmare => [
	'Checkerboard Nightmare',
	'http://www.checkerboardnightmare.com/retro/20001110.shtml',
	[src => qr'retrocomics/\d{8}'],
	next => [src => "/images/nav_03.gif"],
],
TheSystem => [
	'The System',
	'http://www.notquitewrong.com/rosscottinc/2008/07/01/the-system-1/',
	[id => 'comic'],
],
SexyLosers => [
	'Sexy Losers',
	'http://sexylosers.com/001.html',
	[src => qr'comics/'],
	next => [_tag => 'tr',text_match(qr'^\|<...<<All SL Strips>>...>\|$')],[_tag => 'font', text_match('>>')],
],
BlankIt => [
	'Blank It',
	'http://blankitcomics.com/2008/06/08/blankit-0001/',
	[id => 'comic'],
],
Cortland => [
	'Cortland',
	'http://www.cortlandcomic.com/index.php?pageID=1',
	[src => qr'/pages/'],
],
PCWeenies => [
	'PC Weenies',
	'http://pcweenies.com/2008/01/02/a-new-beginning/',
	[id => 'comic-1'],
],
Fafnir => [
	'Fafnir the Dragon',
	'http://fafnirthedragon.com/?p=42',
	[id => 'comic'],[src => qr'/comics/|^$'],
	next => [class => 'next'],[_tag => 'a'],
],
SerenianCentury => [
	'Serenian Century',
	'http://www.holidayblue.com/manga/pages/chpt1/pg1.php',
	[id => qr'^Serenian_Century'],
],
Foxtails => [
	'Foxtails',
	'http://foxtails.magickitsune.com/strips/20090906.html',
	[src => qr'img/\d{8}'],
],
Ginpu => [
	'Ginpu',
	'http://www.ginpu.us/2006/09/06/filler-2/',
	[id => 'comic'],
],
PebbleVersion => [
	'Pebble Version',
	'http://www.pebbleversion.com/Archives/Strip1.html',
	[src => qr'ComicStrips'],
],
Worldbreak => [
	'World Break',
	'http://worldbreak.ashen-ray.com/comic/pages/p001.html',
	[src => qr'p\d+'],
	next => [src => "forward.jpg"],
],
NoNeedForBushido => [
	'No Need For Bushido',
	'http://noneedforbushido.com/2002/comic/1/',
	[class => qr'^comic-item'],
],
NoNeedForBushidoRemix => [
	'No Need For Bushido Remix',
	'http://noneedforbushido.com/2009/remix/remix-01/',
	[class => qr'^comic-item'],
],
OddFish => [
	'Odd Fish',
	'http://www.odd-fish.net/viewing.php?&comic_id=1',
	[alt => 'Odd-Fish webcomic'],
],
MakeshiftMiracle => [
	'The Makeshift Miracle',
	'http://www.makeshiftmiracle.com/?p=75',
	[id => 'comic'],
],
LastResort => [
	'Last Resort',
	'http://www.lastres0rt.com/2007/04/that-sound-you-hear-is-a-shattered-stereotype/',
	[id => 'comic'],
],
Prydwen => [
	'Prydwen',
	'http://www.prydwen.paperfangs.com/?p=38',
	[id => 'comic-1'],
],
Supafine => [
	'Supafine',
	'http://www.supafine.com/comics/classic.php?comicID=1',
	[src => qr'comics/\d{8}'],
],
Legacy => [
	'Legacy',
	'http://www.legacycomic.com/index.php?strip_id=1',
	[src => qr'istrip_files/strips/\d{8}'],
],
OkCancel => [
	'Ok/Cancel',
	'http://www.ok-cancel.com/comic/1.html',
	[id => 'comic'],
	next => [_tag => 'div',class => 'next'],[_tag => 'a'],
],
AmazingSuperPowers => [
	'Amazing Super Powers',
	'http://www.amazingsuperpowers.com/2007/09/heredity/',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
],
LittleGamers => [
	'Little Gamers',
	'http://www.little-gamers.com/2000/12/01/99/',
	[id => 'comic-middle'],
],
Dracula => [
	'Dracula',
	'http://www.draculacomic.net/comic.php?comicID=0',
	[class => 'comicimage'],
],
BiteMe => [
	'Bite Me',
	'http://www.bitemecomic.com/?p=582',
	[id => 'comic'],
],
BiggerCheese => [
	'Bigger Cheese',
	'http://www.biggercheese.com/index.php?comic=1',
	[src => qr'comics/\d{4}'],
],
GoodShipChronicles => [
	'Good Ship Chronicles',
	'http://goodshipchronicles.com/comic.php?comicID=1',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
	next => [alt => 'next'],
],
FChords => [
	'F Chords',
	'http://www.fchords.com/20080729.shtml',
	[id => 'comic'],
],
DarkAndPink => [
	'Dark and Pink',
	'http://www.darkandpink.com/?comic=20080317',
	[class => 'comic'],
],
Sins => [
	'Sins',
	'http://www.sincomics.com/index.php?1',
	[src => qr'comic/'],
	next => [src => "comic/../../pageimages/foward.jpg"],
],
CowbirdsInLove => [
	'Cowbirds in Love',
	'http://cowbirdsinlove.com/1',
	[id => 'comic'],
	next => [_tag => 'a',text_match(qr'^fresher') ],
],
WiglafAndMordred => [
	'Wiglaf & Mordred',
	'http://liliy.net/wam/2005/10/04/whos-the-other-guy/',
	[class => qr'webcomic-object-full'],
],
ComicCritics => [
	'Comic Critics',
	'http://comiccritics.com/2008/06/18/meet-josh/',
	[id => 'comic'],
],
BeyondTheTree => [
	'Beyond the Tree',
	'http://beyondthetree.wordpress.com/2008/03/20/all-adventures-start-somewhere/',
	[src => qr'/btt-\d\d\d'],
	next => [src => "http://beyondthetree.files.wordpress.com/2008/05/nav-na.png?w=655"],
],
AntiHeroes => [
	'Anti Heroes',
	'http://antiheroescomic.com/comic/1',
	[id => 'comic_image'],
],
WallyAndOsborne => [
	'Wally and Osborne',
	'http://wallyandosborne.com/2005/06/27/welcome-to-antarctica/',
	[id => 'comic-1'],
],
Overcompensating => [
	'Overcompensating',
	'http://www.overcompensating.com/posts/20040929.html',
	[src => qr'/comics/'],
],
PressStartToPlay => [
	'Press Start To Play',
	'http://www.pressstarttoplay.net/comic/?cid=1',
	[src => qr'comics/\d{6}'],
],
FeyWinds => [
	'Fey Winds',
	'http://kitsune.rydia.net/comic/page.php?id=0',
	[src => qr'comic/pages'],
],
ExtraLife => [
	'Extra Life',
	'http://www.myextralife.com/comic/06172001/',
	[class => 'comic-content'],
],
TooMuchInformation => [
	'Too Much Information',
	'http://tmi-comic.com/2005/05/05/out-of-the-nest/',
	[id => 'comic-1'],
],
SilenceInTheDarkness => [
	'Silence in the Darkness on Q16',
	'http://silenceinthedarknessonq16.comicostrich.com/comic.php?cdate=20070401',
	[src => qr'comics/'],
],
NateVS => [
	'Nate VS',
	'http://www.failedcomics.com/natevs.php?comicID=1',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
],
FallenAngelsUsedBooks => [
	'Fallen Angels Used Books',
	'http://www.faubcomic.com/d/20030602.html',
	[src => qr'comics/\d{8}'],
],
BoyOnStickAndSlither => [
	'Boy on a Stick and Slither',
	'http://www.boasas.com/?c=1',
	[src => qr'boasas/'],
	next => [src => "images/right.20.png"],
],
Erfworld => [
	'Erfworld',
	'http://www.erfworld.com/book-1-archive/?px=%2F001.jpg',
	[src => qr'uploads/book\d'],
],
EvilInc => [
	'Evil inc.',
	'http://www.evil-comic.com/archive/20050530.html',
	[id => 'ei_strip'],
],
Enoch => [
	'Enoch',
	'http://www.enochcomic.com/?p=45',
	[id => 'comic'],
],
CatAndGirl => [
	'Cat and Girl',
	'http://catandgirl.com/?p=1602',
	[id => 'comic'],
],
Bardsworth => [
	'Bardsworth',
	'http://www.bardsworth.com/archive.php?p=1',
	[id => 'cg_img'],
],
SpikyHairedDragon => [
	'Spiky-Haired Dragon, Worthless Knight',
	'http://www.shd-wk.com/index.php?strip_id=0',
	[alt => 'Current strip'],
],
QuittingTime => [
	'Quitting Time',
	'http://quitting-time.com/2006/02/13/quitting-time-02-12-2006/',
	[id => 'comic'],
],
UglyHill => [
	'Ugly Hill',
	'http://www.uglyhill.com/d/20050523.html',
	[src => qr'/comics/\d{8}'],
	next => [ src => "/images/ahead_button.gif" ],
],
NinjaDeathStarMan => [
	'Ninja Death Star Man',
	'http://www.supafine.com/comics/ndsm.php?comicID=1',
	[src => qr'comics/ndsm/\d{8}'],
],
AwkwardZombie => [
	'Awkward Zombie',
	'http://www.awkwardzombie.com/index.php?page=0&comic=092006',
	[id => 'comic'],
],
Garanos => [
	'Garanos',
	'http://www.garanos.com/pages/page-1/',
	[id => 'comic'],
],
Taiki => [
	'Taiki',
	'http://www.taikiwebcomic.com/Chapter1/Ch1.html',
	[_tag=>'img', height => 800, width => 600],
	next => [_tag => 'a', href => qr'/Chapter\d+/Ch\d+'], [_tag => 'img', src => 'http://www.taikiwebcomic.com/Button_Next.jpg'],
],
WireHeads => [
	'Wire Heads',
	'http://www.wire-heads.com/istrip/index.php?strip_id=1',
	[src => qr'istrip_files/strips/\d{8}'],
],
DungeonsDenizens => [
	'Dungeons & Denizens',
	'http://dungeond.com/2005/08/23/08232005/',
	[id => 'comic-1'],
],
ThePeons => [
	'The Peons',
	'http://shanime.com/?m=20070101',
	[src => qr'peons\d+'],
	next => [_tag => 'a', text_match(qr'Peons #002 >>|Next') ],
],
TakingTheBypass => [
	'Taking the Bypass',
	'http://www.cartoonme.net/index.php?date=20030401',
	[src => qr'/comic/\d{8}'],
],
GenesJournal => [
	'Gene’s Journal',
	'http://www.genesjournalcomic.com/2008/01/11/pilot-strip-my-journal/',
	[id => 'comic'],
],
SequentialArt => [
	'Sequential Art',
	'http://www.collectedcurios.com/sequentialart.php?s=1',
	[id => 'strip'],
	next => [ src => "Nav_ForwardOne.gif" ],
],
ZhedReckoning => [
	'Zhed Reckoning',
	'http://www.zombiegrotto.com/1/',
	[id => 'comic'],
],
Starslip => [
	'Starslip',
	'http://starslip.com/2005/05/23/starslip-number-1/',
	[src => qr'comics/ssc\d{8}'],
],
ElGoonishShive => [
	'El Goonish Shive',
	'http://www.egscomics.com/?date=2002-01-21',
	[class => 'comic2'],
],
GUComics => [
	'GU Comics',
	'http://www.gucomics.com/comic/?cdate=20000710',
	[src => qr'/gu_\d{8}'],
	next => [alt => 'Next Comic'],
],
TerrorIsland => [
	'Terror Island',
	'http://www.terrorisland.net/strips/001.html',
	[src => qr'images/strips/'],
],
EpicFail => [
	'Epic Fail',
	'http://epicfail.xepher.net/2009/01/13/my-kindom-for-some-phat-loot/',
	[id => 'comic-1'],
],
Terra => [
	'Terra',
	'http://www.terra-comic.com/wordpress/?p=23',
	[id => 'comic'],
],
WintersInLavelle => [
	'Winters in Lavelle',
	'http://wintersinlavelle.com/?p=13',
	[id => 'comic']
],
EvilDiva => [
	'Evil Diva',
	'http://www.evildivacomics.com/?p=145',
	[id => 'comic'],
],
OtakuNoYen => [
	'Otaku no Yen',
	'http://www.otakunoyen.com/ony/view.php?date=2005-03-01',
	[src => qr'^comics/'],
],
ErrantStory => [
	'Errant Story',
	'http://www.errantstory.com/2002-11-04/15',
	[id => 'comic'],
	next => [text_match('Next>')],
],
HomeOnTheStrange => [
	'Home on the Strange',
	'http://www.homeonthestrange.com/view.php?ID=1',
	[src => qr'comics/'],
],
SpikysWorld => [
	'Spiky’s World',
	'http://www.shd-wk.com/sw/index.php?strip_id=1',
	[class => 'comic'],
	next => [_tag => 'a', text_match('>') ],
],
StandardDeviation => [
	'Standard Deviation',
	'http://coderedcomics.com/college/?p=6',
	[id => 'comic'],
],
AbbysAgency => [
	'Abby’s Agency',
	'http://abbysagency.us/blog/2004/11/01/a/',
	[id => 'comic-1'],
],
SuperStupor => [
	'Super Stupor',
	'http://www.superstupor.com/sust11262007.shtml',
	[src => qr'sust\d{8}'],
],
TheNoob => [
	'The Noob',
	'http://www.thenoobcomic.com/index.php?pos=1',
	[id => 'main_content_comic'],
],
ValkyrieYuuki => [
	'Sparkling Generation Valkyrie Yuuki',
	'http://www.sgvy.com/archives/Edda1/Cover.html',
	[id => 'comic'],
],
TwoPStart => [
	'2P Start!',
	'http://www.2pstart.com/2007/02/14/traitor/',
	[id => 'comic'],
],
NorthWorld => [
	'North World',
	'http://www.north-world.com/kc/archivepage1.php?page=1',
	[src => qr'/kc\d\d'],
],
PicturesForSadChildren => [
	'Pictures for sad Children',
	'http://www.picturesforsadchildren.com/index.php?comicID=1',
	[src => qr'comics/\d{8}'],
],
WhatBirdsKnow => [
	'What Birds Know',
	#'http://whatbirdsknow.atspace.com/wbk01.htm',
	'http://fribergthorelli.com/wbk/index.php/page-1/',
	[id => 'comic'],
],
FragileGravity => [
	'Fragile Gravity',
	'http://unseenllc.com/core.php?archive=20021109',
	[src => qr'strips/\d{8}'],
],
PeterIsTheWolf => [
	'Peter is the Wolf',
	'http://www.peteristhewolf.com/adult/001.html',
	[src => qr'comics/pitw_'],
],
PrideOfLife => [
	'Pride of Life',
	'http://prideoflife.com/?p=316',
	[id => 'comic'],
],
DarthsAndDroids => [
	'Darths & Droids',
	'http://www.darthsanddroids.net/episodes/0001.html',
	[src => qr'/comics/darths'],
],
GreenAvenger => [
	'Green Avenger',
	'http://www.green-avenger.com/d/20040924.html',
	[src => qr'comics/\d{8}'],
],
BobAndGeorgie => [
	'Bob and Georgie',
	'http://www.bobandgeorge.com/archives/000401',
	[src => qr'comics/\d{4}/\d{6}'],
],
RivalAngels => [
	'Rival Angels',
	'http://www.rivalangels.com/2007/12/30/beginning/',
	[id => 'comic-1'],
],
BladeKitten => [
	'Blade Kitten',
	'http://www.bladekitten.com/comics/blade-kitten/1/page:1',
	[class => 'comic_page_image'],
],
HijinksEnsue => [
	'Hijinks Ensue',
	'http://hijinksensue.com/2007/05/11/a-soul-as-black-as-eyeliner/',
	[id => 'comic-1'],
],
SomethingPositive => [
	'Something Positive',
	'http://www.somethingpositive.net/sp12192001.shtml',
	[src => qr'sp\d{8}'],
],
GeeksWorld => [
	'Geek’s World',
	'http://www.geeksworld.org/strip_1.html',
	[id => 'dastrip'],
],
Wonderella => [
	'Wonderella',
	'http://nonadventures.com/2006/09/09/the-torment-of-a-thousand-yesterdays/',
	[id => 'comic'],
],
DigitalUnrest => [
	'Digital Unrest',
	'http://digitalunrestcomic.com/index.php?date=2005-12-08',
	[src => qr'strips/\d{4}'],
],
SamuraiPride => [
	'Samurai Pride',
	'http://scarymutt.com/sampridecomic/archive_page.php?comicID=1',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
],
WhiteNinja => [
	'White Ninja',
	'http://www.whiteninjacomics.com/comics/severed.shtml',
	[src => qr'/images/comics/.[^\-]'],
],
TalesOfPyleaCrux => [
	'Tales of Pylea Crux',
	'http://crux.talesofpylea.com/1/',
	[src => qr'istrip_files/strips/'],
],
CafFiends => [
	'Caf-Fiends',
	'http://www.caf-fiends.net/Comics/2006/Oct06/20061023.htm',
	[src => qr'\d{8}'],
],
RhymesWithWitch => [
	'Rhymes with Witch',
	'http://www.rhymes-with-witch.com/rww03292004.shtml',
	[src => qr'images/rww\d+'],
	next => [href => qr'rww\d{6,8}.shtml', text_match(qr'Next') ],
],
Subnormality => [
	'Subnormality',
	'http://www.viruscomix.com/page324.html',
	[_tag => 'img', sub { $_[0]->attr('src') !~ qr'^sub' }],
],
NekoTheKitty => [
	'Neko the Kitty',
	'http://www.nekothekitty.net/cusp/daily.php?date=020807',
	[src => qr'cusp/comics/'],
],
Girly => [
	'Girly',
	'http://girlyyy.com/go/1',
	[class => 'comicpage'],
],
Concession => [
	'Concession',
	'http://concessioncomic.com/index.php?pid=20060701',
	[id => 'comic'],
],
JohnnySaturn => [
	'Johnny Saturn',
	'http://johnnysaturn.com/2006/01/18/book-one-page-01/',
	[id => 'comic-1'],
],
OtenbaFiles => [
	'Otenba Files',
	'http://www.otenba-files.com/2009/01/01/chapter-1-cover/',
	[id => 'comic-1'],
],
CuChulainn => [
	'Cú Chulainn',
	'http://www.oghme.com/origins/cuchulainn/post/p.000',
	[class => 'comicpage'],[src => qr'/origins/comics/cuchulainn/'],
],
TechnicalExplanationOfScience => [
	'Technical Explanation of Science',
	'http://www.fusun.ch/tes/index.php?showimage=1',
	[id => 'photo'],
],
PlayerVsPlayer => [
	'Player Vs Player',
	'http://www.pvponline.com/1998/05/04/mon-may-04/',
	[id => 'comic'],
],
Bible => [
	'Basic Instructions Before Leaving Earth',
	'http://www.biblecomic.net/comics/the-creation/the-creation-page-1/',
	[id => 'comic'],
],
FauxPas => [
	'Faux Pas',
	'http://www.ozfoxes.net/cgi/pl-fp1.cgi?1',
	[src => qr'/fp/fp-'],
],
Dreamland => [
	'The Dreamland Chronicles',
	'http://www.thedreamlandchronicles.com/2006/01/05/page-1/',
	[id => 'comic-1'],
],
JesusAndMo => [
	'Jesus and Mo',
	'http://www.jesusandmo.net/2005/11/24/body/',
	[id => 'comic'],
],
JuathuurTwo => [
	'Juathuur 2: Gatecrash',
	'http://www.gatecrash.juathuur.com/?id=1',
	[id => 'comicimg'],
],
GoodCheese => [
	'Good Cheese',
	'http://www.goodcheese.com/index.php?date=2006-12-12',
	[src => qr'pages/\d{4}-\d\d-\d\d'],
],
Aramore => [
	'Aramore',
	'http://www.tomturton.com/aramore/comic.php?chapter=1&page=t',
	[src => qr'comic/'],
],
GoodLittleRobot => [
	'The Good Little Robot',
	'http://www.thegoodlittlerobot.com/2003/09/im_only_one_man.php',
	[id => 'comic'],[src => qr'/images/comics/\d{4}/\d\d/\d\d_\d\d_\d\d\.'],
],
DoctorInsano => [
	'Doctor Insano',
	'http://www2.doctorinsano.com/di/2006/11/family-business-1-spring-water/',
	[id => 'comic-1'],
],
DMFAAbel => [
	'Dan and Mab’s Furry Adventures: Abel’s Story',
	'http://www.missmab.com/Comics/Abel_01.php',
	[src => qr'Abel'],
],
DandyAndCompany => [
	'Dandy & Company',
	'http://www.dandyandcompany.com/2001/10/11/october-11-2001/',
	[id => 'strip_bg'],
],
#spiderforest starts here
AppleValley => [
	'Apple Valley',
	'http://www.applevalleycomic.com/2008/dungeons-and-dragons/',
	[class => 'image-section'], [src => qr'uploads/\d{4}-\d\d-\d\d'],
	next => [text_match('Next Comic')],
],
Avernyght => [
	'The Chronicles of Avernyght',
	'http://avernyght.com/?comic_id=0',
	[src => qr'comics/\d{6}'],
],
BetweenPlaces => [
	'Between Places',
	'http://betweenplaces.spiderforest.com/?comic=20080617-chapter-one---title-page',
	[src => qr'comics/\d{8}'],
],
CatLegend => [
	'Cat Legend',
	'http://www.cat-legend.com/?comic=20030827-1.-the-first',
	[src => qr'comics/\d{8}'],
],
Catalyst => [
	'Catalyst',
	'http://catalyst.spiderforest.com/comic.php?comic_id=0',
	[src => qr'comics/\d{6}'],
],
Cetiya => [
	'Cetiya',
	'http://cetiya.spiderforest.com/?comic=20080601',
	[src => qr'comics/\d{8}'],
],
Chirault => [
	'Chirault',
	'http://chirault.sevensmith.net/pages/00_01.html',
	[src => qr'images/\d\d_\d\d'],
],
Chromacorps => [
	'Kyoudaikido Soldier Chromacorps',
	'http://www.takacomics.com/2010/05/future-work-kyoudaikido-soldier-chromacorps/',
	[id => 'comic'],
],
CodeNameHunter => [
	'Code Name: Hunter',
	'http://www.codenamehunter.com/archive/2005/06/20',
	[class => 'comicImage'],[src => qr'/comics/'],
],
Cooties => [
	'Cooties',
	'http://cooties.spiderforest.com/2006/06/23/06232006/',
	[id => 'comic'],
],
DreamScar => [
	'Dream*Scar',
	'http://dream-scar.net/view.php?id=1',
	[id => 'contents'],
],
Fuzznuts => [
	'Fuzznuts',
	'http://fuzznuts.spiderspawn.com/?p=10',
	[id => 'comic'],
],
Gemutations => [
	'Gemutations: Plague',
	'http://gemutations.spiderforest.com/comic.php?comic=20100924-gemutations-plague',
	[src => qr'comics/\d{8}'],
],
Keys => [
	'Keys',
	'http://keys.spiderforest.com/?comic=20100903',
	[src => qr'comics/\d{8}'],
],
Kinnari => [
	'Kinnari',
	'http://www.kinnaricomic.com/?id=1',
	[id => 'comicimg'],
],
LifesAWitch => [
	'Life’s a Witch',
	'http://www.witchytech.com/lifesawitch/2006/08/07/2006-08-07/',
	[id => 'comic'],
],
Malaak => [
	'Malaak',
	'http://malaakonline.com/I1.html',
	[src => qr'I+\d+', width => 800],
],
MysteriesArcana => [
	'Mysteries of the Arcana',
	'http://mysteriesofthearcana.com/index.php?action=comics&cid=1',
	[id => 'comic'],
],
Nahast => [
	'Nahast Lands of Strife',
	'http://nahast.spiderforest.com/archive.php?comic_id=0',
	[src => qr'comics/\d{6}'],
],
Omega => [
	'Omega',
	'http://omega.indanthrone.com/?p=301',
	[class => 'attachment-full wp-post-image'],
	next => [rel => 'next'],
],
Eldlor => [
	'Planes of Eldlor',
	'http://www.eldlor.com/?page=comic&id=104263',
	[alt => 'Comic'],
],
Precocious => [
	'Precocious',
	'http://precociouscomic.com/comic.php?page=1',
	[src => qr'archive/strips/'],
],
RealLifeFiction => [
	'Real Life Fiction',
	'http://rlfcomic.com/?comic=20080824-cup-a-soup',
	[src => qr'comics/\d{8}'],
],
Requiem => [
	'Requiem',
	'http://requiem.spiderforest.com/?p=4',
	[id => 'comic-1'],
],
RivenSol => [
	'Riven Sol',
	'http://www.rivensol.com/?p=92',
	[id => 'comic'],
],
RuneMaster => [
	'Rune Master',
	'http://rmtoads.com/rm/comic.php?page=1',
	[src => qr'/pages/'],
],
SakanaNoSadness => [
	'Sakana no Sadness',
	'http://www.sakanacomic.com/page/ch1',
	[id => 'comic-inner'],
],
SchoolSpirit => [
	'School Spirit',
	'http://www.schoolspiritcomic.com/2004/06/12/caspersfirstday/',
	[id => 'comic-1'],
],
#http://www.smyzerandblyde.com/ multipage
SpecialSchool => [
	'Special School',
	'http://specialschool.spiderforest.com/?comic=20050319-and-so-it-begins',
	[id => 'comic-inner'],
],
SunsetGrill => [
	'Sunset Grill',
	'http://sunsetgrillcomic.com/index.php?comic=20080804-august-4,-2008',
	[src => qr'comics/\d{8}'],
],
TalesFromMiddleKingdom => [
	'Tales from the Middle Kingdom',
	'http://middlekingdomtales.com/2010/04/01/p1-beginning/',
	[id => 'comic-1'],
],
TalesTravelingGnome => [
	'Tales of the Traveling Gnome',
	'http://ttg.spiderforest.com/?page=ch01/1-000.html',
	[src => qr'\.\./ch\d\d'],
	url_hack => sub { $_[0] =~ s'\.\./''; $_[0] }
],
ThinkBeforeYouThink => [
	'Think before you Think',
	'http://thinkbeforeyouthink.net/?comic=20090613-coffee',
	[src => qr'comics/\d{8}'],
],
TwilightLady => [
	'Twilight Lady',
	'http://www.twilightlady.com/2008/03/16/the-secret-of-cass-corridor-2/',
	[id => 'comic'],
],
WarOfWinds => [
	'The War fo Winds',
	'http://warofwinds.com/comic.php?comic_id=0',
	[id => 'comic'],
],
NotAlone => [
	'Not Alone',
	'http://warofwinds.com/not-alone/?comic_id=0',
	[src => qr'comics/\d{6}'],
],
#hiatus before release
#FromEarthToHeaven => [
#	'From Earth to Heaven',
#	'http://warofwinds.com/from-earth-to-heaven/archive.php?comic_id=0',
#	[id => 'comic'],
#],
Weave => [
	'Weave',
	'http://weave.spiderforest.com/2009/08/10/ch01page001/',
	[id => qr'^comic-'],[class => qr'comic-item'],
	next => [class => 'next-comic-link'],
],
WhatNonsense => [
	'What Nonsense',
	'http://whatnonsensecomic.com/?comic=20080208',
	[src => qr'comics/\d{8}'],
],
WillowsGrove => [
	'Willow’s Grove',
	'http://www.willowsgrove.com/wordpress/?p=17',
	[id => 'comic'],
],
Xander => [
	'Xander',
	'http://xandercomic.com/comic/chapter1/01-%e2%80%93-waking-up-on-the-right-side-of-bed/',
	[id => 'comic-1'],
],
XyliaTales => [
	'Xylia Tales',
	'http://www.xyliatales.com/10032007/',
	[src => qr'comics/\d{4}-\d\d-\d\d'],
],
ArtisteManquee => [
	'The Artiste Manquée',
	'http://artiste.harchun.com/comic/1/beginning',
	[src => qr'img/com/'],
],
Lint => [
	'Lint',
	'http://www.purnicellin.com/lint/2004/01/10/01102004/',
	[id => 'comic'],
],
Valiant => [
	'Valiant',
	'http://valiant.spiderforest.com/?comic_id=0',
	[src => qr'comics/\d{8}'],
],
GodsPack => [
	'The Gods’ Pack',
	'http://godspack.com/archives/index.php?strip_id=1',
	[src => qr'strips/\d{8}'],
],
CatLegendLegends => [
	'Legends, the expanded Cat Legend adventures',
	'http://legends.spiderforest.com/?comic_id=0',
	[src => qr'comics/\d{6}'],
],
ScatteredLeaves => [
	'Scattered Leaves',
	'http://scatteredleaves.spiderforest.com/?comic=20090809',
	[src => qr'comics/\d{8}'],
],
BlueSkunk => [
	'Blue Skunk',
	'http://blueskunk.spiderforest.com/?comic_id=0',
	[src => qr'comics/\d{6}'],
],
#spiderforest end
Lancaster => [
	'Lancaster the Ghost Detective',
	'http://lancaster-comic.com/archive/2010/01/26',
	[src => qr'/comics/'],
],
Bugcomic => [
	'Bugcomic',
	'http://www.bugcomic.com/comics/letter/',
	[id => 'comic-1'],
],
Spinnerette => [
	'Spinnerette',
	'http://www.krakowstudios.com/spinnerette/2010/02/09/20100209/',
	[id => 'comic-1'],
],
EdgeOfDecember => [
	'Edge of December',
	'http://www.edgeofdecember.com/index.php?pid=1',
	[id => 'comic'],
],
EverNight => [
	'EverNight',
	'http://www.evernightcomic.com/archives/1',
	[id => 'comic-1'],
],
NotAVillain => [
	'Not A Villain',
	'http://navcomic.com/archive/v1-001/',
	[src => qr'/wp-content/webcomic/'],
],
BittersweetCandyBowl => [
	'Bittersweet Candy Bowl',
	'http://www.bittersweetcandybowl.com/c1/p1.html',
	[class => 'comicpage'],
],
Unsounded => [
	'Unsounded',
	'http://www.casualvillain.com/Unsounded/comic/ch01/ch01_01.html',
	[id => 'comic'],
	next => [class => 'forward'],
],
Derelict => [
	'Derelict',
	'http://derelictcomic.com/?strip_id=0',
	[src => qr'comics'],
],
SinsVenial => [
	'Sins Venials',
	'http://sincomics.com/index.php?1',
	[src => qr'comic/'],
	next => [src => qr'foward.jpg'],
],
FullTimeInk => [
	'Full Time Ink',
	'http://fulltimeink.com/archives/129',
	[src => qr'comic_object'],
],
Evon => [
	'Evon',
	'http://evoncomics.com/?p=58',
	[id => 'comic'],
],
NikkiSprite => [
	'Nikki Sprite',
	'http://nikkisprite.com/?p=7',
	[id => 'comic'],
],
Underling => [
	'Underling',
	'http://underlingcomic.com/page-one/',
	[id => 'comic'],
],
TurboDefiantKimecan => [
	'Turbo Defiant Kimecan',
	'http://www.kimecan.com/eng/archive/chapter-1-cover/',
	[src => qr'/wp-content/webcomic/'],
],
RankorChronicles => [
	'Rankor Chronicles',
	'http://rankorchronicles.weebly.com/page-1.html',
	[id => 'comic'],
],
TheMaor => [
	'The Maor',
	'http://www.themaor.com/2008/07/05/themaor-chapter-one-cover/',
	[id => 'comic'],
],
Tamuran => [
	'Tamuran',
	'http://www.tamurancomic.com/?p=120',
	[id => 'comic'],
],
SuperNormalStep => [
	'Super Normal Step',
	'http://www.supernormalstep.com/1/',
	[src => qr'strips'],
],
Otherworld => [
	'Otherworld',
	'http://www.otherworldcomic.com/page3/files/23393629afd763c95a23282d39ed70bd-1.html',
	[id => qr'unique-entry-id'],
	next => [class => 'right'],
],
BlueMilkSpecial => [
	'Blue Milk Special',
	'http://www.bluemilkspecial.com/?p=4',
	[id => 'comic'],
],
Memoria => [
	'Memoria',
	'http://memoria.valice.net/?p=77',
	[id => 'comic'],
],
ThistilMistilKistil => [
	'Thistil Mistil Kistil',
	'http://tmkcomic.depleti.com/comic/ch01-pg00/',
	[id => 'comic'],
],
RomanticallyApocalyptic => [
	'Romantically Apocalyptic',
	'http://romanticallyapocalyptic.com/1',
	[src => qr'/art/'],
],
MilkForDeadHamsters => [
	'Milk For Dead Hamsters',
	'http://milkfordeadhamsters.com/comics/pet-fish',
	[src => qr'/wp-content/uploads/'],
],
Archipelago => [
	'Archipelago',
	'http://archipelagocomic.com/comic/?id=1',
	[src => qr'/comic/img/comic/'],
],
ZombieRanch => [
	'Zombie Ranch',
	'http://www.zombieranchcomic.com/2009/09/25/onthezombieranch/',
	[id => 'comic'],
],
Everblue => [
	'Everblue',
	'http://everblue-comic.com/2010/01/01/volume-1-cover/',
	[id => 'comic'],
],
Aeria => [
	'Aeria',
	'http://www.aeria-comic.com/2010/04/20/the-tale-of-aeria/',
	[id => 'comic'],
	next => [id => 'next'],
],
HundredPercentCat => [
	'100% Cat',
	'http://www.100percentcat.com/comics/index.php?date=20110406',
	[src => qr'/comics/']
],
DoesNotPlayWellWithOthers => [
	'Does Not Play Well With Others',
	'http://www.doesnotplaywellwithothers.com/comics/pwc-000f',
	[id => 'comic'],
],
Aikonia => [
	'Aikonia',
	'http://www.aikoniacomic.com/index.php?id=1',
	[id => 'comic'],
],
DraculaMysteryClub => [
	'Dracula Mystery Club',
	'http://draculamysteryclub.com/archive/dracula-mystery-club-cover',
	[id => 'comicbox'],
],
Sorcery101 => [
	'Sorcery 101',
	'http://www.sorcery101.net/sorcery101/s101chapter1/',
	[class => 'comic'],
],
Bisclavret => [
	'Bisclavret',
	'http://www.sorcery101.net/bisclavret/biscover/',
	[class => 'comic'],
],
AsWeWere => [
	'As We Were',
	'http://www.sorcery101.net/aswewere/awwcover/',
	[class => 'comic'],
],
FromScratch => [
	'From Scratch',
	'http://www.sorcery101.net/fromscratch/fscover/',
	[class => 'comic'],
],
StrangeSomeone => [
	'Strange Someone',
	'http://www.sorcery101.net/strangesomeone/cover/',
	[class => 'comic'],
],
MonsterPulse => [
	'Monster Pulse',
	'http://www.monster-pulse.com/?webcomic_post=mppage1',
	[src => qr'/wp-content/webcomic/'],
],
LoveMeNice => [
	'Love Me Nice',
	'http://www.lovemenicecomic.com/archive/every-monkey-has-his-day/',
	[src => qr'/wp-content/webcomic/'],
],
Locus => [
	'Locus',
	'http://www.locuscomics.com/wordpress/?p=58',
	[id => 'comic'],
],
AmazingAgentJennifer => [
	'Amazing Agent Jennifer',
	'http://www.amazingagentjennifer.com/strips-aaj/a_beautiful_campus',
	group('zoom'),
],
AmazingAgentLuna => [
	'Amazing Agent Luna',
	'http://www.amazingagentluna.com/strips-aal/file_01_-_page_1',
	group('zoom'),
],
DraculaEverlasting => [
	'Dracula Everlasting',
	'http://www.draculaeverlasting.com/strips-de/abraham_van_helsing_journal_i',
	group('zoom'),
],
ParanormalMysterySquad => [
	'Paranormal Mystery Squad',
	'http://www.paranormalmysterysquad.com/strips-pms/do_you_see_our_target',
	group('zoom'),
],
AOIHouse => [
	'AOI House',
	'http://www.aoihouse.net/strips-aoi/chewed_out',
	group('zoom'),
],
VampireCheerleaders => [
	'Vampire Cheerleaders',
	'http://www.vampirecheerleaders.net/strips-vc/inspiration_point_1',
	group('zoom'),
],
LizzieNewton => [
	'Lizzie Newton',
	'http://www.lizzienewton.com/strips-lizzie/chapter_1_-_page_1',
	group('zoom'),
],
WitchHunter => [
	'Witch Hunter',
	'http://www.witchhuntercomic.com/strips-wh/chapter_1_-_page_1',
	group('zoom'),
],
EerieCuties => [
	'Eerie Cuties',
	'http://www.eeriecuties.com/strips-ec/(chapter_1)_it_is_gonna_eat_me!',
	group('pixie'),
],
MagickChicks => [
	'Magick Chicks',
	'http://www.magickchicks.com/strips-mc/tis_but_a_trifle',
	group('pixie'),
],
SailorCosmos => [
	'Sailor Cosmos',
	'http://comics.shouri.com.ar/sailorcosmos00.html',
	[src => qr'sailorcosmos_shouri'],
],
Springiette => [
	'Springiette',
	'http://www.springiette.net/strips/the_beginning',
	[src => qr'dstrips/\d{8}'],
	url_hack => sub { $_[0] =~ s'/strips/dstrips'/dstrips'; $_[0] }
],
Fragile => [
	'Fragile',
	'http://www.fragilestory.com/strips/chapter_01',
	[src => qr'dstrips/\d{8}'],
	url_hack => sub { $_[0] =~ s'/strips/dstrips'/dstrips'; $_[0] }
],
CoolCatStudio => [
	'Cool Cat Studio',
	'http://coolcatstudio.com/strips-cat/first',
	group('pixie'),
],
SwordOClock => [
	'Sword O´ Clock',
	'http://gatogordito.wordpress.com/2012/01/15/sword-o´-clock/',
	[class => qr'wp-image-\d+'],
],
