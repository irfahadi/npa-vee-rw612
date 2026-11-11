#include "FreeRTOS.h"
#include "task.h"
#include "queue.h"
#include "timers.h"

#include "fsl_device_registers.h"
#include "fsl_debug_console.h"
#include "pin_mux.h"
#include "clock_config.h"
#include "board.h"
#include "LLMJVM.h"
#include "LLBSP_impl.h"
#include "sni.h"

#include "fsl_inputmux.h"
#include "fsl_dma.h"
#include "fsl_gpio.h"

#include "fsl_io_mux.h"
#include "fsl_spi.h"
#include "fsl_wwdt.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "fsl_lcdic.h"
#include "fsl_lcdic_dma.h"
#include "panel_func.h"

#include "fsl_reset.h"
#include "display_support.h"

#include "cpuload.h"

#include <assert.h>
#include "els_pkc_mbedtls.h"

#include "tree_version.h"

#ifdef ENABLE_SYSTEM_VIEW
#include "SEGGER_SYSVIEW.h"
#include "SEGGER_RTT.h"
#include "npavee.h"
#endif

#ifdef ENABLE_WIFI
#include "fsl_power.h"
#endif

#include "shell.h"
#include "lora.h"

#define nxp_pa_task_PRIORITY (configMAX_PRIORITIES - 6)
#define APP_WATCHDOG_TIMEOUT_SECONDS 60U
#define APP_WATCHDOG_REFRESH_PERIOD_MS 5000U

/*
 * Maksimum jeda antar heartbeat dari aplikasi Java sebelum watchdog
 * berhenti disuapi. Harus lebih kecil dari APP_WATCHDOG_TIMEOUT_SECONDS
 * agar reset terjadi dalam waktu yang wajar.
 */
#define APP_WATCHDOG_HEARTBEAT_TIMEOUT_MS 30000U

/*
 * Masa tenggang setelah boot. VM, WiFi, dan UI butuh waktu untuk siap
 * sebelum heartbeat pertama datang. Tanpa ini board akan reset-loop.
 */
#define APP_WATCHDOG_STARTUP_GRACE_MS 120000U
#define APP_WATCHDOG_MAX_COUNT 0xFFFFFFU
#define APP_WATCHDOG_TASK_STACK_SIZE 512U
#define APP_WATCHDOG_TASK_PRIORITY (configMAX_PRIORITIES - 2)

static void nxp_pa_task(void *pvParameters);
static void APP_WatchdogTask(void *pvParameters);

static void BOARD_InitLcdicClock();
static void APP_InitWatchdog(void);

TaskHandle_t pvMicrojvmCreatedTask = NULL;
static volatile bool appWatchdogEnabled = false;
static volatile uint32_t appHeartbeatCounter = 0U;

int main(void)
{
    BOARD_InitBootPins();
    BOARD_InitBootClocks();

#ifdef ENABLE_ETHERNET
    CLOCK_EnableClock(kCLOCK_TddrMciEnetClk);
#endif

    BOARD_InitDebugConsole();

#ifdef ENABLE_WIFI
    RESET_PeripheralReset(kGDMA_RST_SHIFT_RSTn);

    POWER_ConfigCauInSleep(false);
    BOARD_InitSleepPinConfig();

#ifdef RW610
    POWER_PowerOffBle();
#endif

#endif

    BOARD_InitLcdicClock();

    CLOCK_AttachClk(kSFRO_to_FLEXCOMM2);

    GPIO_PortInit(GPIO, 0);
    GPIO_PortInit(GPIO, 1);

    INPUTMUX_Init(INPUTMUX);

    RESET_PeripheralReset(kINPUTMUX_RST_SHIFT_RSTn);

    INPUTMUX_AttachSignal(
        INPUTMUX,
        APP_LCD_TX_DMA_CH,
        kINPUTMUX_LcdTxRegToDmaSingleToDma0
    );

    INPUTMUX_EnableSignal(
        INPUTMUX,
        kINPUTMUX_Dmac0InputTriggerLcdTxRegToDmaSingleEna,
        true
    );

#if ENABLE_SYSTEM_VIEW == 1
    SEGGER_SYSVIEW_Conf();

    PRINTF(
        "SEGGER_RTT block address: %p\n",
        &(_SEGGER_RTT)
    );

    SEGGER_SYSVIEW_setMicroJVMTask(
        (U32)pvMicrojvmCreatedTask
    );
#endif

    if (xTaskCreate(
            nxp_pa_task,
            "NXP_PA_task",
            4 * 1024,
            NULL,
            nxp_pa_task_PRIORITY,
            &pvMicrojvmCreatedTask) != pdPASS)
    {
        PRINTF(
            "Task creation failed!.\r\n"
        );

        while (1)
            ;
    }

    LORA_Start();

    status_t status =
        CRYPTO_InitHardware();

    if (status != kStatus_Success)
    {
        PRINTF(
            "CRYPTO hardware initialization failed.\r\n"
        );

        while (1)
        {
        }
    }

    APP_InitWatchdog();

    vTaskStartScheduler();

    for (;;)
        ;
}

static void APP_InitWatchdog(void)
{
    wwdt_config_t config;
    uint32_t watchdogClockHz;
    uint64_t timeoutCount;

    CLOCK_AttachClk(kLPOSC_to_WDT0_CLK);
    watchdogClockHz = CLOCK_GetWdtClkFreq();

    if (watchdogClockHz == 0U)
    {
        PRINTF("[WATCHDOG] ERROR: WDT clock is unavailable.\r\n");
        return;
    }

    timeoutCount = ((uint64_t)watchdogClockHz * APP_WATCHDOG_TIMEOUT_SECONDS) / 4U;

    if (timeoutCount > APP_WATCHDOG_MAX_COUNT)
    {
        timeoutCount = APP_WATCHDOG_MAX_COUNT;
    }

    if (timeoutCount < 0xFFU)
    {
        timeoutCount = 0xFFU;
    }

    WWDT_GetDefaultConfig(&config);
    config.enableWwdt = true;
    config.enableWatchdogReset = true;
    config.enableWatchdogProtect = false;
    config.enableLockOscillator = false;
    config.windowValue = APP_WATCHDOG_MAX_COUNT;
    config.timeoutValue = (uint32_t)timeoutCount;
    config.warningValue = 0U;
    config.clockFreq_Hz = watchdogClockHz;

    WWDT_Init(WWDT0, &config);

    if (xTaskCreate(
            APP_WatchdogTask,
            "APP_Watchdog",
            APP_WATCHDOG_TASK_STACK_SIZE,
            NULL,
            APP_WATCHDOG_TASK_PRIORITY,
            NULL) != pdPASS)
    {
        PRINTF("[WATCHDOG] ERROR: Watchdog task creation failed.\r\n");
        return;
    }

    WWDT_Refresh(WWDT0);
    appWatchdogEnabled = true;

    PRINTF(
        "[WATCHDOG] Enabled | clock=%u Hz | timeoutCount=%u | target=%u s | refresh=%u ms\r\n",
        (unsigned int)watchdogClockHz,
        (unsigned int)timeoutCount,
        (unsigned int)APP_WATCHDOG_TIMEOUT_SECONDS,
        (unsigned int)APP_WATCHDOG_REFRESH_PERIOD_MS
    );
}

static void APP_WatchdogTask(void *pvParameters)
{
    uint32_t lastHeartbeatSeen;
    TickType_t lastHeartbeatTick;
    TickType_t startTick;
    bool starvationReported;

    (void)pvParameters;

    lastHeartbeatSeen = appHeartbeatCounter;
    startTick = xTaskGetTickCount();
    lastHeartbeatTick = startTick;
    starvationReported = false;

    for (;;)
    {
        uint32_t currentHeartbeat;
        TickType_t now;
        bool withinGracePeriod;
        bool applicationAlive;

        currentHeartbeat = appHeartbeatCounter;
        now = xTaskGetTickCount();

        if (currentHeartbeat != lastHeartbeatSeen)
        {
            lastHeartbeatSeen = currentHeartbeat;
            lastHeartbeatTick = now;
            starvationReported = false;
        }

        withinGracePeriod =
            ((now - startTick) <
             pdMS_TO_TICKS(APP_WATCHDOG_STARTUP_GRACE_MS));

        applicationAlive =
            ((now - lastHeartbeatTick) <
             pdMS_TO_TICKS(APP_WATCHDOG_HEARTBEAT_TIMEOUT_MS));

        if (appWatchdogEnabled)
        {
            if (withinGracePeriod || applicationAlive)
            {
                WWDT_Refresh(WWDT0);
            }
            else if (!starvationReported)
            {
                /*
                 * Aplikasi Java berhenti berdetak. Berhenti menyuapi WWDT
                 * dan biarkan hardware me-reset board.
                 */
                PRINTF(
                    "[WATCHDOG] No heartbeat from application"
                    " for %u ms. Allowing hardware reset.\r\n",
                    (unsigned int)APP_WATCHDOG_HEARTBEAT_TIMEOUT_MS
                );

                starvationReported = true;
            }
        }

        vTaskDelay(
            pdMS_TO_TICKS(APP_WATCHDOG_REFRESH_PERIOD_MS)
        );
    }
}

/*
 * Dipanggil dari Java. Hanya menaikkan counter; keputusan menyuapi WWDT
 * tetap di APP_WatchdogTask agar terpusat di satu tempat.
 */
void Java_com_nxp_example_smartgreenhouse_services_watchdog_WatchdogNative_refreshNative(void)
{
    appHeartbeatCounter++;
}

static void BOARD_InitLcdicClock()
{
    CLOCK_EnableClock(
        kCLOCK_T3PllMciFlexspiClk
    );

    PRINTF(
        "kMAIN_CLK_to_LCD_CLK %d\n",
        kMAIN_CLK_to_LCD_CLK
    );

    CLOCK_AttachClk(
        kT3PLL_MCI_FLEXSPI_to_LCD_CLK
    );

    CLOCK_SetClkDiv(
        kCLOCK_DivLcdClk,
        12
    );

    RESET_PeripheralReset(
        kLCDIC_RST_SHIFT_RSTn
    );
}

void microjvm_main(void)
{
    void *vm;
    int32_t err;
    int32_t exitcode;

    vm =
        SNI_createVM();

    if (vm == NULL)
    {
        PRINTF(
            "VM initialization error.\n"
        );
    }
    else
    {
        PRINTF(
            "VM START\n"
        );

        err =
            SNI_startVM(
                vm,
                0,
                NULL
            );

        if (err < 0)
        {
            if (err == LLMJVM_E_EVAL_LIMIT)
            {
                PRINTF(
                    "Evaluation limits reached.\n"
                );
            }
            else
            {
                PRINTF(
                    "VM execution error (err = %d).\n",
                    -err
                );
            }
        }
        else
        {
            exitcode =
                SNI_getExitCode(
                    vm
                );

            PRINTF(
                "VM END (exit code = %d)\n",
                exitcode
            );
        }

        SNI_destroyVM(
            vm
        );
    }
}

extern char _HeapAsFreeRAMSize
__asm("_HeapAsFreeRAMSize");

static void nxp_pa_task(
    void *pvParameters)
{
    (void)pvParameters;

#ifdef CPULOAD_ENABLED
    cpuload_init();
#endif

#ifdef SD_ENABLED
    START_SDCARD_Task(
        NULL
    );
#endif

    for (;;)
    {
        PRINTF(
            "\r\nNXP PLATFORM ACCELERATOR\r\n"
        );

        PRINTF(
            "NXP VEE Port '%s' '%s'\r\n",
            VEE_VERSION,
            GIT_SHA_1
        );

        PRINTF(
            "NXP VEE Heap size: %u Bytes\r\n",
            (unsigned)&_HeapAsFreeRAMSize
        );

#if (ENABLE_SHELL == 1)
        if (shell_init() != 0)
        {
            PRINTF(
                "Could not run shell...\r\n"
            );
        }
#endif

        microjvm_main();

        vTaskSuspend(
            NULL
        );
    }
}

void vApplicationMallocFailedHook()
{
    PRINTF(
        (
            "\r\nERROR: Malloc failed to allocate memory\r\n"
        )
    );

    for (;;)
        ;
}

void vApplicationIdleHook(void)
{
#ifdef CPULOAD_ENABLED
    cpuload_idle();
#endif
}