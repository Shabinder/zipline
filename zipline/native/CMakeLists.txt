cmake_minimum_required(VERSION 3.22)

string(REPLACE \\ / CURRENT_DIR ${CMAKE_CURRENT_LIST_DIR})

# Just For windows until https://github.com/ziglang/zig/issues/19746 gets fixed.
#cmake ./ -B build/windows_x64 -DCMAKE_BUILD_TYPE=MinSizeRel -DTARGET_PLATFORM=windows_x64 -DBUILD_WITH_JNI=ON -DLIBRARY_TYPE=shared -G Ninja -DPLATFORM_JAVA_HOME=$JAVA_HOME_WINDOWS_X64
#cmake --build ./build/windows_x64
set(CMAKE_TOOLCHAIN_FILE "${CURRENT_DIR}/cmake/zig-toolchain-windows_x64.cmake")

function(configure_jni include_sub_dir)
  if (NOT DEFINED PLATFORM_JAVA_HOME)
    message(FATAL_ERROR "'PLATFORM_JAVA_HOME' is no set!")
    message()
  endif ()
  string(REPLACE \\ / PLATFORM_JAVA_HOME ${PLATFORM_JAVA_HOME})
  message("PLATFORM_JAVA_HOME: ${PLATFORM_JAVA_HOME}")
  set(JAVA_HOME ${PLATFORM_JAVA_HOME})
  set(ENV{JAVA_HOME} "${JAVA_HOME}")
  message("include_sub_dir: ${JAVA_HOME}/include ${JAVA_HOME}/include/${include_sub_dir}")
  include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/${include_sub_dir})
  set(JAVA_AWT_LIBRARY NotNeeded)
  set(JAVA_JVM_LIBRARY NotNeeded)
  find_package(JNI REQUIRED)
endfunction()


project(quickjs)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")

# QuickJS version define, Gradle cmake cFlags doesn't work on Windows (maybe)
#file(READ "quickjs/VERSION" VERSION_CONTENT)
string(STRIP "2021-03-27" CONFIG_VERSION)
add_compile_definitions(CONFIG_VERSION=\"${CONFIG_VERSION}\")

include_directories("quickjs" "common" ".")


file(GLOB_RECURSE sources
        "quickjs/*.c"
        "common/*.c"
        "*.cpp"
)

configure_jni("win32")
add_compile_definitions(CONFIG_WIN32)

include_directories("winpthreads/include")

# pthread for Windows
file(GLOB_RECURSE pthread_sources
        "winpthreads/src/*.c"
        "winpthreads/src/*.S"
)
list(APPEND sources ${pthread_sources})


add_library(quickjs SHARED ${sources})
#target_compile_definitions(quickjs PUBLIC CONFIG_VERSION="${QUICKJS_VERSION}")

target_link_libraries(quickjs)
