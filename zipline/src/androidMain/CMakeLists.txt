cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

file(GLOB_RECURSE sources "../../native/*.c" "../../native/*.cpp")
file(GLOB_RECURSE pthread_sources "../../native/winpthreads/*.c")

# Remove pthread sources from main sources list.
list(REMOVE_ITEM sources ${pthread_sources})

add_library(quickjs SHARED ${sources})

target_link_libraries(quickjs)

target_link_options(quickjs PRIVATE "-Wl,-z,max-page-size=16384")
