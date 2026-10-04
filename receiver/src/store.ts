import { jpegHeader } from "./jpeg.ts";
import { join, resolve } from "node:path";
import { createHash } from "node:crypto";
import {
  durableJson,
  filenameFree,
  hashFile,
  noLinks,
  portableDirectory,
  readJson,
  Serial,
  syncDirectory,
} from "./files.ts";
import {
  fail,
  HttpError,
  Metadata,
  metadata,
  Receipt,
  same,
} from "./validation.ts";

interface Journal {
  metadata: Metadata;
  temp: string;
  receivedAt: number;
  committed: boolean;
}
export class PhotoStore {
  readonly root: string;
  readonly state: string;
  private commit = new Serial();
  private locks = new Map<string, { serial: Serial; users: number }>();
  readonly partials: string;
  constructor(
    root: string,
    state: string,
    readonly receiverId: string,
    readonly maxSize = 64 * 1024 * 1024,
    readonly timeoutMs = 240_000,
  ) {
    this.root = resolve(root);
    this.state = resolve(state);
    this.partials = join(this.root, ".folder-camera-partials");
    if (
      this.root === "/" || this.state === "/" || this.state === this.root ||
      this.state.startsWith(this.root + "/") ||
      this.root.startsWith(this.state + "/")
    ) throw new Error("State and photo root must be independent directories");
  }
  async init(): Promise<void> {
    await Deno.mkdir(this.root, { recursive: true });
    await noLinks(this.root);
    await noLinks(this.state);
    await Deno.mkdir(join(this.state, "receipts"), {
      recursive: true,
      mode: 0o700,
    });
    await noLinks(join(this.state, "receipts"));
    await Deno.mkdir(join(this.state, "journals"), {
      recursive: true,
      mode: 0o700,
    });
    await noLinks(join(this.state, "journals"));
    await Deno.mkdir(this.partials, { recursive: true, mode: 0o700 });
    await noLinks(this.partials);
    await Deno.chmod(this.partials, 0o700);
    // Startup precedes serving; published files with a journal are verified before receipt repair.
    for await (const entry of Deno.readDir(join(this.state, "journals"))) {
      if (!/^[0-9a-f-]{36}\.json$/.test(entry.name)) continue;
      const journal = await readJson<Journal>(
        join(this.state, "journals", entry.name),
      );
      if (!journal) continue;
      await this.recover(journal);
    }
    for await (const entry of Deno.readDir(this.partials)) {
      if (/^[0-9a-f-]{36}\.part$/.test(entry.name)) {
        await Deno.remove(join(this.partials, entry.name));
      }
    }
  }
  private receiptPath(id: string) {
    return join(this.state, "receipts", `${id}.json`);
  }
  private journalPath(id: string) {
    return join(this.state, "journals", `${id}.json`);
  }
  private async destination(m: Metadata, create = false): Promise<string> {
    m = metadata(m, 256 * 1024 * 1024);
    return join(
      await portableDirectory(this.root, m.relativePath.split("/"), create),
      m.filename,
    );
  }
  async receipt(id: string): Promise<Receipt | null> {
    const receipt = await readJson<Receipt>(this.receiptPath(id));
    if (!receipt) return null;
    if (receipt.receiverId !== this.receiverId) {
      fail("receipt_unavailable", 503);
    }
    try {
      const result = await hashFile(
        await this.destination(receipt),
        receipt.byteSize,
      );
      if (result.size !== receipt.byteSize || result.hash !== receipt.sha256) {
        fail("receipt_unavailable", 503);
      }
    } catch (e) {
      if (e instanceof HttpError && e.code === "unsafe_root") throw e;
      return fail("receipt_unavailable", 503);
    }
    return receipt;
  }
  private async recover(j: Journal): Promise<void> {
    try {
      const path = await this.destination(j.metadata);
      const actual = await hashFile(path, j.metadata.byteSize);
      if (
        actual.size !== j.metadata.byteSize || actual.hash !== j.metadata.sha256
      ) return;
      // A different occupied file is never modified, and a hash mismatch never earns a receipt.
      const r: Receipt = {
        ...j.metadata,
        receiverId: this.receiverId,
        receivedAt: j.receivedAt,
      };
      await durableJson(this.receiptPath(r.photoId), r);
      await Deno.remove(this.journalPath(r.photoId));
      await syncDirectory(join(this.state, "journals"));
    } catch (e) {
      if (e instanceof Deno.errors.NotFound || e instanceof HttpError) return;
      throw e;
    }
  }
  put(
    m: Metadata,
    body: ReadableStream<Uint8Array> | null,
  ): Promise<Receipt> {
    m = metadata(m, this.maxSize);
    const requestDeadline = Date.now() + this.timeoutMs;
    let lock = this.locks.get(m.photoId);
    if (!lock) {
      lock = { serial: new Serial(), users: 0 };
      this.locks.set(m.photoId, lock);
    }
    lock.users++;
    return lock.serial.run(async () => {
      let old = await this.receipt(m.photoId);
      if (old) {
        if (!same(old, m)) {
          await body?.cancel();
          fail("conflict", 409);
        }
      }
      const journal = await readJson<Journal>(this.journalPath(m.photoId));
      if (journal) {
        if (!same(journal.metadata, m)) fail("conflict", 409);
        await this.recover(journal);
        const recovered = await this.receipt(m.photoId);
        if (recovered) {
          old = recovered;
        }
      }
      if (!body) fail("invalid_content", 422);
      const temp = `${crypto.randomUUID()}.part`;
      const tempPath = join(this.partials, temp);
      await noLinks(this.partials);
      const output = await Deno.open(tempPath, {
        createNew: true,
        write: true,
        mode: 0o600,
      });
      let published = false;
      try {
        const hash = createHash("sha256");
        let size = 0;
        const prefix = new Uint8Array(256 * 1024);
        let prefixSize = 0;
        let first = new Uint8Array(0);
        let tail = new Uint8Array(0);
        const reader = body.getReader();
        const deadline = requestDeadline;
        try {
          while (true) {
            const remaining = deadline - Date.now();
            if (remaining <= 0) fail("upload_timeout", 408);
            let timer: ReturnType<typeof setTimeout> | undefined;
            const data = await Promise.race([
              reader.read(),
              new Promise<never>((_, reject) => {
                timer = setTimeout(
                  () => reject(new HttpError("upload_timeout", 408)),
                  Math.min(remaining, 30_000),
                );
              }),
            ]).finally(() => clearTimeout(timer));
            if (data.done) break;
            const head = data.value.subarray(0, prefix.length - prefixSize);
            prefix.set(head, prefixSize);
            prefixSize += head.length;
            size += data.value.length;
            if (size > m.byteSize || size > this.maxSize) {
              fail(old ? "conflict" : "excessive_size", old ? 409 : 413);
            }
            if (first.length < 2) {
              first = Uint8Array.from([
                ...first,
                ...data.value.subarray(0, 2 - first.length),
              ]);
            }
            tail = Uint8Array.from(
              [
                ...tail,
                ...data.value.subarray(Math.max(0, data.value.length - 2)),
              ].slice(-2),
            );
            hash.update(data.value);
            let at = 0;
            while (at < data.value.length) {
              at += await output.write(data.value.subarray(at));
            }
          }
        } catch (e) {
          await reader.cancel().catch(() => {});
          throw e;
        } finally {
          reader.releaseLock();
        }
        if (size !== m.byteSize || hash.digest("hex") !== m.sha256) {
          fail(old ? "conflict" : "content_mismatch", old ? 409 : 422);
        }
        if (
          !jpegHeader(prefix.subarray(0, prefixSize)) || first[0] !== 255 ||
          first[1] !== 216 || tail[0] !== 255 || tail[1] !== 217
        ) fail("invalid_content", 422);
        await output.sync();
        output.close();
        if (old) return old;
        return await this.commit.run(async () => {
          const parent = await portableDirectory(
            this.root,
            m.relativePath.split("/"),
            true,
          );
          await filenameFree(parent, m.filename);
          const receipt: Receipt = {
            ...m,
            receiverId: this.receiverId,
            receivedAt: Date.now(),
          };
          await durableJson(this.journalPath(m.photoId), {
            metadata: m,
            temp,
            receivedAt: receipt.receivedAt,
            committed: false,
          });
          await noLinks(parent);
          await noLinks(this.partials);
          // link() publishes atomically and fails EEXIST. rename() would overwrite on Linux.
          await Deno.link(tempPath, join(parent, m.filename));
          published = true;
          await syncDirectory(parent);
          await durableJson(this.receiptPath(m.photoId), receipt);
          await Deno.remove(this.journalPath(m.photoId));
          await syncDirectory(join(this.state, "journals"));
          return receipt;
        });
      } catch (e) {
        if (e instanceof Deno.errors.AlreadyExists) fail("conflict", 409);
        if (e instanceof HttpError) throw e;
        if (
          e instanceof Error &&
          /ENOSPC|No space left|disk full/i.test(e.message)
        ) fail("insufficient_storage", 507);
        fail("transient_failure", 503);
      } finally {
        try {
          output.close();
        } catch { /* closed after sync */ }
        // Journal is retained on publication/receipt failure; startup will validate the published bytes.
        await Deno.remove(tempPath).catch(() => {});
        if (
          !published
        ) {
          /* A prior journal is deliberately retained to bind metadata across retries. */
        }
      }
    }).finally(() => {
      lock!.users--;
      if (lock!.users === 0) this.locks.delete(m.photoId);
    });
  }
}
