import { join } from "node:path";
import { X509Certificate } from "node:crypto";
import { noLinks } from "./files.ts";

async function regular(path: string) {
  const info = await Deno.lstat(path);
  if (info.isSymlink || !info.isFile) throw new Error("Unsafe TLS state");
}
export async function certificate(state: string, bind: string) {
  await noLinks(state);
  const certPath = join(state, "certificate.pem");
  const keyPath = join(state, "private-key.pem");
  const exists = async (path: string) => {
    try {
      await regular(path);
      return true;
    } catch (e) {
      if (e instanceof Deno.errors.NotFound) return false;
      throw e;
    }
  };
  const haveCert = await exists(certPath);
  const haveKey = await exists(keyPath);
  if (haveCert !== haveKey) {
    throw new Error(
      "Incomplete TLS identity; restore the certificate/key pair before starting",
    );
  }
  if (!haveCert) {
    const result = await new Deno.Command("openssl", {
      args: [
        "req",
        "-x509",
        "-newkey",
        "rsa:3072",
        "-sha256",
        "-nodes",
        "-days",
        "3650",
        "-keyout",
        keyPath,
        "-out",
        certPath,
        "-subj",
        "/CN=Folder Camera Receiver",
        "-addext",
        `subjectAltName=IP:${bind}`,
        "-addext",
        "extendedKeyUsage=serverAuth",
        "-addext",
        "basicConstraints=critical,CA:FALSE",
        "-addext",
        "keyUsage=critical,digitalSignature,keyEncipherment",
      ],
      stdout: "null",
      stderr: "piped",
    }).output();
    if (!result.success) {
      throw new Error("OpenSSL certificate generation failed");
    }
  }
  await Deno.chmod(keyPath, 0o600);
  await Deno.chmod(certPath, 0o600);
  const cert = await Deno.readTextFile(certPath);
  const key = await Deno.readTextFile(keyPath);
  const fingerprint = new X509Certificate(cert).fingerprint256.replaceAll(
    ":",
    "",
  ).toLowerCase();
  return { cert, key, fingerprint };
}
