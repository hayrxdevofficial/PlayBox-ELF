# PlayBox

PlayBox is an experimental Android x86-64 ELF test environment.

## v0.2 preview
- Select a local ELF file and configure a guest RAM budget (128 MB–6 GB).
- Configure a virtual storage capacity (1–70 GB); disk I/O is not implemented yet.
- Select and preview PNG, JPEG, and WebP images.
- Select custom package files ending in `.tuns` (PlayBox applications) or `.game.surb` (PlayBox game packages). Selection is present; package installation and game downloading are not implemented yet.
- The app displays a built-in PlayBox splash image when it starts.
- GitHub Actions builds a small demonstration `PlayBox.elf` alongside the Android APK. The demo ELF is a tiny x86-64 executable for testing the current interpreter; the startup splash is rendered by the Android app, not by graphics instructions inside the ELF.

## ELF support
The current interpreter accepts static little-endian ELF64 x86-64 ET_EXEC images and supports only a small instruction subset: NOP, MOV register/immediate, register XOR/MOV, ADD/SUB RAX immediate, RET, HLT, and INT3 reporting.

## Limitations
This is not a PlayStation emulator and cannot run PS4/PS5 games or Sony firmware. It does not implement PlayStation OS APIs, ELF dynamic linking, shared libraries, system calls, a complete x86-64 CPU, graphics/audio APIs, controllers, or virtual disk I/O. Most real game ELF files will not run. RAM is a guest memory budget; only the loaded image span is allocated, currently capped at 128 MiB.

## Build
GitHub Actions builds the debug APK and `PlayBox.elf` demo. Download the `PlayBox-v0.2-debug` artifact from the workflow run.
