# PlayBox

PlayBox is an experimental Android x86-64 ELF test emulator.

## v0.1
- Select a local ELF file.
- Configure virtual RAM budget from 128 MB to 6 GB.
- Configure virtual storage capacity from 1 GB to 70 GB. In v0.1 this is only a setting; disk I/O is not emulated.
- Load static little-endian ELF64 x86-64 ET_EXEC images with PT_LOAD segments.
- Execute a small x86-64 subset: NOP, MOV register/immediate, register XOR/MOV, ADD/SUB RAX immediate, RET, HLT, and INT3 reporting.
- Report unsupported instructions and safety-limit stops.

## Limitations
This is not a PlayStation emulator and cannot run PS4/PS5 games or Sony firmware. It does not implement PlayStation OS APIs, graphics, audio, controllers, system calls, dynamic linking, shared libraries, or a complete x86-64 CPU. Most real game ELF files will not run yet. RAM is a guest memory budget; only the loaded image span is allocated, capped at 128 MiB.

## Build
GitHub Actions builds a debug APK on pushes to main and manual workflow dispatch. Download the PlayBox-debug artifact from the workflow run.
