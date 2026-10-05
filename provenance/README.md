# Verifiable authorship

Horizon OS was developed by Ranvir Singh using Android/LineageOS and AI-assisted engineering. This public key verifies the signed original creator record and preview release manifest. Private signing material is never published.

Public-key SHA-256 (DER): `9ac0e7396c50f20dd16d5773ee932dd9c5c483353f5e3da1b266716b43adae1d`

Verify the release manifest with OpenSSL:
```sh
openssl pkeyutl -verify -pubin -inkey owner-public.pem -rawin -in RELEASE.json -sigfile RELEASE.sig
```
Then compare each downloaded asset’s SHA-256 with `RELEASE.json`. Successful verification proves the exact bytes were signed by this key; the account/repository link supplies the public attribution to Ranvir Singh. This does not prove all build claims or imply independent third-party certification.

The ROM integration includes `creator.json`, its signature, and the public key under `/product/etc/horizon/`. Existing Android release signatures protect official build contents. A person changing an OS can remove these files or replace a key, but cannot make changed contents validate against this published key without the original private key. Blockchain cannot prevent editing or removal.

This is a portfolio source preview. No production firmware or installable full OTA is included.
