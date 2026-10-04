# Play Console submission worksheet

Publication model: one-time paid download on Google Play, free on official F-Droid, same GPL-3.0-or-later code and capabilities. No in-app purchases, subscriptions, ads, proprietary license checks or Play runtime dependency.

- App title: Folder Camera
- Application ID: io.github.sigmasd.foldercamera
- Publisher/display name: sigmasd
- Legal account owner: registered SARL — owner must provide verified legal details
- Play account: organization, not created/verified yet
- Price: pending owner decision; set paid before any public free offering
- Support email/phone: pending verified company contacts
- Source: https://github.com/sigmasd/folder-camera
- Privacy URL: https://sigmasd.github.io/folder-camera/privacy.html
- Distribution territories: pending owner decision; default proposal is supported countries without unverified special declarations
- Category proposal: Photography
- Contains ads: No
- Account creation/sign-in: No app accounts
- App access/reviewer instructions: all core functions available without an account; choose a local base folder and path. Receiver is optional.
- Content rating: complete the questionnaire using actual app capabilities; generic camera utility, no provided social feed/content catalogue
- Target audience: general-purpose photography utility; final age selections require owner confirmation
- Health/financial/government/news declarations: generic utility, no specialized features; answer each current Console form accurately

## Data safety evidence to review before submission

No analytics, advertising, developer-operated backend or crash-reporting SDK. Local photos, storage URIs and metadata remain on the phone in standalone mode. Sync is optional and sends JPEGs (including ordinary JPEG metadata), filenames, relative paths, photo IDs, byte sizes and SHA-256 directly to the user's paired receiver. Pairing sends an expiring secret and device name, then exchanges a receiver-bound credential. TLS is certificate-scoped; keys/receiver storage are controlled by the user. Android sharing/export and provider-backed trees can transfer files to user-selected destinations/apps.

Google's definitions include off-device transfer and exceptions for end-to-end encryption and certain user-initiated sharing. Do not automatically select “no data collected” merely because there is no cloud backend. Match the final declaration to the verified sender/recipient/encryption model and user-initiated actions; this worksheet is not a filed declaration.

## Upload and release

Use the AAB signed by the separate upload key. Supply the independently generated app-signing key to Play App Signing so it can match developer-signed standalone APKs for F-Droid reproducible distribution. Test internal-track installs/upgrades and fix pre-launch findings before production. Never upload the debug APK.

Owner actions: legal identity/D-U-N-S verification, developer terms and registration payment, verified merchant/bank profile, price and public contact approval, and granting release access. Do not store identity documents, bank data or key passwords in this repository.
