# Custom FindOpenCL.cmake for Android builds
# Uses the OpenCL target created by OpenCL-ICD-Loader

if(TARGET OpenCL::OpenCL)
    set(OpenCL_FOUND TRUE)
    set(OpenCL_VERSION "3.0")
    set(OpenCL_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/OpenCL-Headers" CACHE PATH "OpenCL include directory")
    set(OpenCL_LIBRARIES OpenCL::OpenCL)
    set(OpenCL_LIBRARY OpenCL::OpenCL)
    return()
endif()

# If target doesn't exist yet, provide stub values - will be set after OpenCL-ICD-Loader is added
set(OpenCL_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/OpenCL-Headers" CACHE PATH "OpenCL include directory")

if(EXISTS "${OpenCL_INCLUDE_DIR}/CL/opencl.h")
    set(OpenCL_FOUND TRUE)
    set(OpenCL_VERSION "3.0")
else()
    set(OpenCL_FOUND FALSE)
endif()
