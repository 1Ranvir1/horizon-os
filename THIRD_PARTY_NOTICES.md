# Upstream acknowledgments

Horizon OS builds on Android Open Source Project and LineageOS. The public repository contains original app source and patch additions rather than a copy of the entire platform.

- Android / LineageOS: existing source-file notices and upstream licenses apply. https://source.android.com/ and https://github.com/LineageOS
- X6739 integration: pinned upstream projects are listed in `rom/local_manifests/x6739.xml`. Kernel and proprietary vendor binaries are not redistributed here.
- SunCalc: its retained notice is `app/src/main/assets/SunCalc-LICENSE.txt`.
- Photo restoration: retained model notices are in `photoenhancer/src/main/assets`. Model binaries are excluded.
- Gradle wrapper: upstream Gradle distribution and wrapper licensing applies. https://github.com/gradle/gradle/blob/master/LICENSE
- AndroidX, Kotlin, ONNX Runtime and other dependency licenses remain their respective authors’ licenses; these dependencies are resolved by the build rather than bundled in this repository.

The Horizon startup artwork is supplied by Ranvir Singh. The existing WeatherGPT APK and Google Apps binaries are not included. No kernel-source compliance or vendor redistribution completeness is claimed for this portfolio preview.
