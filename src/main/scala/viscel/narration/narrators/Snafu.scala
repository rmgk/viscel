package viscel.narration.narrators

import viscel.narration.{Queries, Templates}

import scala.collection.immutable.Set


object Snafu {

  def Snar(id: String, name: String, start: String) = Templates.SimpleForward(id, name, start,
    Queries.queryImageNext(".comicpage img", ".comicnav a.previous")
  )

  def cores = Set(
    ("grim", "Grim Tales", "http://snafu-comics.com/swmcomic/chapter-1/"),
    ("sugar", "Sugar Bits", "http://snafu-comics.com/swmcomic/prolouge/"),
    ("ppg", "PowerPuff Girl Doujinshi", "http://snafu-comics.com/swmcomic/ppg-chapter-1/"),
    ("mypanda", "MyPanda", "http://snafu-comics.com/swmcomic/mypanda/"),
    ("zim", "Invader Zim", "http://snafu-comics.com/swmcomic/cover-art/"),
    ("titan", "Titan Sphere", "http://snafu-comics.com/swmcomic/it-begins-3/"),
    ("naruto", "Naruto: Heroes Path", "http://snafu-comics.com/swmcomic/onichan/"),
    ("league", "The League", "http://snafu-comics.com/swmcomic/in-which-we-are-introduced/"),
    ("braindead", "Brain Dead", "http://snafu-comics.com/swmcomic/brain-dead-intro/"),
    ("snafu", "Snafu Comics", "http://snafu-comics.com/swmcomic/remix-v2-0/"),
    ("trunksandsoto", "Trunks & Soto", "http://snafu-comics.com/swmcomic/junk-in-the-trunk/"),
    ("skullboy", "Fluffy Doom", "http://snafu-comics.com/swmcomic/skull-boy-ch-1-cover/"),
    ("ea", "Ever After", "http://snafu-comics.com/swmcomic/page-1/"),
    ("tw", "Training Wheels", "http://snafu-comics.com/swmcomic/conker-and-cloud-in-a-bar/"),
    ("sf", "Sticky Floors", "http://snafu-comics.com/swmcomic/im-with-that-guy/"),
    ("soul", "Soul Frontier", "http://snafu-comics.com/swmcomic/prolouge-2/"),
    ("tin", "Tin The Incompetent Ninja", "http://snafu-comics.com/swmcomic/it-begins/"),
    ("bunnywith", "Bunnywith", "http://snafu-comics.com/swmcomic/intro/"),
    ("satans", "Satan's Excrement", "http://snafu-comics.com/swmcomic/satans-excrement/"),
    ("ft", "Forgotten Tower", "http://snafu-comics.com/swmcomic/prolouge-3/"),
    ("kof", "King of Fighters Doujinshi", "http://snafu-comics.com/swmcomic/king-of-fighters/"),
    ("dp", "Digital Purgatory", "http://snafu-comics.com/swmcomic/0001/"),
    ("stbb", "Sure to be Ban'd", "http://snafu-comics.com/swmcomic/not-funny/")
  ).map { case (id, name, url) => Snar(s"Snafu_$id", s"[snafu] $name", url) }

}
