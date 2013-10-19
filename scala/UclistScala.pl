use v5.014;
use Data::Dumper;
use utf8;

#fetches the list of known remotes
sub fetch_list {
	my %raw_list = do ('../src/Uclist.pm');
	if ($@) {
		chomp $@;
		say('could not load list: ' , $@);
		return;
	}
	my %list;
	for my $id (keys %raw_list) {
		my @l = @{$raw_list{$id}};
		$list{'Universal_'.$id} = {name => shift @l,
								start => shift @l };
		my @criteria;
		push(@criteria,shift @l) while ref $l[0];
		$list{'Universal_'.$id}->{criteria} = \@criteria;
		while ($l[0]) {
			given (shift @l) {
				when ('next') {
					my @next;
					push(@next,shift @l) while ref $l[0];
					$list{'Universal_'.$id}->{next} = \@next;
				}
				when ('url_hack') {
					$list{'Universal_'.$id}->{url_hack} = shift @l;
				}
				when ('custom_match') {
					$list{'Universal_'.$id}->{custom_match} = shift @l;
				}
				default {
					Log->error('illegal entry: ' . $_);
					die "illegal entry: " . $_;
				}
			}
		}
	}
	return \%list;
}

sub scalaify {
	my ($it, $indent) = @_;
	if (ref($it) eq "HASH") {
		my @lines = map {"$_ = " .  scalaify($it->{$_}, $indent + 1) } keys %$it;
		return "LegacyCollection(\n". ("\t" x $indent) . (join ",\n" . ("\t" x $indent), @lines) . ")";
	}
	elsif (ref($it) eq "ARRAY") {
		my @lines = map {"(" . scalaify($_, $indent + 1) . ')' } @$it;
		return "Seq(\n". ("\t" x $indent) . (join ",\n" . ("\t" x $indent), @lines) . ")";
	}
	elsif (ref($it) eq "Regexp") {
		return '"""' . ("$it" =~ s/^\(\?\^(\w+):/(?\1:/r) . '""".r'; #/
	}
	elsif(ref($it) eq "CODE") {
		return 'Some("CODE")';
	}
	else {
		say ref($it) if ref($it);
		return '"""' . $it . '"""';
	}
}

open my $out, ">utf8", "Uclist.scalaconf";
my %list = %{fetch_list()};

say $out "import viscel.core.LegacyCollection

object UclistPort {
implicit def seqToOpt[T](xs: Seq[T]): Option[Seq[T]] = Some(xs)";
say $out "def get = Seq(";
print $out join ",\n", keys %list;
say $out ")\n";


for my $id (keys %list) {
	print $out "def $id = ";
	$list{$id}->{id} = $id;
	print $out scalaify($list{$id},1);
	say $out "\n";
}

say $out "}\n UclistPort.get";



