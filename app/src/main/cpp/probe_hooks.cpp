#include "native_common.h"

#include <cstdarg>
#include <cstring>

namespace wukong {

namespace {

bool contains_token(const char* value, const char* token) {
    return value != nullptr && token != nullptr && std::strstr(value, token) != nullptr;
}

bool is_suspicious_path(const char* path) {
    if (path == nullptr || path[0] == '\0') {
        return false;
    }
    const char* tokens[] = {
            "build.prop",
            "/proc/self/maps",
            "/proc/self/status",
            "/proc/self/cmdline",
            "/proc/self/mounts",
            "/proc/self/task",
            "/proc/self/fd",
            "/proc/net/",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk",
            "zygisk",
            "riru",
            "lsposed",
            "xposed",
            "frida",
            "substrate",
            "libwukong",
    };
    for (const char* token : tokens) {
        if (contains_token(path, token)) {
            return true;
        }
    }
    return false;
}

bool is_suspicious_command(const char* command) {
    if (command == nullptr || command[0] == '\0') {
        return false;
    }
    const char* tokens[] = {
            "getprop",
            "build.prop",
            " ro.product.",
            " ro.build.",
            "which su",
            "/su",
            "magisk",
            "zygisk",
            "riru",
            "lsposed",
            "xposed",
            "frida",
    };
    for (const char* token : tokens) {
        if (contains_token(command, token)) {
            return true;
        }
    }
    return false;
}

}  // namespace

void log_suspicious_path(const char* api, const char* path) {
    if (is_suspicious_path(path)) {
        LOGI("probe %s path=%s", api, path);
    }
}

void log_suspicious_command(const char* api, const char* command) {
    if (is_suspicious_command(command)) {
        LOGI("probe %s command=%s", api, command);
    }
}

int hooked_open(const char* pathname, int flags, ...) {
    log_suspicious_path("open", pathname);
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        return g_real_open == nullptr ? -1 : g_real_open(pathname, flags, mode);
    }
    return g_real_open == nullptr ? -1 : g_real_open(pathname, flags);
}

int hooked_openat(int dirfd, const char* pathname, int flags, ...) {
    log_suspicious_path("openat", pathname);
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        return g_real_openat == nullptr ? -1 : g_real_openat(dirfd, pathname, flags, mode);
    }
    return g_real_openat == nullptr ? -1 : g_real_openat(dirfd, pathname, flags);
}

int hooked_access(const char* pathname, int mode) {
    log_suspicious_path("access", pathname);
    return g_real_access == nullptr ? -1 : g_real_access(pathname, mode);
}

int hooked_stat(const char* pathname, struct stat* statbuf) {
    log_suspicious_path("stat", pathname);
    return g_real_stat == nullptr ? -1 : g_real_stat(pathname, statbuf);
}

int hooked_lstat(const char* pathname, struct stat* statbuf) {
    log_suspicious_path("lstat", pathname);
    return g_real_lstat == nullptr ? -1 : g_real_lstat(pathname, statbuf);
}

ssize_t hooked_readlink(const char* pathname, char* buf, size_t bufsiz) {
    log_suspicious_path("readlink", pathname);
    return g_real_readlink == nullptr ? -1 : g_real_readlink(pathname, buf, bufsiz);
}

FILE* hooked_fopen(const char* pathname, const char* mode) {
    log_suspicious_path("fopen", pathname);
    return g_real_fopen == nullptr ? nullptr : g_real_fopen(pathname, mode);
}

int hooked_system(const char* command) {
    log_suspicious_command("system", command);
    return g_real_system == nullptr ? -1 : g_real_system(command);
}

FILE* hooked_popen(const char* command, const char* type) {
    log_suspicious_command("popen", command);
    return g_real_popen == nullptr ? nullptr : g_real_popen(command, type);
}

}  // namespace wukong
