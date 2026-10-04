# Folder Camera protocol v1

HTTPS only; no redirects. Credentials are never URL parameters. UTF-8 JSON responses have `Content-Type: application/json`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`. JSON request bodies are limited to 4,096 bytes; response clients cap JSON at 16,384 bytes. No CORS support: requests with an Origin header are rejected. Unknown routes return `not_found` after authentication.

All UUIDs are lowercase canonical v4 UUIDs. Timestamps are Unix epoch milliseconds. SHA-256 values are 64 lowercase hex characters. Unknown JSON fields are ignored for compatible additive changes. Required fields and version must match; incompatible changes require a new route/protocol version.

## Portable destination policy

`relativePath` is a sequence of 1–32 directory segments separated by `/`, at most 1,024 UTF-8 bytes total. Every segment is 1–120 UTF-8 bytes. `filename` is one segment, ends in lowercase `.jpg`, and has the same 120-byte limit. Unicode is preserved; malformed Unicode is rejected. No normalization or trimming changes accepted destinations.

Reject empty segments; leading/trailing separators; `.`, `..`; slash within a filename; backslash; C0/C1 controls (U+0000–001F and U+007F–009F); `: * ? " < > | %`; trailing dots or spaces; Windows names CON, PRN, AUX, NUL, COM1–COM9 and LPT1–LPT9, including superscript ¹²³ forms and extensions, case-insensitively. `%` is deliberately forbidden to avoid encoded-separator interpretations. Spaces inside names and Unicode letters, including French and Arabic, are allowed. `.folder-camera-partials` is reserved in any segment.

Directory/file comparisons conservatively detect NFC/case aliases and return conflict instead of renaming or merging incompatible spellings. The exact accepted spelling is used for creation. The PC and Android roots are independent; only the relative path and filename are mirrored. Existing file contents are never replaced. Empty directories, moves, deletion and reverse synchronization are out of scope.

## Trust bootstrap

QR or manual JSON:

```json
{"version":1,"receiverId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","endpoint":"https://192.168.1.50:8443","fingerprint":"<64 hex DER-certificate SHA-256>","secret":"<43 character base64url random secret>","expiresAt":1791087900000}
```

Payload limit: 4,096 characters; HTTPS origin address limit: 256 characters. No userinfo, query, fragment or non-root URL path. Receiver CLI advertises a private IPv4/ULA IPv6 or loopback address; Android additionally accepts `.local`/localhost and resolves only to private/loopback addresses. Link-local IPv6 scope identifiers and public endpoints are not supported in v1. The receiver enforces the actual five-minute expiry; manual entry cannot extend it.

Android compares the actual leaf DER certificate SHA-256 to the trusted PC value with a client-scoped X509TrustManager, checks certificate dates, limits hostname handling to the configured endpoint, and disables redirects. This supports self-signed certificates without global trust bypasses. An IP change is explicitly verified against the same certificate and authenticated receiver ID before saving the address.

### POST /v1/pair

Request, with `Content-Type: application/json`:

```json
{"version":1,"receiverId":"<UUID>","secret":"<temporary secret>","deviceName":"Android camera"}
```

Device name: at most 80 characters, no control characters. Receiver identity must match. Secret consumption and credential creation are serialized and durably persisted before response. Sixteen pairing attempts per minute per process; only one outstanding session. A new session cancels the previous one. Restarting with `--pair` creates a fresh session; shutdown cancels it.

Success (200):

```json
{"version":1,"receiverId":"<UUID>","deviceId":"<UUID>","token":"<43 character base64url credential>"}
```

The secret and credential each contain 32 cryptographically random bytes. Receiver persists only SHA-256 hashes and revocation metadata. Android encrypts the token/configuration with AES-GCM using Android Keystore and excludes all metadata/credentials/staging from backup and device transfer. Losing a pairing response requires a fresh session; unused device credentials can be revoked through offline management.

### GET /v1/health

`Authorization: Bearer <credential>`. Success (200): `{"version":1,"receiverId":"<UUID>"}`. Wrong/revoked credentials return 401.

## Photo delivery

### PUT /v1/photos/{photoId}

Headers:

- `Authorization: Bearer <credential>`
- `Content-Type: image/jpeg`
- `Content-Length: <byteSize>` when known; any supplied value must match metadata
- `X-FolderCamera-Metadata: <unpadded base64url of UTF-8 JSON>` (maximum 8,192 encoded characters)

Metadata:

```json
{"version":1,"photoId":"<UUID>","relativePath":"Été/صور/Job A","filename":"20261004_143920_482_<32 hex UUID>.jpg","mimeType":"image/jpeg","byteSize":1234567,"sha256":"<64 hex>"}
```

The URL ID must equal the metadata ID. Raw request body is the full JPEG, not multipart or JSON. Content is streamed, capped by declared size and configured maximum (64 MiB by default), SHA-256 checked, and verified for SOI/EOI plus a valid JPEG frame/SOS header within a bounded 256-KiB prefix. This is a structural check, not a full image decoder. Header formats not matching the supported 8-bit SOF0/SOF1/SOF2 JPEG envelope are rejected. CameraX captures normally meet this policy; unusual JPEG providers require testing.

Every retry restarts the whole body. One per-photo-ID serialized operation plus serialized publication prevents redundant commits. Repeated identical ID/metadata/body returns the same receipt; altered metadata or content returns 409. Another ID using an occupied filename, including incompatible case/canonical aliases, returns 409.

A new upload writes a unique 0600 file in the controlled partial directory on the photo filesystem. After count/hash validation it fsyncs the file, commits a durable journal in separate state, uses a hard link to publish atomically without overwrite, fsyncs the parent directory, writes/fsyncs the receipt with atomic state-file replacement, then acknowledges. Partial files are cleaned; startup recovers the publication/receipt gap by verifying the journal against permanent bytes. Receipts for missing/changed bytes are never acknowledged. Linux hard-link/fsync semantics are a runtime requirement.

Success (200) receipt:

```json
{"version":1,"photoId":"<UUID>","relativePath":"Été/صور/Job A","filename":"<stable filename>.jpg","mimeType":"image/jpeg","byteSize":1234567,"sha256":"<64 hex>","receiverId":"<UUID>","receivedAt":1791087800000}
```

Android verifies ID, receiver, exact path/filename, size, hash, version and positive receipt time against the durable delivery task before marking Received on PC.

### GET /v1/photos/{photoId}

Authenticated receipt lookup. Returns the same receipt after rechecking permanent file size/hash; 404 if no committed receipt; 503 if its bytes are missing/changed. Use this to investigate a lost acknowledgement. PUT retry is independently idempotent.

## Errors

JSON: `{"version":1,"code":"<stable category>"}`. No secret values or private path details.

| HTTP | Codes | Action |
|---|---|---|
| 400 | invalid_path, invalid_metadata, invalid_endpoint | Correct metadata/path; permanent |
| 401 | unauthenticated | Re-pair after checking revocation |
| 403 | pairing_invalid, forbidden_origin | Refresh trusted pairing session / reject web-origin request |
| 404 | not_found | Check ID/route |
| 408 | upload_timeout, request_timeout | Restart full upload with backoff |
| 409 | conflict, unsafe_root | Resolve occupied name/ID or unsafe tree; permanent |
| 413 | excessive_size | Raise receiver limit deliberately or use a supported capture size |
| 422 | content_mismatch, invalid_content | Check original JPEG; permanent |
| 429 | busy, rate_limited | Retry with backoff |
| 503 | transient_failure, receipt_unavailable, starting | Restore receiver/storage and retry |
| 507 | insufficient_storage | Free PC space; retry |

One Android transfer runs at a time; default receiver limit is two. Delivery records bind the original receiver ID and carry attempts, retry time, claim/ten-minute lease, last error and receipt. Expired claims are recoverable after process death; stale claim completion cannot overwrite a newer claim. Disabling sync pauses scheduling, and received copies remain committed.
