# Horizon OS

**A real-sky Android experience, developed by Ranvir Singh.**

Horizon OS is my Android 16 customization project, built with AI-assisted engineering on an Infinix GT 10 Pro (X6739), using Android and LineageOS as its foundation. It connects the interface to the sky: local weather, the sun’s position, moon phases, and a continuous analog clock across Home, Lock screen, and Always-on.

## The experience

- An animated sky with natural cloud artwork, location-aware sunlight and moon phases, stars, meteors and a small aircraft.
- Coordinated sky palettes, translucent controls, and a themed search widget.
- A shared analog clock and sparse Always-on sky with a continuous screen-off handoff.
- A Horizon startup reveal, forward fluid loader and the credit **OS developed by Ranvir Singh**.
- An offline photo restoration companion with separate enhanced copies and preserved originals. Reconstructed details are estimates, not recovered ground truth.
- A GitHub-backed updater with device-separated feeds and Android’s existing signed update mechanism.

## Explore the engineering

| Area | Source |
| --- | --- |
| Sky rendering and theme | [Launcher design](app/src/main/java/dev/gtos/launcher/design) |
| Astronomy and meteor paths | [Domain layer](core/domain/src/main/kotlin/dev/gtos/domain) |
| Android integration | [ROM patch stack](rom/patches) |
| Photo restoration | [Photo enhancer](photoenhancer/src/main/java/dev/gtos/photoenhancer) |
| Verification of authorship | [Signed provenance](provenance/README.md) |

## Current release status

This is a **source and portfolio preview**, not a universal flashable ROM. The project has been built and exercised on my X6739. Home-to-Always-on testing retained the sun’s disc size with at most one physical pixel of measured center movement. This is a specific test result, not a guarantee across devices or every transition.

Full phone-installable distribution remains pending a clean release build, redistributable dependencies and device installation validation. Personal debugging keys, signing secrets, Google Apps APKs, device backups, photos, logs, and firmware images are excluded from this repository. Other phones require separate hardware integration and testing.

The existing update infrastructure is [gtos-updates](https://github.com/1Ranvir1/gtos-updates). Its technical URL is retained for compatibility; this preview does not add an OTA to that feed.

## Build the app source

Use JDK 17, Android SDK 37.0 and Build Tools 36.0.0. Configure your own untracked `local.properties`. Run `sh gradlew :app:assembleDebug :core:domain:test` (Windows: `gradlew.bat`). Native ROM patches require the matching LineageOS source trees and pinned device manifest; they are not an installer.

Photo model binaries are excluded from this source preview. Their notices are retained under `photoenhancer/src/main/assets`; a working photo build requires the correctly licensed models. The separately supplied WeatherGPT application is not redistributed here.

## Authorship and rights

Horizon OS project work and original additions: **Ranvir Singh, 2026**. See [COPYRIGHT.md](COPYRIGHT.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Existing Android, LineageOS and other upstream components retain their authorship and license terms.

Official preview artifacts are accompanied by an Ed25519-signed manifest. A modified build cannot reproduce that signature without the private key. People with control of a modified OS can still remove visible credits; neither signatures nor blockchain can make an editable OS’s credit impossible to remove.
