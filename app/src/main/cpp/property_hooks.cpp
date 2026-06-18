#include "native_common.h"

#include <algorithm>
#include <cstdint>
#include <cstring>

namespace wukong {

namespace {

constexpr size_t kFakePropNameMax = 128;

struct FakePropInfo {
    uint32_t serial;
    char value[PROP_VALUE_MAX];
    char name[kFakePropNameMax];
};

void update_fake_prop_info(FakePropInfo* info, const char* name, const std::string& value) {
    if (info == nullptr) {
        return;
    }
    const size_t value_length = std::min(value.length(), static_cast<size_t>(PROP_VALUE_MAX - 1));
    info->serial = static_cast<uint32_t>(value_length << 24U);
    std::memset(info->value, 0, sizeof(info->value));
    std::memcpy(info->value, value.data(), value_length);
    std::memset(info->name, 0, sizeof(info->name));
    if (name != nullptr) {
        std::strncpy(info->name, name, sizeof(info->name) - 1);
    }
}

}  // namespace

std::string fake_property_value(const char* key) {
    if (key == nullptr) {
        return {};
    }
    pthread_mutex_lock(&g_lock);
    const auto item = g_properties.find(key);
    const auto value = item == g_properties.end() ? std::string() : item->second;
    pthread_mutex_unlock(&g_lock);
    return value;
}

std::string fake_property_value(const prop_info* info) {
    if (info == nullptr) {
        return {};
    }
    pthread_mutex_lock(&g_lock);
    const auto name = g_prop_names.find(info);
    const auto prop = name == g_prop_names.end() ? g_properties.end() : g_properties.find(name->second);
    const auto value = prop == g_properties.end() ? std::string() : prop->second;
    pthread_mutex_unlock(&g_lock);
    return value;
}

void cache_prop_name(const prop_info* info, const char* name) {
    if (info == nullptr || name == nullptr || name[0] == '\0') {
        return;
    }
    pthread_mutex_lock(&g_lock);
    if (g_properties.find(name) != g_properties.end()) {
        g_prop_names[info] = name;
    }
    pthread_mutex_unlock(&g_lock);
}

const prop_info* fake_prop_info_for(const char* name) {
    if (name == nullptr || name[0] == '\0') {
        return nullptr;
    }
    pthread_mutex_lock(&g_lock);
    const auto prop = g_properties.find(name);
    if (prop == g_properties.end()) {
        pthread_mutex_unlock(&g_lock);
        return nullptr;
    }
    auto item = g_fake_prop_infos.find(name);
    if (item == g_fake_prop_infos.end()) {
        auto* fake_info = new FakePropInfo();
        update_fake_prop_info(fake_info, name, prop->second);
        const prop_info* info = reinterpret_cast<const prop_info*>(fake_info);
        item = g_fake_prop_infos.emplace(name, info).first;
        g_prop_names[info] = name;
    } else {
        auto* fake_info = reinterpret_cast<FakePropInfo*>(const_cast<prop_info*>(item->second));
        update_fake_prop_info(fake_info, name, prop->second);
        g_prop_names[item->second] = name;
    }
    const prop_info* info = item->second;
    pthread_mutex_unlock(&g_lock);
    return info;
}

int hooked_system_property_get(const char* name, char* value) {
    const auto fake = fake_property_value(name);
    if (!fake.empty() && value != nullptr) {
        std::strncpy(value, fake.c_str(), PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        LOGI("__system_property_get hit %s=%s", name, value);
        return static_cast<int>(std::strlen(value));
    }
    return g_real_property_get == nullptr ? 0 : g_real_property_get(name, value);
}

int hooked_libcutils_property_get(const char* name, char* value, const char* default_value) {
    const auto fake = fake_property_value(name);
    if (!fake.empty() && value != nullptr) {
        std::strncpy(value, fake.c_str(), PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        LOGI("property_get hit %s=%s", name, value);
        return static_cast<int>(std::strlen(value));
    }
    if (g_real_libcutils_property_get != nullptr) {
        return g_real_libcutils_property_get(name, value, default_value);
    }
    if (default_value != nullptr && value != nullptr) {
        std::strncpy(value, default_value, PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        return static_cast<int>(std::strlen(value));
    }
    return 0;
}

const prop_info* hooked_system_property_find(const char* name) {
    if (!fake_property_value(name).empty()) {
        const prop_info* info = fake_prop_info_for(name);
        cache_prop_name(info, name);
        LOGI("__system_property_find hit %s", name);
        return info;
    }
    const prop_info* info = g_real_property_find == nullptr ? nullptr : g_real_property_find(name);
    return info;
}

int hooked_system_property_read(const prop_info* info, char* name, char* value) {
    const auto fake = fake_property_value(info);
    if (!fake.empty()) {
        pthread_mutex_lock(&g_lock);
        const auto cached_name = g_prop_names.find(info);
        const std::string prop_name = cached_name == g_prop_names.end() ? std::string() : cached_name->second;
        pthread_mutex_unlock(&g_lock);
        if (name != nullptr) {
            std::strncpy(name, prop_name.c_str(), PROP_NAME_MAX - 1);
            name[PROP_NAME_MAX - 1] = '\0';
        }
        if (value != nullptr) {
            std::strncpy(value, fake.c_str(), PROP_VALUE_MAX - 1);
            value[PROP_VALUE_MAX - 1] = '\0';
        }
        LOGI("__system_property_read hit %s=%s", prop_name.c_str(), fake.c_str());
        return static_cast<int>(std::min(fake.length(), static_cast<size_t>(PROP_VALUE_MAX - 1)));
    }
    const int result = g_real_property_read == nullptr ? 0 : g_real_property_read(info, name, value);
    if (name != nullptr && name[0] != '\0') {
        cache_prop_name(info, name);
    }
    return result;
}

void hooked_system_property_read_callback(
        const prop_info* info,
        void (*callback)(void*, const char*, const char*, uint32_t),
        void* cookie) {
    if (callback == nullptr) {
        return;
    }
    const auto fake = fake_property_value(info);
    if (!fake.empty()) {
        pthread_mutex_lock(&g_lock);
        const auto name = g_prop_names.find(info);
        const std::string prop_name = name == g_prop_names.end() ? std::string() : name->second;
        pthread_mutex_unlock(&g_lock);
        LOGI("__system_property_read_callback hit %s=%s", prop_name.c_str(), fake.c_str());
        callback(cookie, prop_name.c_str(), fake.c_str(), 0);
        return;
    }
    if (g_real_property_read_callback != nullptr) {
        g_real_property_read_callback(info, callback, cookie);
    }
}

void* replacement_for_dlsym(const char* symbol) {
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
    return nullptr;
}

}  // namespace wukong
