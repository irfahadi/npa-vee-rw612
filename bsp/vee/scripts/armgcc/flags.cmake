IF(NOT DEFINED FPU)
    SET(FPU "-mfloat-abi=hard -mfpu=fpv5-sp-d16")
ENDIF()

IF(NOT DEFINED SPECS)
    SET(SPECS "--specs=nano.specs --specs=nosys.specs")
ENDIF()

IF(NOT DEFINED DEBUG_CONSOLE_CONFIG)
    SET(DEBUG_CONSOLE_CONFIG "-DSDK_DEBUGCONSOLE=1")
ENDIF()

IF(ENABLE_SHELL)
    SET(SHELL_C_FLAGS " \
    -DENABLE_SHELL=1 \
    -DDEBUG_CONSOLE_TRANSFER_NON_BLOCKING \
    -DDEBUG_CONSOLE_RX_ENABLE=0 \
    -DSERIAL_PORT_TYPE_UART=1 \
    -DSHELL_PRINT_COPYRIGHT=0 \
")
ENDIF()

IF(ENABLE_WIFI)
    SET(ENABLE_WIFI_CFLAG "-DENABLE_WIFI")
ENDIF()

IF(ENABLE_SYSTEM_VIEW)
    SET(SYSTEM_VIEW_CFLAGS "-DENABLE_SYSTEM_VIEW=1")
ENDIF()

IF(ENABLE_ETHERNET)
    SET(ENABLE_ETHERNET_CFLAGS " \
    -DENABLE_ETHERNET \
    -DFSL_FEATURE_PHYKSZ8081_USE_RMII50M_MODE \
    ")
ENDIF()

SET(STACK_SZ 0x1000)
SET(HEAP_SZ 0x10000)

SET(CMAKE_ASM_FLAGS_DEBUG " \
    ${CMAKE_ASM_FLAGS_DEBUG} \
    -D__STARTUP_CLEAR_BSS \
    -mcpu=cortex-m33+nodsp \
    -mthumb \
    ${FPU} \
")
SET(CMAKE_ASM_FLAGS_RELEASE " \
    ${CMAKE_ASM_FLAGS_RELEASE} \
    -D__STARTUP_CLEAR_BSS \
    -mcpu=cortex-m33+nodsp \
    -mthumb \
    ${FPU} \
")
SET(CMAKE_C_FLAGS_DEBUG " \
    ${CMAKE_C_FLAGS_DEBUG} \
    -DDEBUG \
    -DFSL_SDK_DRIVER_QUICK_ACCESS_ENABLE=1 \
    -DLV_CONF_INCLUDE_SIMPLE=1 \
    -DMCUX_DBI_LEGACY=0 \
    -DILI9341_USE_DBI_IFACE=1 \
    -DFT6X06_USE_CMSIS_DRIVER=0 \
    -DCPU_RW612ETA2I \
    -DMCUXPRESSO_SDK \
    -DBOOT_HEADER_ENABLE=1 \
    -DSDK_I2C_BASED_COMPONENT_USED=1 \
    -DSDK_OS_FREE_RTOS \
    -DUSE_RTOS=1 \
    -DPRINTF_FLOAT_ENABLE=1 \
    -DPRINTF_ADVANCED_ENABLE=1 \
    -DSDK_BOARD_ID=\"RW612\" \
    -DMCUX_ENABLE_TRNG_AS_ENTROPY_SEED \
    -DMBEDTLS_MCUX_ELS_PKC_API \
    -DMBEDTLS_MCUX_USE_PKC \
    -DMBEDTLS_MCUX_ELS_API \
    -DMBEDTLS_MCUX_USE_ELS \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    -DSERIAL_PORT_TYPE_UART \
    -DFSL_OSA_TASK_ENABLE=1 \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    ${ENABLE_WIFI_CFLAG} \
    ${ENABLE_ETHERNET_CFLAGS} \
    ${SYSTEM_VIEW_CFLAGS} \
    -g \
    -O0 \
    -mcpu=cortex-m33+nodsp \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -mthumb \
    -mapcs \
    -std=gnu99 \
    -Wno-format \
    ${SHELL_C_FLAGS} \
    ${FPU} \
    ${DEBUG_CONSOLE_CONFIG} \
")
SET(CMAKE_C_FLAGS_RELEASE " \
    ${CMAKE_C_FLAGS_RELEASE} \
    -DNDEBUG \
    -DFSL_SDK_DRIVER_QUICK_ACCESS_ENABLE=1 \
    -DLV_CONF_INCLUDE_SIMPLE=1 \
    -DMCUX_DBI_LEGACY=0 \
    -DILI9341_USE_DBI_IFACE=1 \
    -DFT6X06_USE_CMSIS_DRIVER=0 \
    -DCPU_RW612ETA2I \
    -DMCUXPRESSO_SDK \
    -DBOOT_HEADER_ENABLE=1 \
    -DSDK_I2C_BASED_COMPONENT_USED=1 \
    -DSDK_OS_FREE_RTOS \
    -DMCUX_ENABLE_TRNG_AS_ENTROPY_SEED \
    -DMBEDTLS_MCUX_ELS_PKC_API \
    -DMBEDTLS_MCUX_USE_PKC \
    -DMBEDTLS_MCUX_ELS_API \
    -DMBEDTLS_MCUX_USE_ELS \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    -DUSE_RTOS=1 \
    -DSERIAL_PORT_TYPE_UART \
    -DFSL_OSA_TASK_ENABLE=1 \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    ${ENABLE_WIFI_CFLAG} \
    ${ENABLE_ETHERNET_CFLAGS} \
    ${SYSTEM_VIEW_CFLAGS} \
    -Os \
    -mcpu=cortex-m33+nodsp \
    -DSDK_BOARD_ID=\"RW612\" \
    -DPRINTF_FLOAT_ENABLE=1 \
    -DPRINTF_ADVANCED_ENABLE=1 \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -mthumb \
    -mapcs \
    -std=gnu99 \
    -Wno-format \
    ${SHELL_C_FLAGS} \
    ${FPU} \
    ${DEBUG_CONSOLE_CONFIG} \
")
SET(CMAKE_CXX_FLAGS_DEBUG " \
    ${CMAKE_CXX_FLAGS_DEBUG} \
    -DFSL_SDK_DRIVER_QUICK_ACCESS_ENABLE=1 \
    -DCPU_RW612ETA2I \
    -DMCUXPRESSO_SDK \
    -DBOOT_HEADER_ENABLE=1 \
    -DSERIAL_PORT_TYPE_UART \
    -DFSL_OSA_TASK_ENABLE=1 \
    -g \
    -O0 \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    -mcpu=cortex-m33+nodsp \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -mthumb \
    -mapcs \
    -fno-rtti \
    -fno-exceptions \
    -Wno-format \
    ${SHELL_C_FLAGS} \
    ${FPU} \
    ${DEBUG_CONSOLE_CONFIG} \
")
SET(CMAKE_CXX_FLAGS_RELEASE " \
    ${CMAKE_CXX_FLAGS_RELEASE} \
    -DFSL_SDK_DRIVER_QUICK_ACCESS_ENABLE=1 \
    -DCPU_RW612ETA2I \
    -DMCUXPRESSO_SDK \
    -DBOOT_HEADER_ENABLE=1 \
    -DSERIAL_PORT_TYPE_UART \
    -DFSL_OSA_TASK_ENABLE=1 \
    -Os \
    -DLWIP_TIMEVAL_PRIVATE=0 \
    -mcpu=cortex-m33+nodsp \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -mthumb \
    -mapcs \
    -fno-rtti \
    -fno-exceptions \
    -Wno-format \
    ${SHELL_C_FLAGS} \
    ${FPU} \
    ${DEBUG_CONSOLE_CONFIG} \
")
SET(CMAKE_EXE_LINKER_FLAGS_DEBUG " \
    ${CMAKE_EXE_LINKER_FLAGS_DEBUG} \
    -g \
    -mcpu=cortex-m33+nodsp \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -u _printf_float \
    -mthumb \
    -mapcs \
    -Xlinker \
    --gc-sections \
    -Xlinker \
    -static \
    -Xlinker \
    -z \
    -Xlinker \
    muldefs \
    -Xlinker \
    --defsym=__stack_size__=${STACK_SZ} \
    -Xlinker \
    --defsym=__heap_size__=${HEAP_SZ} \
    -Xlinker \
    -Map=output.map \
    -Wl,--print-memory-usage \
    ${FPU} \
    ${SPECS} \
    -T\"${ProjDirPath}/RW612_flash.ld\" -static \
")
SET(CMAKE_EXE_LINKER_FLAGS_RELEASE " \
    ${CMAKE_EXE_LINKER_FLAGS_RELEASE} \
    -mcpu=cortex-m33+nodsp \
    -Wall \
    -fno-common \
    -ffunction-sections \
    -fdata-sections \
    -fno-builtin \
    -u _printf_float \
    -mthumb \
    -mapcs \
    -Xlinker \
    --gc-sections \
    -Xlinker \
    -static \
    -Xlinker \
    -z \
    -Xlinker \
    muldefs \
    -Xlinker \
    --defsym=__stack_size__=${STACK_SZ} \
    -Xlinker \
    --defsym=__heap_size__=${HEAP_SZ} \
    -Xlinker \
    -Map=output.map \
    -Wl,--print-memory-usage \
    ${FPU} \
    ${SPECS} \
    -T\"${ProjDirPath}/RW612_flash.ld\" -static \
")
