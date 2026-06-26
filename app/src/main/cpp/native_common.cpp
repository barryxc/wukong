#include "native_common.h"

#include <sys/prctl.h>

#include <cstring>

namespace wukong {

pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
std::unordered_map<std::string, std::string> g_properties;
std::unordered_map<const prop_info*, std::string> g_prop_names;
std::unordered_map<std::string, const prop_info*> g_fake_prop_infos;

PropertyGet g_real_property_get = nullptr;
LibcutilsPropertyGet g_real_libcutils_property_get = nullptr;
PropertyFind g_real_property_find = nullptr;
PropertyRead g_real_property_read = nullptr;
PropertyReadCallback g_real_property_read_callback = nullptr;
Dlopen g_real_dlopen = nullptr;
AndroidDlopenExt g_real_android_dlopen_ext = nullptr;
Dlsym g_real_dlsym = nullptr;
Dlvsym g_real_dlvsym = nullptr;
LoaderDlsym g_real_loader_dlsym = nullptr;
Open g_real_open = nullptr;
OpenAt g_real_openat = nullptr;
Access g_real_access = nullptr;
Stat g_real_stat = nullptr;
Stat g_real_lstat = nullptr;
Readlink g_real_readlink = nullptr;
Fopen g_real_fopen = nullptr;
System g_real_system = nullptr;
Popen g_real_popen = nullptr;

bool is_art_debug_lib(const char* name) {
    return name != nullptr
        && (std::strstr(name, "libjdwp.so") != nullptr
            || std::strstr(name, "libopenjdkjvmti.so") != nullptr
            || std::strstr(name, "libadbconnection.so") != nullptr
            || std::strstr(name, "libart.so") != nullptr
            || std::strstr(name, "libartbase.so") != nullptr
            || std::strstr(name, "libartpalette.so") != nullptr
            || std::strstr(name, "/apex/com.android.art/") != nullptr);
}

bool should_bypass_debugger_hook(const void* caller_addr) {
    char thread_name[16] = {};
    if (prctl(PR_GET_NAME, reinterpret_cast<unsigned long>(thread_name), 0, 0, 0) == 0
            && std::strstr(thread_name, "JDWP") != nullptr) {
        return true;
    }

    Dl_info info = {};
    return caller_addr != nullptr
        && dladdr(caller_addr, &info) != 0
        && is_art_debug_lib(info.dli_fname);
}

void load_originals() {
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc == nullptr) {
        libc = RTLD_NEXT;
    }
    void* libdl = dlopen("libdl.so", RTLD_NOW);
    if (libdl == nullptr) {
        libdl = RTLD_NEXT;
    }
    void* libcutils = dlopen("libcutils.so", RTLD_NOW);

    if (g_real_property_get == nullptr) {
        g_real_property_get = reinterpret_cast<PropertyGet>(dlsym(libc, "__system_property_get"));
    }
    if (g_real_libcutils_property_get == nullptr && libcutils != nullptr) {
        g_real_libcutils_property_get = reinterpret_cast<LibcutilsPropertyGet>(
                dlsym(libcutils, "property_get"));
    }
    if (g_real_property_find == nullptr) {
        g_real_property_find = reinterpret_cast<PropertyFind>(dlsym(libc, "__system_property_find"));
    }
    if (g_real_property_read == nullptr) {
        g_real_property_read = reinterpret_cast<PropertyRead>(dlsym(libc, "__system_property_read"));
    }
    if (g_real_property_read_callback == nullptr) {
        g_real_property_read_callback = reinterpret_cast<PropertyReadCallback>(
                dlsym(libc, "__system_property_read_callback"));
    }
    if (g_real_dlopen == nullptr) {
        g_real_dlopen = reinterpret_cast<Dlopen>(dlsym(libdl, "dlopen"));
    }
    if (g_real_android_dlopen_ext == nullptr) {
        g_real_android_dlopen_ext = reinterpret_cast<AndroidDlopenExt>(
                dlsym(libdl, "android_dlopen_ext"));
    }
    if (g_real_dlsym == nullptr) {
        g_real_dlsym = reinterpret_cast<Dlsym>(dlsym(libdl, "dlsym"));
    }
    if (g_real_dlvsym == nullptr) {
        g_real_dlvsym = reinterpret_cast<Dlvsym>(dlsym(libdl, "dlvsym"));
    }
    if (g_real_loader_dlsym == nullptr) {
        g_real_loader_dlsym = reinterpret_cast<LoaderDlsym>(dlsym(libdl, "__loader_dlsym"));
    }
    if (g_real_open == nullptr) {
        g_real_open = reinterpret_cast<Open>(dlsym(libc, "open"));
    }
    if (g_real_openat == nullptr) {
        g_real_openat = reinterpret_cast<OpenAt>(dlsym(libc, "openat"));
    }
    if (g_real_access == nullptr) {
        g_real_access = reinterpret_cast<Access>(dlsym(libc, "access"));
    }
    if (g_real_stat == nullptr) {
        g_real_stat = reinterpret_cast<Stat>(dlsym(libc, "stat"));
    }
    if (g_real_lstat == nullptr) {
        g_real_lstat = reinterpret_cast<Stat>(dlsym(libc, "lstat"));
    }
    if (g_real_readlink == nullptr) {
        g_real_readlink = reinterpret_cast<Readlink>(dlsym(libc, "readlink"));
    }
    if (g_real_fopen == nullptr) {
        g_real_fopen = reinterpret_cast<Fopen>(dlsym(libc, "fopen"));
    }
    if (g_real_system == nullptr) {
        g_real_system = reinterpret_cast<System>(dlsym(libc, "system"));
    }
    if (g_real_popen == nullptr) {
        g_real_popen = reinterpret_cast<Popen>(dlsym(libc, "popen"));
    }
}

}  // namespace wukong
