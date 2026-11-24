#include <jni.h>
#include <elf.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>

static std::string run_elf(const uint8_t* data, size_t size, int ram_mb) {
    if (!data || size < sizeof(Elf64_Ehdr)) return "Invalid ELF: file is too small.";
    const auto* eh = reinterpret_cast<const Elf64_Ehdr*>(data);
    if (std::memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) return "Not an ELF file.";
    if (eh->e_ident[EI_CLASS] != ELFCLASS64) return "Only ELF64 is supported.";
    if (eh->e_ident[EI_DATA] != ELFDATA2LSB) return "Unsupported ELF byte order.";
    if (eh->e_machine != EM_X86_64) return "Only x86-64 ELF is supported.";
    if (eh->e_type != ET_EXEC) return "v0.1 accepts static ET_EXEC only; PIE/shared ELF is not supported.";
    if (eh->e_phentsize != sizeof(Elf64_Phdr) || eh->e_phnum == 0) return "Invalid ELF program-header table.";
    if (eh->e_phoff > size || (uint64_t)eh->e_phnum * sizeof(Elf64_Phdr) > size - eh->e_phoff)
        return "Program-header table is outside the file.";
    const auto* ph = reinterpret_cast<const Elf64_Phdr*>(data + eh->e_phoff);
    uint64_t low = UINT64_MAX, high = 0;
    bool entry_exec = false;
    for (uint16_t i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type != PT_LOAD) continue;
        if (ph[i].p_filesz > ph[i].p_memsz || ph[i].p_offset > size || ph[i].p_filesz > size - ph[i].p_offset)
            return "Invalid PT_LOAD segment bounds.";
        if (!ph[i].p_memsz) continue;
        low = std::min(low, ph[i].p_vaddr);
        if (ph[i].p_vaddr > UINT64_MAX - ph[i].p_memsz) return "Segment address overflow.";
        high = std::max(high, ph[i].p_vaddr + ph[i].p_memsz);
        if ((ph[i].p_flags & PF_X) && eh->e_entry >= ph[i].p_vaddr && eh->e_entry < ph[i].p_vaddr + ph[i].p_memsz) entry_exec = true;
    }
    if (low == UINT64_MAX || high <= low) return "ELF has no loadable segments.";
    const uint64_t span = high - low;
    const uint64_t ram_limit = (uint64_t)std::max(32, ram_mb) * 1024ULL * 1024ULL;
    if (span > ram_limit || span > 128ULL * 1024ULL * 1024ULL)
        return "ELF image exceeds the configured RAM budget or the 128 MiB loader cap.";
    if (!entry_exec || eh->e_entry < low || eh->e_entry >= high) return "Entry point is not in an executable segment.";
    std::vector<uint8_t> memory((size_t)span, 0);
    for (uint16_t i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type == PT_LOAD && ph[i].p_memsz) {
            const size_t dst = (size_t)(ph[i].p_vaddr - low);
            std::memcpy(memory.data() + dst, data + ph[i].p_offset, (size_t)ph[i].p_filesz);
        }
    }
    uint64_t regs[16] = {};
    uint64_t pc = eh->e_entry - low, executed = 0;
    constexpr uint64_t limit = 250000;
    while (pc < memory.size() && executed < limit) {
        uint8_t opcode = memory[(size_t)pc++]; ++executed;
        if (opcode == 0x90) continue;
        if (opcode == 0xC3 || opcode == 0xF4) {
            std::ostringstream out;
            out << "Execution stopped normally.\nInstructions: " << executed
                << "\nRAX: 0x" << std::hex << regs[0] << "\nLoaded image: " << std::dec << span << " bytes";
            return out.str();
        }
        if (opcode == 0xCC) return "ELF reached INT3 breakpoint.";
        uint8_t rex = 0;
        if (opcode >= 0x40 && opcode <= 0x4F) {
            rex = opcode;
            if (pc >= memory.size()) return "Truncated REX instruction.";
            opcode = memory[(size_t)pc++]; ++executed;
        }
        if ((opcode & 0xF8) == 0xB8 && (rex & 8)) {
            if (pc + 8 > memory.size()) return "Truncated MOV immediate.";
            int r = (opcode & 7) | ((rex & 1) ? 8 : 0);
            uint64_t value = 0; std::memcpy(&value, memory.data() + pc, 8); pc += 8; regs[r] = value; continue;
        }
        if ((opcode == 0x31 || opcode == 0x33) && (rex & 8)) {
            if (pc >= memory.size()) return "Truncated XOR.";
            uint8_t m = memory[(size_t)pc++]; ++executed;
            if ((m >> 6) != 3) return "XOR memory operands are not implemented.";
            int r = ((m >> 3) & 7) | ((rex & 4) ? 8 : 0), rm = (m & 7) | ((rex & 1) ? 8 : 0);
            if (opcode == 0x31) regs[rm] ^= regs[r]; else regs[r] ^= regs[rm];
            continue;
        }
        if ((opcode == 0x05 || opcode == 0x2D) && (rex & 8)) {
            if (pc + 4 > memory.size()) return "Truncated arithmetic immediate.";
            int32_t imm = 0; std::memcpy(&imm, memory.data() + pc, 4); pc += 4;
            if (opcode == 0x05) regs[0] += (int64_t)imm; else regs[0] -= (int64_t)imm;
            continue;
        }
        if (opcode == 0x89 || opcode == 0x8B) {
            if (pc >= memory.size()) return "Truncated MOV.";
            uint8_t m = memory[(size_t)pc++]; ++executed;
            if ((m >> 6) != 3) return "MOV memory operands are not implemented.";
            int r = ((m >> 3) & 7) | ((rex & 4) ? 8 : 0), rm = (m & 7) | ((rex & 1) ? 8 : 0);
            if (opcode == 0x89) regs[rm] = regs[r]; else regs[r] = regs[rm];
            continue;
        }
        std::ostringstream out;
        out << "Unsupported opcode 0x" << std::hex << (int)opcode << " at guest offset 0x" << (pc - 1)
            << ".\nThis instruction is not implemented in PlayBox v0.1.\nInstructions executed: " << std::dec << executed;
        return out.str();
    }
    if (executed >= limit) return "Stopped at the 250,000-instruction safety limit.";
    return "Instruction pointer left the loaded ELF image.";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_playbox_elf_MainActivity_nativeRunElf(JNIEnv* env, jobject, jbyteArray bytes, jint ramMb) {
    if (!bytes) return env->NewStringUTF("No ELF data.");
    jsize length = env->GetArrayLength(bytes);
    if (length <= 0 || length > 128 * 1024 * 1024) return env->NewStringUTF("ELF must be between 1 byte and 128 MiB.");
    std::vector<uint8_t> input((size_t)length);
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(input.data()));
    const std::string result = run_elf(input.data(), input.size(), (int)ramMb);
    return env->NewStringUTF(result.c_str());
}
