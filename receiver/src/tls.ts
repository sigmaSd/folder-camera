import "reflect-metadata";
import { join } from "node:path";
import {
  createPrivateKey,
  createPublicKey,
  X509Certificate,
} from "node:crypto";
import * as x509 from "@peculiar/x509";
import { durableJson, noLinks, readJson, syncDirectory } from "./files.ts";
import { privateMode } from "./platform.ts";
interface Identity {
  version: 1;
  cert: string;
  key: string;
}
async function regular(path: string) {
  const info = await Deno.lstat(path);
  if (info.isSymlink || !info.isFile) throw new Error("Unsafe TLS state");
}
async function generate(bind: string): Promise<Identity> {
  const keys = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 3072,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const serial = crypto.getRandomValues(new Uint8Array(16));
  serial[0] &= 0x7f;
  const now = Date.now();
  const cert = await x509.X509CertificateGenerator.createSelfSigned({
    serialNumber: [...serial].map((n) => n.toString(16).padStart(2, "0")).join(
      "",
    ),
    name: "CN=Folder Camera Receiver",
    notBefore: new Date(now - 60_000),
    notAfter: new Date(now + 3650 * 86400_000),
    signingAlgorithm: { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    keys,
    extensions: [
      new x509.BasicConstraintsExtension(false, undefined, true),
      new x509.KeyUsagesExtension(
        x509.KeyUsageFlags.digitalSignature |
          x509.KeyUsageFlags.keyEncipherment,
        true,
      ),
      new x509.ExtendedKeyUsageExtension(["1.3.6.1.5.5.7.3.1"]),
      new x509.SubjectAlternativeNameExtension([{ type: "ip", value: bind }]),
    ],
  }, crypto);
  const bytes = new Uint8Array(
    await crypto.subtle.exportKey("pkcs8", keys.privateKey),
  );
  const encoded = btoa(String.fromCharCode(...bytes));
  return {
    version: 1,
    cert: cert.toString("pem") + "\n",
    key: `-----BEGIN PRIVATE KEY-----\n${
      encoded.match(/.{1,64}/g)!.join("\n")
    }\n-----END PRIVATE KEY-----\n`,
  };
}
export async function certificate(state: string, bind: string) {
  await noLinks(state);
  const certPath = join(state, "certificate.pem"),
    keyPath = join(state, "private-key.pem");
  const exists = async (path: string) => {
    try {
      await regular(path);
      return true;
    } catch (e) {
      if (e instanceof Deno.errors.NotFound) return false;
      throw e;
    }
  };
  const haveCert = await exists(certPath), haveKey = await exists(keyPath);
  // Preserve CLI identities; a durable journal repairs interrupted first-time generation.
  if (!haveCert || !haveKey) {
    let identity = await readJson<Identity>(join(state, "tls-identity.json"));
    if (!identity && haveCert !== haveKey) {
      throw new Error(
        "Incomplete TLS identity; restore the certificate/key pair before starting",
      );
    }
    if (!identity) {
      identity = await generate(bind);
      await durableJson(join(state, "tls-identity.json"), identity);
    }
    if (identity.version !== 1) {
      throw new Error("Unsupported TLS identity version");
    }
    for (
      const [path, text, exists] of [[keyPath, identity.key, haveKey], [
        certPath,
        identity.cert,
        haveCert,
      ]] as const
    ) {
      if (!exists) {
        const file = await Deno.open(path, {
          createNew: true,
          write: true,
          mode: 0o600,
        });
        try {
          const bytes = new TextEncoder().encode(text);
          let at = 0;
          while (at < bytes.length) at += await file.write(bytes.subarray(at));
          await file.sync();
        } finally {
          file.close();
        }
      }
    }
    await syncDirectory(state);
  }
  await privateMode(keyPath, 0o600);
  await privateMode(certPath, 0o600);
  const cert = await Deno.readTextFile(certPath),
    key = await Deno.readTextFile(keyPath),
    parsed = new X509Certificate(cert);
  if (
    !parsed.publicKey.export({ type: "spki", format: "der" }).equals(
      createPublicKey(createPrivateKey(key)).export({
        type: "spki",
        format: "der",
      }),
    )
  ) throw new Error("TLS certificate and private key do not match");
  return {
    cert,
    key,
    fingerprint: parsed.fingerprint256.replaceAll(":", "").toLowerCase(),
  };
}
