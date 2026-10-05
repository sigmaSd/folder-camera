// Development-only fixture preview; never imported by the packaged receiver.
// @ts-types="qrcode-types"
import QRCode from "qrcode";
const assets = new URL("./ui/", import.meta.url);
const pair = {
  version: 1,
  receiverId: crypto.randomUUID(),
  endpoint: "https://127.0.0.1:19445",
  fingerprint: "0".repeat(64),
  secret: btoa(
    String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))),
  ).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", ""),
  expiresAt: Date.now() + 300000,
};
const state = {
  initialized: true,
  status: "ready",
  error: null,
  root: "/home/sigmasd/Captures",
  endpoint: pair.endpoint,
  active: 0,
  pairing: pair,
  qr: await QRCode.toString(JSON.stringify(pair), { type: "svg", margin: 1 }),
  devices: [],
  recent: [],
  autostart: false,
  closeToTray: false,
  tray: true,
};
const adapter = `let s=${
  JSON.stringify(state)
}; globalThis.receiverPreview={snapshot:async()=>s,start:async()=>{s.status='ready';return s},stop:async()=>{s.status='stopped';return s},pair:async()=>{s.pairing.expiresAt=Date.now()+300000;return s},revoke:async()=>s,chooseFolder:async()=>({fallback:true}),browseFolders:async(path)=>({path:path||s.root,parent:'/home/sigmasd',folders:[{name:'Projects',path:'/home/sigmasd/Captures/Projects'},{name:'Travel',path:'/home/sigmasd/Captures/Travel'}]}),setRoot:async(path)=>{s.root=path;return s},openFolder:async()=>{},setAutostart:async(v)=>{s.autostart=v;return s},setCloseToTray:async(v)=>{s.closeToTray=v;return s},quit:async()=>{s.status='stopped';return s}};document.querySelector('.brand span').textContent='Desktop receiver · fixture preview';`;
Deno.serve({ hostname: "127.0.0.1", port: 19446 }, async (request) => {
  const path = new URL(request.url).pathname;
  if (path === "/preview.js") {
    return new Response(adapter, {
      headers: { "content-type": "text/javascript" },
    });
  }
  const files: Record<string, string> = {
    "/": "text/html",
    "/app.js": "text/javascript",
    "/app.css": "text/css",
    "/icon.png": "image/png",
    "/licenses.txt": "text/plain; charset=utf-8",
  };
  if (!files[path]) return new Response("Not found", { status: 404 });
  if (path === "/") {
    return new Response(
      (await Deno.readTextFile(new URL("index.html", assets))).replace(
        '<script src="app.js" defer>',
        '<script src="preview.js" defer></script><script src="app.js" defer>',
      ),
      { headers: { "content-type": files[path] } },
    );
  }
  return new Response(await Deno.readFile(new URL(path.slice(1), assets)), {
    headers: { "content-type": files[path] },
  });
});
