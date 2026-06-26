cmake_minimum_required(VERSION 3.10.2)
project("yolo_ncnn")

# Set NCNN path
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-sdk/arm64-v8a/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(yolo_ncnn SHARED yolo_ncnn.cpp)

# Explicitly add include directories
target_include_directories(yolo_ncnn PRIVATE
    ${CMAKE_SOURCE_DIR}/ncnn-sdk/arm64-v8a/include
)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(yolo_ncnn
    ncnn
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)