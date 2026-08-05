# SPDX-FileCopyrightText: Copyright 2025 crueter
# SPDX-License-Identifier: LGPL-3.0-or-later

## UseLTO ##

# Enable Interprocedural Optimization (IPO).
# Self-explanatory.

option(ENABLE_LTO "Enable Link-Time Optimization (LTO)" OFF)
set(LTO_MODE "auto" CACHE STRING "LTO implementation: auto, thin, or full")
set_property(CACHE LTO_MODE PROPERTY STRINGS auto thin full)

if (ENABLE_LTO)
    if (NOT LTO_MODE MATCHES "^(auto|thin|full)$")
        message(FATAL_ERROR "Unsupported LTO_MODE='${LTO_MODE}'. Use auto, thin, or full.")
    endif()

    include(CheckIPOSupported)
    check_ipo_supported(RESULT COMPILER_SUPPORTS_LTO)
    if(NOT COMPILER_SUPPORTS_LTO)
        message(FATAL_ERROR
        "Your compiler does not support interprocedural optimization"
        " (IPO). Disable ENABLE_LTO and try again.")
    endif()
    if (LTO_MODE STREQUAL "thin")
        if (NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
            message(FATAL_ERROR "ThinLTO requires Clang")
        endif()
        add_compile_options(-flto=thin)
        add_link_options(-flto=thin)
    elseif (LTO_MODE STREQUAL "full")
        if (CMAKE_CXX_COMPILER_ID MATCHES "Clang")
            add_compile_options(-flto=full)
            add_link_options(-flto=full)
        else()
            add_compile_options(-flto)
            add_link_options(-flto)
        endif()
    else()
        set(CMAKE_POLICY_DEFAULT_CMP0069 NEW)
        set(CMAKE_INTERPROCEDURAL_OPTIMIZATION ${COMPILER_SUPPORTS_LTO})
    endif()
endif()
