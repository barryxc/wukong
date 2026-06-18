#include "native_common.h"

#include <elf.h>
#include <sys/mman.h>

#include <cstring>

#if defined(__LP64__)
#define WK_ELF_R_TYPE ELF64_R_TYPE
#define WK_ELF_R_SYM ELF64_R_SYM
#else
#define WK_ELF_R_TYPE ELF32_R_TYPE
#define WK_ELF_R_SYM ELF32_R_SYM
#endif

#if defined(__aarch64__)
#define WK_R_JUMP_SLOT R_AARCH64_JUMP_SLOT
#define WK_R_GLOB_DAT R_AARCH64_GLOB_DAT
#elif defined(__arm__)
#define WK_R_JUMP_SLOT R_ARM_JUMP_SLOT
#define WK_R_GLOB_DAT R_ARM_GLOB_DAT
#elif defined(__i386__)
#define WK_R_JUMP_SLOT R_386_JMP_SLOT
#define WK_R_GLOB_DAT R_386_GLOB_DAT
#elif defined(__x86_64__)
#define WK_R_JUMP_SLOT R_X86_64_JUMP_SLOT
#define WK_R_GLOB_DAT R_X86_64_GLOB_DAT
#endif

namespace wukong {

namespace {

void* page_start(void* address) {
    const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
    const auto value = reinterpret_cast<uintptr_t>(address);
    return reinterpret_cast<void*>(value & ~(page_size - 1));
}

bool make_writable(void* address) {
    const auto page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    return mprotect(page_start(address), page_size, PROT_READ | PROT_WRITE) == 0;
}

void* replacement_for(const char* symbol) {
    if (symbol == nullptr) {
        return nullptr;
    }
    if (std::strcmp(symbol, "__system_property_get") == 0) {
        return reinterpret_cast<void*>(hooked_system_property_get);
    }
    if (std::strcmp(symbol, "property_get") == 0) {
        return reinterpret_cast<void*>(hooked_libcutils_property_get);
    }
    if (std::strcmp(symbol, "__system_property_find") == 0) {
        return reinterpret_cast<void*>(hooked_system_property_find);
    }
    if (std::strcmp(symbol, "__system_property_read") == 0) {
        return reinterpret_cast<void*>(hooked_system_property_read);
    }
    if (std::strcmp(symbol, "__system_property_read_callback") == 0) {
        return reinterpret_cast<void*>(hooked_system_property_read_callback);
    }
    if (std::strcmp(symbol, "dlopen") == 0) {
        return reinterpret_cast<void*>(hooked_dlopen);
    }
    if (std::strcmp(symbol, "android_dlopen_ext") == 0) {
        return reinterpret_cast<void*>(hooked_android_dlopen_ext);
    }
    if (std::strcmp(symbol, "dlsym") == 0) {
        return reinterpret_cast<void*>(hooked_dlsym);
    }
    if (std::strcmp(symbol, "dlvsym") == 0) {
        return reinterpret_cast<void*>(hooked_dlvsym);
    }
    if (std::strcmp(symbol, "__loader_dlsym") == 0) {
        return reinterpret_cast<void*>(hooked_loader_dlsym);
    }
    if (std::strcmp(symbol, "open") == 0) {
        return reinterpret_cast<void*>(hooked_open);
    }
    if (std::strcmp(symbol, "openat") == 0) {
        return reinterpret_cast<void*>(hooked_openat);
    }
    if (std::strcmp(symbol, "access") == 0) {
        return reinterpret_cast<void*>(hooked_access);
    }
    if (std::strcmp(symbol, "stat") == 0) {
        return reinterpret_cast<void*>(hooked_stat);
    }
    if (std::strcmp(symbol, "lstat") == 0) {
        return reinterpret_cast<void*>(hooked_lstat);
    }
    if (std::strcmp(symbol, "readlink") == 0) {
        return reinterpret_cast<void*>(hooked_readlink);
    }
    if (std::strcmp(symbol, "fopen") == 0) {
        return reinterpret_cast<void*>(hooked_fopen);
    }
    if (std::strcmp(symbol, "system") == 0) {
        return reinterpret_cast<void*>(hooked_system);
    }
    if (std::strcmp(symbol, "popen") == 0) {
        return reinterpret_cast<void*>(hooked_popen);
    }
    return nullptr;
}

bool should_skip_object(const char* name) {
    if (name == nullptr || name[0] == '\0') {
        return true;
    }
    return std::strstr(name, "libwukong_native.so") != nullptr
        || std::strstr(name, "/linker") != nullptr
        || std::strstr(name, "/libc.so") != nullptr;
}

template <typename Relocation>
void hook_relocations(
        const char* object_name,
        ElfW(Addr) base,
        Relocation* relocations,
        size_t count,
        ElfW(Sym)* symbols,
        const char* strings) {
    if (relocations == nullptr || symbols == nullptr || strings == nullptr) {
        return;
    }
    for (size_t i = 0; i < count; ++i) {
        const auto info = relocations[i].r_info;
        const auto type = WK_ELF_R_TYPE(info);
        if (type != WK_R_JUMP_SLOT && type != WK_R_GLOB_DAT) {
            continue;
        }
        const auto symbol_index = WK_ELF_R_SYM(info);
        const char* symbol_name = strings + symbols[symbol_index].st_name;
        void* replacement = replacement_for(symbol_name);
        if (replacement == nullptr) {
            continue;
        }
        auto** target = reinterpret_cast<void**>(base + relocations[i].r_offset);
        if (*target == replacement) {
            continue;
        }
        if (!make_writable(target)) {
            LOGE("mprotect failed for %s %s", object_name, symbol_name);
            continue;
        }
        *target = replacement;
        __builtin___clear_cache(reinterpret_cast<char*>(target), reinterpret_cast<char*>(target + 1));
        LOGI("hooked %s in %s", symbol_name, object_name);
    }
}

uintptr_t dyn_addr(ElfW(Addr) base, ElfW(Addr) value) {
    if (value == 0) {
        return 0;
    }
    if (value >= base) {
        return static_cast<uintptr_t>(value);
    }
    return static_cast<uintptr_t>(base + value);
}

int hook_loaded_object(dl_phdr_info* info, size_t, void*) {
    const char* object_name = info->dlpi_name;
    if (should_skip_object(object_name)) {
        return 0;
    }
    ElfW(Dyn)* dynamic = nullptr;
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const auto& phdr = info->dlpi_phdr[i];
        if (phdr.p_type == PT_DYNAMIC) {
            dynamic = reinterpret_cast<ElfW(Dyn)*>(info->dlpi_addr + phdr.p_vaddr);
            break;
        }
    }
    if (dynamic == nullptr) {
        return 0;
    }

    ElfW(Sym)* symbols = nullptr;
    const char* strings = nullptr;
    ElfW(Rela)* rela = nullptr;
    size_t rela_count = 0;
    ElfW(Rel)* rel = nullptr;
    size_t rel_count = 0;
    void* plt_relocations = nullptr;
    size_t plt_size = 0;
    bool plt_is_rela = true;

    for (ElfW(Dyn)* entry = dynamic; entry->d_tag != DT_NULL; ++entry) {
        switch (entry->d_tag) {
            case DT_SYMTAB:
                symbols = reinterpret_cast<ElfW(Sym)*>(dyn_addr(info->dlpi_addr, entry->d_un.d_ptr));
                break;
            case DT_STRTAB:
                strings = reinterpret_cast<const char*>(dyn_addr(info->dlpi_addr, entry->d_un.d_ptr));
                break;
            case DT_RELA:
                rela = reinterpret_cast<ElfW(Rela)*>(dyn_addr(info->dlpi_addr, entry->d_un.d_ptr));
                break;
            case DT_RELASZ:
                rela_count = entry->d_un.d_val / sizeof(ElfW(Rela));
                break;
            case DT_REL:
                rel = reinterpret_cast<ElfW(Rel)*>(dyn_addr(info->dlpi_addr, entry->d_un.d_ptr));
                break;
            case DT_RELSZ:
                rel_count = entry->d_un.d_val / sizeof(ElfW(Rel));
                break;
            case DT_JMPREL:
                plt_relocations = reinterpret_cast<void*>(dyn_addr(info->dlpi_addr, entry->d_un.d_ptr));
                break;
            case DT_PLTRELSZ:
                plt_size = entry->d_un.d_val;
                break;
            case DT_PLTREL:
                plt_is_rela = entry->d_un.d_val == DT_RELA;
                break;
            default:
                break;
        }
    }

    hook_relocations(object_name, info->dlpi_addr, rela, rela_count, symbols, strings);
    hook_relocations(object_name, info->dlpi_addr, rel, rel_count, symbols, strings);
    if (plt_relocations != nullptr && plt_size > 0) {
        if (plt_is_rela) {
            hook_relocations(
                    object_name,
                    info->dlpi_addr,
                    reinterpret_cast<ElfW(Rela)*>(plt_relocations),
                    plt_size / sizeof(ElfW(Rela)),
                    symbols,
                    strings);
        } else {
            hook_relocations(
                    object_name,
                    info->dlpi_addr,
                    reinterpret_cast<ElfW(Rel)*>(plt_relocations),
                    plt_size / sizeof(ElfW(Rel)),
                    symbols,
                    strings);
        }
    }
    return 0;
}

}  // namespace

void install_hooks() {
    load_originals();
    dl_iterate_phdr(hook_loaded_object, nullptr);
}

void* hooked_dlopen(const char* filename, int flags) {
    void* result = g_real_dlopen == nullptr ? nullptr : g_real_dlopen(filename, flags);
    if (result != nullptr) {
        LOGI("dlopen loaded %s, rescan hooks", filename == nullptr ? "" : filename);
        install_hooks();
    }
    return result;
}

void* hooked_android_dlopen_ext(const char* filename, int flags, const android_dlextinfo* extinfo) {
    void* result = g_real_android_dlopen_ext == nullptr
            ? nullptr
            : g_real_android_dlopen_ext(filename, flags, extinfo);
    if (result != nullptr) {
        LOGI("android_dlopen_ext loaded %s, rescan hooks", filename == nullptr ? "" : filename);
        install_hooks();
    }
    return result;
}

void* hooked_dlsym(void* handle, const char* symbol) {
    if (void* replacement = replacement_for_dlsym(symbol)) {
        LOGI("dlsym hit %s", symbol);
        return replacement;
    }
    return g_real_dlsym == nullptr ? nullptr : g_real_dlsym(handle, symbol);
}

void* hooked_dlvsym(void* handle, const char* symbol, const char* version) {
    if (void* replacement = replacement_for_dlsym(symbol)) {
        LOGI("dlvsym hit %s", symbol);
        return replacement;
    }
    return g_real_dlvsym == nullptr ? nullptr : g_real_dlvsym(handle, symbol, version);
}

void* hooked_loader_dlsym(void* handle, const char* symbol, const void* caller_addr) {
    if (void* replacement = replacement_for_dlsym(symbol)) {
        LOGI("__loader_dlsym hit %s", symbol);
        return replacement;
    }
    return g_real_loader_dlsym == nullptr
            ? nullptr
            : g_real_loader_dlsym(handle, symbol, caller_addr);
}

}  // namespace wukong
