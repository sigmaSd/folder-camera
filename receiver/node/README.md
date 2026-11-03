# folder-camera-receiver (Node)

Folder Camera's receiver engine for Node: the same code as the desktop receiver and the CLI (`../src`), compiled for
apps that embed a receiver of their own instead of running the desktop app. It pairs phones (the QR payload),
receives photos over certificate-pinned HTTPS on the LAN, and keeps them durable: same protocol, same state folder
format, so a state folder moves between the desktop app, the CLI and an embedding app with its phones still paired.

Releases are tarballs on GitHub (`receiver-engine-vX.Y.Z`), installed by URL:

```sh
npm install https://github.com/sigmasd/folder-camera/releases/download/receiver-engine-v1.3.0/folder-camera-receiver-1.3.0.tgz
```

```ts
import { Receiver } from "folder-camera-receiver";

// its own state folder (identity, certificate, paired phones, port) and the folder photos go to
const receiver = new Receiver("/path/to/state", new Map([["--root", "/path/to/photos"]]));
await receiver.init(); // takes the state folder for this process: refused while another receiver holds it
await receiver.start(); // listens on the LAN; first start: a pairing code is pending
const { pairing } = await receiver.pair(); // a fresh single-use code: JSON.stringify(pairing) is the QR text
receiver.snapshot(); // status, error, endpoint, devices (paired phones), recent arrivals, pending code
setInterval(() => receiver.refreshNetwork(), 5000); // follows the PC to a new LAN address
await receiver.revoke(deviceId); // a phone can't send any more
await receiver.stop(); // stops listening (start() again)
await receiver.close(); // stops and releases the state folder
```

Options (the `Map`) are the command line's: `--root`, `--bind` (`auto`, the default, or a private IP), `--port`
(default: the saved one, else 8443; a busy automatic port moves to the next free one and is kept), `--max-mib`,
`--timeout-seconds`, `--concurrency`. Node 22 or later.

On Node the state lock is a pid file (no `flock`), and Windows publication is a hard link then the source removed.

Development: `npm ci`, `npm run build` (`dist/`), `npm test` (the compiled package end to end over HTTPS).
