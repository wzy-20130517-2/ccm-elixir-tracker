cmake_minimum_required(VERSION 3.22.1)
project(crexec C)

# 产物本质是 PIE 可执行文件，但命名为 lib*.so 以便被 Gradle 打进 APK。
# 运行时由 Kotlin 复制到 /data/local/tmp 后 chmod +x 执行。
add_executable(crexec crexec.c)
set_target_properties(crexec PROPERTIES
        OUTPUT_NAME "crexec"
        SUFFIX ".so"
        PREFIX "lib"
        POSITION_INDEPENDENT_CODE ON)
target_compile_options(crexec PRIVATE -O2 -Wall)
target_link_options(crexec PRIVATE -pie)

# elixir 定位：开机后找一次圣水地址
add_executable(elixir_find elixir_find.c)
set_target_properties(elixir_find PROPERTIES
        OUTPUT_NAME "elixir_find"
        SUFFIX ".so"
        PREFIX "lib"
        POSITION_INDEPENDENT_CODE ON)
target_compile_options(elixir_find PRIVATE -O2 -Wall)
target_link_options(elixir_find PRIVATE -pie)
# rd4：读单个 i32（圣水轮询专用，~30ms）
add_executable(rd4 rd4.c)
set_target_properties(rd4 PROPERTIES
        OUTPUT_NAME "rd4"
        SUFFIX ".so"
        PREFIX "lib"
        POSITION_INDEPENDENT_CODE ON)
target_compile_options(rd4 PRIVATE -O2 -Wall)
target_link_options(rd4 PRIVATE -pie)
