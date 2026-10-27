cmake_minimum_required(VERSION 3.5)
project(zipline_quickjs LANGUAGES C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

file(GLOB_RECURSE sources "../../native/*.c" "../../native/*.cpp")

add_library(quickjs SHARED ${sources})

target_link_libraries(quickjs)

target_link_options(quickjs PRIVATE "-Wl,-z,max-page-size=16384")
