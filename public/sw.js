const CACHE = "avatar-cache-v1";
const ASSETS = [
  "./index.html",
  "./main.js"
];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => {
      return Promise.allSettled(
        ASSETS.map(url => c.add(url).catch(() => console.warn(`Failed to cache: ${url}`)))
      );
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(k => (k === CACHE ? null : caches.delete(k))))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request).catch(() => {
      console.warn(`Fetch failed for: ${e.request.url}`);
      return new Response("Offline");
    }))
  );
});