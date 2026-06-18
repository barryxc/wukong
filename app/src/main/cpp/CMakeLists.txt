cmake_minimum_required(VERSION 3.22.1)

project(wukong_native)

add_library(
        wukong_native
        SHARED
        linker_hooks.cpp
        native_common.cpp
        native_build_info_hook.cpp
        probe_hooks.cpp
        property_hooks.cpp
)

target_link_libraries(
        wukong_native
        android
        log
        dl
)
