# OpenSw handbook

OpenSw is distributed as `com.remipelloux.opensw` and can coexist with the official Eden nightly
package. Debug builds use `com.remipelloux.opensw.debug`.

The maintained guides are:

- [Android build and installation](./Android.md)
- [Performance modes and A/B captures](./Performance.md)
- [Session stability and memory lifecycle](./SessionStability.md)
- [Secondary-screen cockpit and live cheats](./CockpitAndCheats.md)
- [Troubleshooting and safe diagnostics](./Troubleshooting.md)
- [Engineering roadmap](./Roadmap.md)

OpenSw never changes Android power modes, CPU/GPU frequencies or Eden private data. Eden import is
read-only and requires an explicit Android document-provider grant.
