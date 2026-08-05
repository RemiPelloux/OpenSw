# OpenSw troubleshooting

## Safe diagnostic bundle

Open Settings and select `Share OpenSw diagnostics`. The ZIP contains build/device metadata and up
to five recent Performance Lab reports. It deliberately excludes keys, firmware, saves, game paths,
game files and Eden data.

## Session shutdown

If a native memory invariant fails, record the complete `HostMemory` message. OpenSw logs the page
table identity, address-space width, offset, length and separate-heap state; do not hide the failure
by disabling assertions or clipping the range.

## Secondary display

The cockpit requires Android to expose the lower screen as a non-default active display. If it does
not appear, Performance and Cheats remain available from the main in-game menu.
