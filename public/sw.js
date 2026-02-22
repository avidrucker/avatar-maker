const CACHE = "avatar-cache-v2";
const ASSETS = [
  "./",
  "./index.html",
  "./global.css",
  "./js/main.js",
  "./manifest.webmanifest",
  "./vite.svg",
  "https://unpkg.com/tachyons@4.12.0/css/tachyons.min.css"
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
  if (e.request.method !== "GET") {
    return;
  }

  e.respondWith(
    fetch(e.request)
      .then((res) => {
        // Keep cache fresh so refresh picks up latest app assets.
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() =>
        caches.match(e.request).then((hit) => {
          if (hit) return hit;
          if (e.request.mode === "navigate") return caches.match("./index.html");
          return Response.error();
        })
      )
  );
});
