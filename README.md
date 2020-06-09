Viscel
======
![latest release](https://img.shields.io/github/v/release/rmgk/viscel)


Description
-----------

Viscel is a tool to download webcomics and then present them in a browser again. The main reasons to do so are:
* Downloaded archives never vanish, enjoy your comics today, still enjoy them tomorrow.
* Archives are downloaded in advance, no more waiting for slow servers to show you the next image.
* Read all comics with the same UI, the same controls, the same way to bookmark. No more guessing where to click for next, no more trying to remember where you last stopped reading.
* Get an overview of webcomics you have bookmarked, and how many new pages are available.


Quick start
-----------

* download release
* extract to somewhere with enough space
* run the viscel binary
  on windows you may have to install [1], but you are used to installing random crap like this anyways, right?
* navigate to http://localhost:2358 in your browser
* enter a username & password to create a user
* click any comic from the list, the comic will start downloading, refresh page after some time
* hover over icons to get a description

[1] https://www.microsoft.com/en-us/download/details.aspx?id=52685 

Caveats
-------

* Viscel is not really designed for new users, you may have to experiment a bit to figure out how it works.
* Webcomics change their websites from time to time, resulting in downloads breaking. This will not affect your existing archives.
* The local webserver Viscel starts is reachable from anywhere by default. This makes it easy to read from your Smartphone/Tablet if you want, but also means that anyone in your network can potentially access the server.

