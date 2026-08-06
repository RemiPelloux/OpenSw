# Cockpit and live cheats

When Android exposes an active secondary display in the `DISPLAY_CATEGORY_PRESENTATION` category,
OpenSw opens its cockpit there and leaves the game on the primary display. The cockpit contains
Performance, Cheats and Session tabs. A secondary display without Android's presentation flag is not
claimed by OpenSw; the same Performance and Cheats tools remain in the right in-game drawer instead.

The Cheats panel loads only the file matching the running main NSO Build ID. Mastercode is enabled
and locked. Other sections can be switched while the game continues. Disabling stops future opcode
execution but cannot undo a memory write already performed, so some codes still require restart.

Cheat state is keyed by Title ID, Build ID and opcode fingerprint. Search uses Android's compact IME
mode in landscape and returns focus to the controller list after `Done`.
