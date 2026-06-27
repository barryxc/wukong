#pragma once

#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <cstdio>
#include <string>
#include <unordered_map>

#define LOG_TAG "WuKongNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace wukong {

using PropertyGet = int (*)(const char*, char*);
using LibcutilsPropertyGet = int (*)(const char*, char*, const char*);
using PropertyFind = const prop_info* (*)(const char*);
using PropertyRead = int (*)(const prop_info*, char*, char*);
using PropertyReadCallback = void (*)(const prop_info*, void (*)(void*, const char*, const char*, uint32_t), void*);
using Dlopen = void* (*)(const char*, int);
using AndroidDlopenExt = void* (*)(const char*, int, const android_dlextinfo*);
using Dlsym = void* (*)(void*, const char*);
using Dlvsym = void* (*)(void*, const char*, const char*);
using LoaderDlsym = void* (*)(void*, const char*, const void*);
using Open = int (*)(const char*, int, ...);
using OpenAt = int (*)(int, const char*, int, ...);
using Access = int (*)(const char*, int);
using Stat = int (*)(const char*, struct stat*);
using Readlink = ssize_t (*)(const char*, char*, size_t);
using Fopen = FILE* (*)(const char*, const char*);
using System = int (*)(const char*);
using Popen = FILE* (*)(const char*, const char*);

extern pthread_mutex_t g_lock;
extern std::unordered_map<std::string, std::string> g_properties;
extern std::unordered_map<const prop_info*, std::string> g_prop_names;
extern std::unordered_map<std::string, const prop_info*> g_fake_prop_infos;

extern PropertyGet g_real_property_get;
extern LibcutilsPropertyGet g_real_libcutils_property_get;
extern PropertyFind g_real_property_find;
extern PropertyRead g_real_property_read;
extern PropertyReadCallback g_real_property_read_callback;
extern Dlopen g_real_dlopen;
extern AndroidDlopenExt g_real_android_dlopen_ext;
extern Dlsym g_real_dlsym;
extern Dlvsym g_real_dlvsym;
extern LoaderDlsym g_real_loader_dlsym;
extern Open g_real_open;
extern OpenAt g_real_openat;
extern Access g_real_access;
extern Stat g_real_stat;
extern Stat g_real_lstat;
extern Readlink g_real_readlink;
extern Fopen g_real_fopen;
extern System g_real_system;
extern Popen g_real_popen;

void load_originals();
void install_hooks();
bool is_art_debug_lib(const char* name);
bool should_bypass_debugger_hook(const void* caller_addr);

std::string fake_property_value(const char* key);
std::string fake_property_value(const prop_info* info);
void cache_prop_name(const prop_info* info, const char* name);
const prop_info* fake_prop_info_for(const char* name);
void clear_fake_prop_infos_locked();

int hooked_system_property_get(const char* name, char* value);
int hooked_libcutils_property_get(const char* name, char* value, const char* default_value);
const prop_info* hooked_system_property_find(const char* name);
int hooked_system_property_read(const prop_info* info, char* name, char* value);
void hooked_system_property_read_callback(
        const prop_info* info,
        void (*callback)(void*, const char*, const char*, uint32_t),
        void* cookie);
void* replacement_for_dlsym(const char* symbol);

void log_suspicious_path(const char* api, const char* path);
void log_suspicious_command(const char* api, const char* command);
int hooked_open(const char* pathname, int flags, ...);
int hooked_openat(int dirfd, const char* pathname, int flags, ...);
int hooked_access(const char* pathname, int mode);
int hooked_stat(const char* pathname, struct stat* statbuf);
int hooked_lstat(const char* pathname, struct stat* statbuf);
ssize_t hooked_readlink(const char* pathname, char* buf, size_t bufsiz);
FILE* hooked_fopen(const char* pathname, const char* mode);
int hooked_system(const char* command);
FILE* hooked_popen(const char* command, const char* type);

void* hooked_dlopen(const char* filename, int flags);
void* hooked_android_dlopen_ext(const char* filename, int flags, const android_dlextinfo* extinfo);
void* hooked_dlsym(void* handle, const char* symbol);
void* hooked_dlvsym(void* handle, const char* symbol, const char* version);
void* hooked_loader_dlsym(void* handle, const char* symbol, const void* caller_addr);

}  // namespace wukong
