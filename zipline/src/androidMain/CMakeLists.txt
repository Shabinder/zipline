cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)


# QuickJS sources
file(GLOB_RECURSE sources
        "../../native/quickjs/cutils.c"
        "../../native/quickjs/libbf.c"
        "../../native/quickjs/libregexp.c"
        "../../native/quickjs/libunicode.c"
        "../../native/quickjs/quickjs.c"
        "../../native/common/*.c"
        "../../native/*.cpp"
)

add_library(quickjs SHARED ${sources})

target_link_libraries(quickjs)

target_link_options(quickjs PRIVATE "-Wl,-z,max-page-size=16384")
