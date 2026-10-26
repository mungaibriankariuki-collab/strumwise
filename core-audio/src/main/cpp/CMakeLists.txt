cmake_minimum_required(VERSION 3.22.1)
project(strumwise_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe comes from the com.google.oboe:oboe AAR through Prefab.
find_package(oboe REQUIRED CONFIG)

add_library(strumwise_audio SHARED
        GuitarEngine.cpp
        jni_bridge.cpp)

target_compile_options(strumwise_audio PRIVATE -Wall -Wextra -O3 -ffast-math)
target_link_libraries(strumwise_audio PRIVATE oboe::oboe log android)
