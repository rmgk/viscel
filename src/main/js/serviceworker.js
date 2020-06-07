const cacheName = '[inserted app cache name]';

self.addEventListener('install', function (e) {
    console.log("installing serviceworker in cache " + cacheName);
    self.skipWaiting();
    e.waitUntil(
        caches.open(cacheName).then(function (cache) {
            const cachedRequests = [
                './',
                'viscel-opt.js',
                'style.css',
                'localforage.min.js',
                'icon.png'
            ].map(url => new Request(url, {credentials: 'same-origin'}));
            return cache.addAll(cachedRequests);
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
        ))
    );
});


self.addEventListener('fetch', function (e) {
    e.respondWith(
        caches.match(e.request).then(function (response) {
            return response || fetch(e.request);
        })
    );
});
