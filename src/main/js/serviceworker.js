const cacheName = 'AppCache[inserted app cache name]';

self.addEventListener('install', function (e) {
    console.log("installing serviceworker in cache " + cacheName);
    self.skipWaiting();
    e.waitUntil(
        caches.open(cacheName).then(function (cache) {
            return cache.addAll([
                '',
                'js',
                'css'
            ].map(url => new Request(url, {credentials: 'same-origin'})));
        })
    );
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(keys => Promise.all(
            keys.map(key => {
                if (key !== cacheName) {
                    return caches.delete(key);
                }
            })
        )).then(() => {
            console.log('SW ' + cacheName + ' now ready to handle fetches');
        })
    );
});


self.addEventListener('fetch', function (e) {
    e.respondWith(
        caches.match(e.request).then(function (response) {
            console.log("responding to " + e.request.url + " from cache " + response);
            return response || fetch(e.request);
        })
    );
});

// c.f. https://github.com/vaneenige/offline-gallery/blob/master/sw.js
self.addEventListener('message', (event) => {
    const vid = event.data.vid;
    caches.open('vid' + vid).then((cache) => {
        switch (event.data.command) {
            case 'add':
                const request = new Request(event.data.url, {mode: 'no-cors'});
                return cache.match(request)
                    .then(function (response) {
                        if (!response) fetch(request)
                            .then(response => cache.put(event.data.url, response));
                    });
            case 'delete':
                return cache.delete(event.data.url);
            default:
                throw Error(`Unknown command: ${event.data.command}`);
        }
    });
});