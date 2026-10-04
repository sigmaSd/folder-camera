TLS tests create a fresh, short-lived key/certificate with the JDK keytool at runtime.
Temporary credentials are deleted when the test finishes; no private key is stored in source.
