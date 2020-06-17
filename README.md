Viscel
======
[![latest release](https://img.shields.io/github/v/release/rmgk/viscel)](https://github.com/rmgk/viscel/releases)


Description
-----------

Viscel is a tool to download webcomics and then present them in a browser again. The main reasons to do so are:
* Downloaded archives never vanish, enjoy your comics today, still enjoy them tomorrow.
* Archives are downloaded in advance, no more waiting for slow servers to show you the next image.
* Read all comics with the same UI, the same controls, the same way to bookmark. No more guessing where to click for next, no more trying to remember where you last stopped reading.
* Get an overview of webcomics you have bookmarked, and how many new pages are available.


Quick start
-----------

* Download release [1].
* Extract to somewhere with enough space.
* Run the Viscel binary.
  * On windows you may have to install [2], but you are used to installing random crap like this anyways, right?
* Navigate to http://localhost:2358 in your browser.
* Enter a username & password to create a user.
* Click any comic from the list, the comic will start downloading, refresh page after some time.
* Hover over icons to get a description.

[1] https://github.com/rmgk/viscel/releases <br>
[2] https://www.microsoft.com/en-us/download/details.aspx?id=52685 

Caveats
-------

* Viscel is not really designed for new users, you may have to experiment a bit to figure out how it works.
* Webcomics change their websites from time to time, resulting in downloads breaking. This will not affect your existing archives.
* The local webserver Viscel starts is reachable from anywhere by default. This makes it easy to read from your smartphone/tablet if you want, but also means that anyone in your network can potentially access the server.

