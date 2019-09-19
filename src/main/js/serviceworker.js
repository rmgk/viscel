const cacheName = 'AppCache[inserted app cache name]';

self.addEventListener('install', function(e) {
    console.log("installing serviceworker in cache " + cacheName);
    self.skipWaiting();
    e.waitUntil(
        caches.open(cacheName).then(function(cache) {
            return cache.addAll([
                '',
                'js',
                'css'
            ].map(url => new Request(url, {credentials: 'same-origin'})));
        })
    );
});

self.addEventListener('activate', function(event) {
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


self.addEventListener('fetch', function(e) {
    e.respondWith(
        caches.match(e.request).then(function(response) {
            console.log("responding to " + e.request.url + " from cache " + response);
            return response || fetch(e.request);
        })
    );
});
