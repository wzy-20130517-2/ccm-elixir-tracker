cmake_minimum_required(VERSION 3.22.1)
project(crexec C)

# 产物本质是 PIE 可执行文件，但命名为 lib*.so 以便被 Gradle 打进 APK。
# 运行时由 Kotlin 复制到私有目录后 chmod +x 执行。
add_executable(crexec crexec.c)
set_target_properties(crexec PROPERTIES
        OUTPUT_NAME "crexec"
        SUFFIX ".so"
        PREFIX "lib"
        POSITION_INDEPENDENT_CODE ON)
target_compile_options(crexec PRIVATE -O2 -Wall)
target_link_options(crexec PRIVATE -pie)
