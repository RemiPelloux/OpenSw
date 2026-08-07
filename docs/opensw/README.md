# OpenSw handbook

OpenSw is a standalone Android Switch emulator distributed as `com.remipelloux.opensw`. Debug builds
use `com.remipelloux.opensw.debug`.

The maintained guides are:

- [Android build and installation](./Android.md)
- [Performance modes and A/B captures](./Performance.md)
- [Vulkan rendering compatibility](./Rendering.md)
- [Session stability and memory lifecycle](./SessionStability.md)
- [Secondary-screen cockpit and live cheats](./CockpitAndCheats.md)
- [Troubleshooting and safe diagnostics](./Troubleshooting.md)
- [Engineering roadmap](./Roadmap.md)

OpenSw never changes Android power modes, CPU/GPU frequencies or data owned by another application.
Optional legacy import is read-only and requires an explicit Android document-provider grant.
