// Offline support: keeps the app's files so it opens without a connection. Bump VERSION when files change.
const VERSION = "wl-7";
const FILES = ["./", "index.html", "app.css", "app.js", "logic.js", "catalog.json", "manifest.json", "icon-180.png", "icon-192.png", "icon-512.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
// Network first (so updates show up), but if the network is slow (one bar at the gym) the saved copy is used
// after 3 seconds; the network answer still refreshes the saved copy for next time.
let slowUntil = 0;
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET" || new URL(e.request.url).origin !== location.origin) return;
  const network = fetch(e.request).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(e.request, copy)); }
    return res;
  });
  const saved = () => caches.match(e.request, { ignoreSearch: true });
  // Once a request has waited 3 s, the network counts as slow for 20 s and the rest of the page's files
  // come from the saved copy straight away, so opening the app waits 3 s once, not 3 s per file.
  const wait = Date.now() < slowUntil ? 0 : 3000;
  const slow = new Promise((resolve) => setTimeout(resolve, wait)).then(saved).then((r) => {
    if (r && wait) slowUntil = Date.now() + 20000;
    return r;
  });
  e.respondWith(
    Promise.race([network.catch(() => null), slow]).then((first) =>
      first || network.catch(() => saved().then((r) => r || caches.match("index.html"))))
  );
  e.waitUntil(network.catch(() => {}));
});
