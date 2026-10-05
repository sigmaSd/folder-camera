# Folder Camera — implementation specification

## 1. Purpose and instructions to the implementing agent

Build a generic native Android folder camera with an optional desktop receiver for automatic LAN uploads. This document is the agreed product specification. Implement working software, not just scaffolding or a design mockup.

Work in the selected local project directory. Inspect existing files and AGENTS.md before changing anything. Make routine implementation decisions autonomously; document material deviations. Verify current Android APIs, platform restrictions, dependency versions, and F-Droid requirements against official documentation rather than guessing versions.

Build the standalone Android app first, then the optional receiver and integration. Keep the scope generic: do not add patient-specific fields, terminology, or workflows.

Working title: Folder Camera. The name and final application ID may be changed before release. No store submission, public deployment, or release signing is requested.

## 2. Required experience

- On each fresh app launch, show the destination-path screen before camera capture. Do not silently resume shooting into yesterday's folder.
- The user must enter or explicitly select a relative folder path before taking any photo. The previous path may be suggested, but needs confirmation.
- Support nested paths from version one, e.g. Projects/Job_A/Before.
- After confirmation, open the camera. Capture any number of photos into that path until the user chooses Change folder.
- Keep the full current relative path visible on the camera screen; allow wrapping or expansion for long paths.
- Change folder opens path entry with the existing path available for editing. Cancel retains the current destination.
- Reusing an existing path adds photos safely; it never replaces existing files.
- Preserve the selected destination during rotation and ordinary background/foreground transitions. A new launch returns to the path gate.
- Pairing, settings, and browsing may be accessed without selecting a capture path.
- The Android app must be completely usable without a PC, account, internet connection, or pairing.
- LAN upload is optional and disabled by default.

### First-use setup

Ask the user to choose a base directory using Android's system folder picker (Storage Access Framework / ACTION_OPEN_DOCUMENT_TREE). Persist the granted access. Then request the relative capture path.

The base-directory setting is separate from the frequently changed relative capture path. Clearly show where photos will be saved. Do not request broad all-files access.

### Minimal screens

1. Destination: relative-path entry, recent paths, Start camera, links to settings and folders.
2. Camera: preview, shutter, flash control if available, current path, Change folder, latest thumbnail, local-save/upload counts.
3. Folder/photo browser: browse the captured tree, open photo previews, share/export photos through Android mechanisms.
4. Settings: base directory, optional PC sync switch, pairing/address management, pending-upload status and retry action.

Use a clean responsive Compose interface, large touch targets, accessibility labels, and string resources. Begin with English. Preserve Unicode folder names, including French and Arabic.

Do not add scanning, OCR, PDF conversion, filters, editing, video, cloud integration, or bidirectional synchronization in the first version.

## 3. File-tree semantics

The user chooses independent base directories on Android and PC. Mirror relative paths and filenames beneath those roots.

Example:
- Android selected root: Pictures/FolderCamera
- Relative path: Projects/Job_A/Before
- Android file: Pictures/FolderCamera/Projects/Job_A/Before/20261003_143920_482_a1b2c3d4.jpg
- PC selected root: /home/user/Captures
- PC file: /home/user/Captures/Projects/Job_A/Before/20261003_143920_482_a1b2c3d4.jpg

Mirror captured files and their parent directories. Empty-directory replication, deletes, moves, and renames are out of scope. This is photo upload, not a general filesystem synchronizer.

Generate filenames once on Android using capture time and a sufficiently long random suffix. Store a separate full UUID photo ID. Reopening folders, clock changes, and multiple devices must not overwrite files. Keep the filename stable during retries.

### Path rules

Treat input as a relative sequence of directory segments, using / as the separator. Validate on both Android and receiver.

- Reject empty paths, empty segments, leading/trailing separators, absolute paths, . and .. segments.
- Reject backslashes, NUL/control characters, drive prefixes, and characters incompatible with the supported desktop filenames.
- Apply a documented portable policy for reserved Windows names and trailing dots/spaces.
- Permit ordinary spaces inside names and Unicode letters.
- Do not silently rename, trim, or normalize accepted paths into different destinations. Show any proposed correction for confirmation.
- Define reasonable segment/path length limits and explain validation errors.
- Do not confuse an Android content URI with an ordinary filesystem path.
- Receiver must prevent symlink/junction/reparse-point escapes outside its configured root, not only lexical ../ traversal.
- Existing file collisions or incompatible case-sensitive names must produce an explicit conflict; never overwrite silently.

Changing the base directory does not rewrite the location or upload path of existing captures. Retain the information needed to access pending captures in earlier roots, or explicitly resolve them before releasing old permissions.

## 4. Android implementation

Preferred stack:
- Kotlin, Jetpack Compose, CameraX.
- Room for capture metadata and delivery records.
- Kotlin coroutines for active capture/upload coordination.
- OkHttp for networking.
- WorkManager for persistent background retry.
- Open-source QR decoding such as ZXing, without a proprietary service dependency.

Choose supported stable versions, pin them, and commit the Gradle wrapper/dependency configuration. Select and document minSdk based on actual APIs; target the appropriate current SDK. Do not invent version numbers from this spec.

Separate capture/storage, delivery queue, and connection configuration behind small testable interfaces. Avoid excessive framework abstraction.

### Storage and capture

The user-selected tree is the permanent Android destination. SAF may not provide filesystem-style atomic rename or fsync guarantees, so do not assume it does.

Suggested robust sequence:
1. At shutter press, freeze relative path, filename, base-tree URI, and applicable receiver ID. Assign photo ID; persist a CAPTURING record.
2. Capture into an app-private staging file using CameraX, keeping capture work off the UI thread.
3. After success, calculate size/hash and persist staging readiness.
4. Create nested SAF directories and copy to a new destination document, never replacing an existing document.
5. Close the output, verify completion (and bytes/hash where supported), and persist its document URI and SAVED state.
6. Make it eligible for upload; release staging only when the permanent copy and queue reference are safe.

Track intermediate document URIs so startup reconciliation can distinguish incomplete writes from finalized photos. Where provider operations differ, use explicit state and recovery rather than assuming rename is atomic.

Surface storage failures as local-save failures, not network failures. Do not claim Saved until permanent storage succeeds. Preserve recoverable staging data when storage permission is revoked or the destination runs out of space. Allow regranting access/retrying.

On startup reconcile incomplete records/staging/documents. Avoid deleting a valid original or duplicating a finished photo after a crash.

Base directories can be provider-backed. Recommend an on-device folder for predictable offline operation; if a chosen provider cannot write offline, explain the failure and retain the staging copy.

### Metadata

Capture records should include:
- photoId (UUID), filename, capturedAt, relativePath
- baseTreeUri, destinationDocumentUri, staging reference where applicable
- MIME type, byte size, SHA-256
- capture/storage state, last local error

Delivery records should be separate and include:
- photoId, receiverId, delivery state
- attempt count, last attempt, next retry, last error, receipt metadata

For version one, support one active paired receiver. Keep queue records bound to their original receiver. Replacing a receiver must not silently redirect pending photos; require an explicit migration/discard decision for delivery tasks. Local originals remain intact.

### Upload behavior

- Sync off: capture locally; no pending/failed network indicator and no automatic network calls.
- Enable sync: offer Future photos only (default) or Selected existing folders plus future photos.
- Sync on, PC reachable: enqueue only after safe local save; immediately start upload while app is active.
- PC unavailable: continue capture and retain durable delivery tasks.
- Switching paths never changes queued destinations.
- Pausing/disabling sync stops scheduling transfers and pauses retained tasks. Explain that an already committed PC copy is not undone.
- Resume sync drains eligible tasks automatically.

Use a small concurrency limit (initially one or two transfers). Shutter operation must not wait for upload. Use backoff for transient failures, and distinguish permanent validation/auth/conflict failures.

While active, promptly check/retry on reconnect and provide Retry now. WorkManager handles deferred retries when backgrounded; do not promise immediate background uploads. Verify platform limitations and runtime LAN permission requirements for the chosen target SDK. LAN connections may have no internet, so do not require Android's validated-internet condition.

Coordinate active uploads and workers using durable claims/leases so the same photo does not start redundant concurrent requests. Recover expired claims after process death. Idempotency remains mandatory on the receiver.

Status language:
- Saving locally / Saved locally / Local save failed
- Upload pending / Sending / Received on PC / Upload paused / Upload failed

## 5. Optional PC receiver

Implement a small TypeScript/Deno receiver, initially targeting Fedora/Linux, with portable path behavior for future Windows support. It is not required to run the Android app.

CLI example (illustrative, implement and document actual options):
folder-camera-receiver --root /home/user/Captures --bind <LAN-IP> --port 8443

Requirements:
- User specifies the root; receiver creates captured relative directories beneath it.
- HTTPS only for normal pairing and uploads.
- Persist stable receiver identity, certificate/key, authorized-device credentials, and durable upload receipts in a separate application state directory.
- Do not put credentials or databases inside mirrored photo folders.
- Start-up output shows address, destination, and pairing instructions; never print permanent device tokens.
- Provide a one-time QR display and manual pairing fallback. Any local management page must be loopback-only and protected against hostile requests.
- Document Fedora firewall configuration, interface binding, private key permissions, and certificate persistence.
- Receiver should work without internet at runtime.
- Bounded streaming uploads; do not buffer whole photos in memory.
- Configure maximum photo size and request/concurrency/time limits.
- Useful logs with secrets excluded; avoid unnecessarily logging full user paths.

Desktop GUI follow-up accepted on 2026-10-05: provide a standalone desktop receiver with folder selection, expiring QR pairing, phone revocation, recent transfers, pause/start, tray controls and optional launch at login. Package ready-to-run downloads for Windows, macOS and Linux and link validated artifacts from the website alongside the signed Android APK. Keep the CLI for advanced use. Preserve existing receiver identities, certificate pins and old photo receipts when changing destinations. Public servers and cloud photo storage remain outside scope.

## 6. Pairing and transport security

Use QR-based trust bootstrap:
- QR includes protocol version, receiver ID, HTTPS endpoint, certificate SHA-256 fingerprint, and a cryptographically random expiring single-use pairing secret.
- Validate QR fields and length limits.
- Verify the actual server certificate against the out-of-band fingerprint; never use a global trust-all TLS configuration.
- After a successful pairing-secret exchange, issue a distinct revocable high-entropy device credential.
- Store Android credentials encrypted with a key protected by Android Keystore. Store server-side token hashes rather than plaintext where feasible.
- Rate-limit pairing attempts, make secret consumption atomic, and expire/cancel pending sessions.
- Reject wrong receiver identity or changed certificate; offer explicit re-pairing.
- Provide device revocation on the receiver.

For self-signed TLS, implement and test narrowly scoped trust for the paired receiver with suitable hostname handling; normal OkHttp certificate pinning alone does not make a self-signed certificate trusted.

Manual pairing must retain the same trust properties: enter address, obtain/check certificate fingerprint through a trusted PC display, and enter the temporary secret. An address alone is not authentication.

For a changed LAN IP, let users update the address while retaining verified receiver identity. Automatic mDNS discovery is deferred. No router port forwarding or public exposure.

## 7. Versioned HTTP protocol

Document exact request/response schemas, headers, errors, and compatibility rules in protocol.md. Keep v1 small. Proposed routes:

| Route | Purpose |
| --- | --- |
| POST /v1/pair | Exchange expiring secret for a device credential |
| GET /v1/health | Authenticated availability and receiver identity |
| PUT /v1/photos/{photoId} | Stream photo bytes with validated metadata |
| GET /v1/photos/{photoId} | Authenticated receipt lookup |

Use an Authorization bearer credential for authenticated routes. Metadata can use a bounded encoded header or multipart part; choose one and document the encoding so Unicode paths work. Never place credentials in URLs. Validate filename, relative path, size, MIME type, and SHA-256. Initial format is JPEG only.

A successful upload response includes photoId, receiverId, filename, relativePath, hash, byte size, and receipt time. Android validates the response against the queued task.

### Commit and idempotency

- Stream to a unique temporary file inside a receiver-controlled location on the destination filesystem.
- Verify received byte count/hash and reject invalid content.
- Flush/sync using supported OS operations, then atomically publish without overwrite.
- Commit a durable receipt and acknowledge only after file commit.
- Handle the crash window between file publication and receipt persistence with startup reconciliation; do not acknowledge a receipt for missing/incomplete bytes.
- Use a per-photo-ID lock/transaction to handle concurrent retries.
- Repeated ID with identical destination, filename, hash, and size returns the existing receipt.
- Repeated ID with different metadata/content returns conflict.
- Different IDs targeting an occupied filename return conflict; never overwrite.
- Remove/reconcile abandoned partials safely.

For concurrent hostile filesystem mutation, use handle-relative/no-follow facilities if available. If the chosen runtime cannot provide full race resistance, reject symlinks/reparse points, document the trusted-root assumption and residual limitation, and test the supported guarantees.

Use explicit error codes for unauthenticated, invalid path/metadata, excessive size, conflict, insufficient storage, and transient failure. Restart full-photo uploads on interruption initially; chunked resume is deferred.

## 8. Open-source and F-Droid readiness

Intent: future submission to the official F-Droid repository; admission is not guaranteed.

Default proposed license: GPL-3.0-or-later for app and receiver. Include correct license text, notices, and third-party attribution; inspect dependency compatibility.

- Public-source-ready repository; no secrets or private release signing keys.
- Only free/open-source runtime dependencies; audit direct and transitive dependencies.
- No Google Play Services, proprietary ML Kit, Firebase SDKs, trackers, advertising SDKs, or proprietary crash reporting.
- QR scanning must work offline through a FOSS implementation.
- Standard documented command-line Gradle build, pinned versions, no opaque bundled binary dependencies.
- Do not require Android Studio to build.
- No Play Store-specific updater; release/update through distribution channels.
- No mandatory account, server vendor, analytics, or cloud endpoint.
- Disable OS cloud backup for sensitive pairing credentials and review backup rules for app metadata.
- Explain permissions and local-only network use.
- Add release tags and Fastlane-compatible store metadata when preparing a release.
- Reproducible builds are a goal; do not claim bit-for-bit reproducibility without verification.

Verify the current inclusion policy when preparing submission:
https://f-droid.org/docs/Inclusion_Policy/
https://f-droid.org/docs/FAQ_-_App_Developers/

## 9. Repository and deliverables

Suggested layout:
- android/ — native Android project
- receiver/ — Deno server, dependency lock, tests
- docs/protocol.md — v1 contract and security decisions
- docs/testing.md — emulator/device/LAN checklist
- README.md — build, installation, standalone use, pairing, receiver use
- LICENSE and third-party notices
- SPEC.md — this document

Deliver:
- Working Android source and buildable debug APK where toolchain availability permits.
- Working optional receiver source with documented startup command.
- Meaningful automated tests and manual hardware checks.
- Explicit list of actual checks run and anything blocked by unavailable SDK/device/network tooling.
- Do not claim real-camera, multi-device, or Fedora firewall testing from an emulator-only run.

## 10. Acceptance checks

### Standalone
- Fresh launch cannot capture until a relative path is confirmed.
- Nested paths, spaces, and Arabic/French names produce the intended tree.
- Multiple captures remain in one folder until Change folder.
- Reopening a folder never overwrites photos.
- Sync-off capture works with no PC and no internet; no network error or pending indicator.
- Rotation/background return preserves the active capture session appropriately.
- User-selected files are accessible through other apps and survive app uninstall; explain that app-private staging/metadata do not.
- Revoked SAF access, out-of-space, and interrupted writes preserve recoverable photos and show truthful status.

### LAN and recovery
- Sync-on photos reach the matching PC path while active without a manual share action.
- Lost Wi-Fi does not block capture; reconnect drains the queue.
- Changing folders with pending photos preserves each original path.
- Retry after a lost acknowledgement yields one PC file and the correct receipt.
- Killing Android during capture/local save/upload recovers without silent loss.
- Restarting receiver during upload leaves no partial file presented as completed.
- PC out-of-space and revoked credentials yield actionable errors.
- Disabling/re-enabling sync pauses/resumes delivery without losing originals.
- Pairing later uploads only the existing folders explicitly chosen.
- Changing the active receiver does not silently send old queued photos to a new machine.

### Validation/security
- Reject traversal, absolute paths, reserved names, encoded separator tricks, and destination symlink escapes.
- Test concurrent duplicate uploads and ID/filename conflicts.
- Wrong token, expired/reused pairing secret, and wrong certificate fail.
- Logs and error messages do not expose secrets.
- A LAN without internet still works.
- Verify foreground responsiveness with large photos and several queued captures.

Automate meaningful path, queue transition, crash-recovery, and receiver integration tests. Keep UI tests focused on the path gate and destination switching. Use dummy photos only during development.

## 11. Implementation order

1. Create Android project and portable path rules.
2. Implement base directory selection, destination gate, CameraX capture, safe local persistence, and previews.
3. Verify standalone operation and storage recovery.
4. Implement receiver commit/idempotency and protocol tests.
5. Implement certificate-verified pairing and revocation.
6. Add optional sync, durable queue, foreground uploads, background retry, and status UI.
7. Exercise failure scenarios, document builds/use, and audit dependencies for F-Droid.

Do not stop at a plan. Carry the implementation through the available build and validation steps, reporting concrete blockers without inventing successful tests.

