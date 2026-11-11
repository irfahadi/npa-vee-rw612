# CROSS COMPILER SETTING
SET(CMAKE_SYSTEM_NAME Generic)
CMAKE_MINIMUM_REQUIRED (VERSION 3.27.0)

# ENABLE ASM
ENABLE_LANGUAGE(ASM)

SET(CMAKE_STATIC_LIBRARY_PREFIX)
SET(CMAKE_STATIC_LIBRARY_SUFFIX)

SET(CMAKE_EXECUTABLE_LIBRARY_PREFIX)
SET(CMAKE_EXECUTABLE_LIBRARY_SUFFIX)

# CURRENT DIRECTORY
SET(ProjDirPath ${CMAKE_CURRENT_SOURCE_DIR})

find_package(Git)

project(npavee)

file (STRINGS "${ProjDirPath}/../../../nvee_version.txt" VEE_VERSION)


set(MCUX_SDK_PROJECT_NAME npavee.elf)
cmake_path(GET MCUX_SDK_PROJECT_NAME STEM MCUX_SDK_PROJECT_NAME_NO_EXT)

if (NOT DEFINED MicroEjRootDirPath)
    SET(MicroEjRootDirPath ${ProjDirPath}/../../../vee/)
endif()

if (NOT DEFINED SdkOverlayRootDirPath)
    SET(SdkOverlayRootDirPath ${ProjDirPath}/../../../sdk_overlay/)
endif()

if (NOT DEFINED VeeSdkRootDirPath)
    SET(VeeSdkRootDirPath ${ProjDirPath}/../../../mcux-sdk/core/)
endif()

if (NOT DEFINED SdkMiddlewareDirPath)
    SET(SdkMiddlewareDirPath ${ProjDirPath}/../../../mcux-sdk/middleware/)
endif()

if (NOT DEFINED RtosRootDirPath)
    SET(RtosRootDirPath ${SdkOverlayRootDirPath}/rtos/)
endif()

include(${ProjDirPath}/flags.cmake)

include(${ProjDirPath}/config.cmake)

#include(${SdkMiddlewareDirPath}/coreHTTP/httpFilePaths.cmake)

execute_process(COMMAND
  "${GIT_EXECUTABLE}" describe --always --abbrev=8 --dirty
  WORKING_DIRECTORY "${ProjDirPath}"
  OUTPUT_VARIABLE GIT_SHA_1
  ERROR_QUIET OUTPUT_STRIP_TRAILING_WHITESPACE)

configure_file(
    "${MicroEjRootDirPath}/src/main/tree_version.c.in"
    "${CMAKE_CURRENT_BINARY_DIR}/tree_version.c"
    @ONLY
)

set(MFlashRootDirPath "${MicroEjRootDirPath}/../mcux-sdk/core/components/flash/mflash")

add_executable(${MCUX_SDK_PROJECT_NAME}
    "${MicroEjRootDirPath}/src/main/npavee.c"
    "${MicroEjRootDirPath}/src/main/npavee.h"

    "${MicroEjRootDirPath}/src/bsp/lora/lora.c"
    "${MicroEjRootDirPath}/src/bsp/lora/lora.h"
    "${MicroEjRootDirPath}/src/bsp/lora/lora_native.c"

    "${MicroEjRootDirPath}/src/bsp/rtc/ds3231.c"
    "${MicroEjRootDirPath}/src/bsp/rtc/ds3231.h"
    "${MicroEjRootDirPath}/src/bsp/rtc/rtc_native.c"

    "${MicroEjRootDirPath}/src/bsp/wifi/wifi_credential_storage.c"
    "${MicroEjRootDirPath}/src/bsp/wifi/wifi_credential_storage.h"
    "${MicroEjRootDirPath}/src/bsp/wifi/wifi_credential_native.c"

    "${MFlashRootDirPath}/mflash_file.c"
    "${MFlashRootDirPath}/frdmrw612/mflash_drv.c"

    "${MicroEjRootDirPath}/src/bsp/FreeRTOSConfig.h"
    "${MicroEjRootDirPath}/src/bsp/lwipopts.h"
    "${MicroEjRootDirPath}/src/bsp/pin_mux.c"
    "${MicroEjRootDirPath}/src/bsp/pin_mux.h"
    "${MicroEjRootDirPath}/src/bsp/panel_func.c"
    "${MicroEjRootDirPath}/src/bsp/core_test_natives.c"
    "${MicroEjRootDirPath}/src/bsp/board.c"
    "${MicroEjRootDirPath}/src/bsp/board.h"
    "${MicroEjRootDirPath}/src/bsp/clock_config.c"
    "${MicroEjRootDirPath}/src/bsp/clock_config.h"
    "${MicroEjRootDirPath}/common/bsp/sdmmc/ffconf.h"
    "${MicroEjRootDirPath}/common/bsp/lpm/lpm.c"
    "${MicroEjRootDirPath}/common/bsp/lpm/lpm.h"
    "${MicroEjRootDirPath}/common/bsp/lpm/host_sleep.c"
    "${MicroEjRootDirPath}/common/bsp/lpm/host_sleep.h"
    "${MicroEjRootDirPath}/src/bsp/display_support.h"
    "${MicroEjRootDirPath}/src/bsp/display_support.c"
    "${MicroEjRootDirPath}/src/bsp/simple_gfx_app_imp.c"
    "${CMAKE_CURRENT_BINARY_DIR}/tree_version.c"
    "${ADDITIONAL_SRC}"
    "${JLINKSCRIPT_SRC}"
)

IF(NOT DEFINED MICROEJ_DEST_OVERRIDE)
    SET(MICROEJ_DEST ${MicroEjRootDirPath})
    SET(MICROEJ_DEST_LIB "/lib")
ELSE()
    SET(MICROEJ_DEST ${CMAKE_BINARY_DIR})
    SET(MICROEJ_DEST_LIB "")
ENDIF()

target_include_directories(${MCUX_SDK_PROJECT_NAME} PRIVATE
    ${MicroEjRootDirPath}/src/main
    ${MicroEjRootDirPath}/src/bsp

    ${MicroEjRootDirPath}/src/bsp/lora
    ${MicroEjRootDirPath}/src/bsp/rtc
    ${MicroEjRootDirPath}/src/bsp/wifi
    ${MFlashRootDirPath}
    ${MFlashRootDirPath}/frdmrw612

    ${MicroEjRootDirPath}/
    ${MICROEJ_DEST}/inc
    ${MicroEjRootDirPath}/port/core/inc
    ${MicroEjRootDirPath}/port/cpuload/inc
    ${MicroEjRootDirPath}/port/ecom-wifi/inc
    ${MicroEjRootDirPath}/port/net/inc
    ${MicroEjRootDirPath}/port/inc
    ${MicroEjRootDirPath}/port/inc/intern
    ${MicroEjRootDirPath}/port/security/inc
    ${MicroEjRootDirPath}/port/ssl/inc
    ${MicroEjRootDirPath}/port/trace/inc
    ${MicroEjRootDirPath}/port/ui/inc
    ${MicroEjRootDirPath}/port/util/inc
    ${HTTP_INCLUDE_PUBLIC_DIRS}
    ${MicroEjRootDirPath}/common/bsp/sdmmc
    ${MicroEjRootDirPath}/src/bsp
    ${MicroEjRootDirPath}/common/bsp/lpm

if (ENABLE_WIFI)
    ${MicroEjRootDirPath}/ecom-wifi/inc
endif (ENABLE_WIFI)
)

if (ENABLE_WIFI)

set_source_files_properties(
    "${MicroEjRootDirPath}/src/bsp/wifi_config.h"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_wifi_template"
)

set_source_files_properties(
    "${MicroEjRootDirPath}/src/bsp/lwiphooks.h"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_wifi_template"
)

endif (ENABLE_WIFI)

set_source_files_properties(
    "${MicroEjRootDirPath}/src/bsp/FreeRTOSConfig.h"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_freertos-kernel_template"
)

set_source_files_properties(
    "${MicroEjRootDirPath}/common/bsp/sdmmc/ffconf.h"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_fatfs_template_sd"
)

set_source_files_properties(
    "${MicroEjRootDirPath}/common/bsp/sdmmc/sdmmc_config.c"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_sdmmc_usdhc_template"
)

set_source_files_properties(
    "${MicroEjRootDirPath}/common/bsp/sdmmc/sdmmc_config.h"
    PROPERTIES COMPONENT_CONFIG_FILE "middleware_sdmmc_usdhc_template"
)

# mbedTLS Config
target_compile_definitions(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    MBEDTLS_CONFIG_FILE="microej_mbedtls_config.h"
)

set(CMAKE_MODULE_PATH
    ${MicroEjRootDirPath}/port/core
    ${MicroEjRootDirPath}/port/cpuload
    ${MicroEjRootDirPath}/port/ecom-network
    ${MicroEjRootDirPath}/port/ecom-network-lwip

if (ENABLE_WIFI)
    ${MicroEjRootDirPath}/port/ecom-wifi
    ${SdkOverlayRootDirPath}/middleware/wifi_nxp/
endif (ENABLE_WIFI)

    ${MicroEjRootDirPath}/port/net
    ${MicroEjRootDirPath}/port/security
    ${MicroEjRootDirPath}/port/ssl
    ${MicroEjRootDirPath}/port/trace
    ${MicroEjRootDirPath}/port/ui
    ${MicroEjRootDirPath}/port/util
    ${MicroEjRootDirPath}/common/bsp/sdmmc
    ${MicroEjRootDirPath}/common/segger
    ${RtosRootDirPath}/freertos/freertos-kernel
    ${SdkOverlayRootDirPath}/core/components/exception_handling
    ${SdkMiddlewareDirPath}/coreHTTP
)

set(CONFIG_USE_driver_flexcomm_i2c true)

include(${VeeSdkRootDirPath}/devices/RW612/all_lib_device.cmake)

include(microej_core)
include(microej_cpuload)
include(microej_security)
include(microej_ssl)
include(microej_util)
include(microej_ui)

include(npa_util)


if (ENABLE_WIFI)
    include(microej_ecom-wifi)
endif (ENABLE_WIFI)

include(microej_ecom-network)
include(microej_ecom-network-lwip)
include(microej_net)
include(microej_trace)
include(utility_segger)

SET(
    TARGET_LINK_SYSTEM_LIBRARIES
    "-lm -lc -lgcc -lnosys"
)

TARGET_LINK_LIBRARIES(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    -Wl,--start-group
)

target_link_libraries(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    ${TARGET_LINK_SYSTEM_LIBRARIES}
)

target_link_libraries(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    ${MICROEJ_DEST}${MICROEJ_DEST_LIB}/microejruntime.a
)

target_link_libraries(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    ${MICROEJ_DEST}${MICROEJ_DEST_LIB}/microejapp.o
)

TARGET_LINK_LIBRARIES(
    ${MCUX_SDK_PROJECT_NAME}
    PRIVATE
    -Wl,--end-group
)

add_custom_command(
    TARGET ${MCUX_SDK_PROJECT_NAME}
    POST_BUILD
    COMMAND ${CMAKE_OBJCOPY}
    -Obinary
    ./${MCUX_SDK_PROJECT_NAME}
    ./${MCUX_SDK_PROJECT_NAME_NO_EXT}.bin
)

add_custom_command(
    TARGET ${MCUX_SDK_PROJECT_NAME}
    POST_BUILD
    COMMAND ${CMAKE_OBJCOPY}
    -Oihex
    ./${MCUX_SDK_PROJECT_NAME}
    ./${MCUX_SDK_PROJECT_NAME_NO_EXT}.hex
)

set_target_properties(
    ${MCUX_SDK_PROJECT_NAME}
    PROPERTIES
    ADDITIONAL_CLEAN_FILES ./${MCUX_SDK_PROJECT_NAME_NO_EXT}.bin
)

set_target_properties(
    ${MCUX_SDK_PROJECT_NAME}
    PROPERTIES
    ADDITIONAL_CLEAN_FILES ./${MCUX_SDK_PROJECT_NAME_NO_EXT}.hex
)