#include "display_support.h"

#include "FreeRTOS.h"
#include "task.h"
#include "queue.h"
#include "semphr.h"
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

#include "fsl_i2c.h"
#include "fsl_video_common.h"

#include "pin_mux.h"
#include "clock_config.h"
#include "board.h"
#include "fsl_lcdic.h"
#include "fsl_lcdic_dma.h"
#include "fsl_debug_console.h"
#include "panel_func.h"

#include "fsl_inputmux.h"
#include "fsl_reset.h"

#if (DEMO_PANEL == DEMO_PANEL_LCD_PAR_S035)
#include "fsl_st7796s.h"
#include "fsl_gt911.h"
#include "fsl_dbi_lcdic_dma.h"
#include "fsl_inputmux.h"
#else
#include "fsl_ili9341.h"
#include "fsl_ft6x06.h"
#include "fsl_dbi_spi_dma.h"
#endif

static void APP_InitLcdic(void);
static void APP_LcdDoneCallback(LCDIC_Type *base, lcdic_dma_handle_t *handle, status_t status, void *userData);
static void APP_ResetLcdTransferState(void);

SDK_ALIGN(uint8_t s_lcdicBuffer[1][LCD_VIRTUAL_BUF_SIZE * LCD_FB_BYTE_PER_PIXEL], 4);

uint8_t *disp_sup_get_fb_address() { return &s_lcdicBuffer[0]; }

static lcdic_dma_handle_t s_lcdHandle;
static dma_handle_t s_lcdDmaTxHandle;

static volatile bool lcd_transfer_done;
static volatile status_t lcd_transfer_status;
static SemaphoreHandle_t shared_i2c2_mutex;
static bool shared_i2c2_initialized;

#define DISPLAY_FLUSH_TIMEOUT_MS 2000U
#define SHARED_I2C2_MUTEX_TIMEOUT_MS 1000U
#define SHARED_I2C2_BAUDRATE 100000U
#define SHARED_I2C2_HARDWARE_TIMEOUT_MS 50U

AT_NONCACHEABLE_SECTION_ALIGN(static dma_descriptor_t s_dmaDesc[2], 16);

static void APP_InitLcdic(void)
{
    lcdic_config_t config;

    LCDIC_GetDefaultConfig(&config);
    config.mode = APP_LCDIC_MODE;

#if (APP_LCDIC_ENDIAN == APP_LCD_BIG_ENDIAN)
    config.endian = kLCDIC_BigEndian;
#else
    config.endian = kLCDIC_LittleEndian;
#endif

#if defined(APP_LCDIC_SPI_FLAG)
    config.spiCtrlFlags = APP_LCDIC_SPI_FLAG;
#endif

#if defined(APP_LCDIC_I8080_FLAG)
    config.i8080CtrlFlags = APP_LCDIC_I8080_FLAG;
#endif

    config.cmdShortTimeout_Timer0 = 0U;
    config.cmdLongTimeout_Timer1 = 0U;

    LCDIC_Init(APP_LCDIC, &config);

    DMA_Init(APP_DMA);
    DMA_CreateHandle(&s_lcdDmaTxHandle, APP_DMA, APP_LCD_TX_DMA_CH);

    LCDIC_TransferCreateHandleDMA(APP_LCDIC, &s_lcdHandle, APP_LcdDoneCallback, NULL, &s_lcdDmaTxHandle, NULL, s_dmaDesc);

    NVIC_SetPriority(LCDIC_GetIRQn(LCDIC_GetInstance(APP_LCDIC)), 3);
}

static void APP_LcdDoneCallback(LCDIC_Type *base, lcdic_dma_handle_t *handle, status_t status, void *userData)
{
    (void)base;
    (void)handle;
    (void)userData;

    lcd_transfer_status = status;
    lcd_transfer_done = true;
}

void disp_sup_flush(uint8_t* srcAddr, uint32_t startX, uint32_t startY, uint32_t endX, uint32_t endY, uint32_t number_pixel)
{
    uint32_t flush_width = endX - startX + 1;
    lcdic_xfer_t xfer;

    APP_PanelSelectRegion(startX, startY, endX, endY);

    xfer.mode = kLCDIC_XferSendDataArray;
    xfer.txXfer.cmd = APP_MEM_WRITE_CMD;
    xfer.txXfer.teSyncMode = kLCDIC_TeNoSync;
    xfer.txXfer.trxTimeoutMode = kLCDIC_LongTimeout;
    xfer.txXfer.dataFormat = kLCDIC_DataFormatHalfWord;
    xfer.txXfer.dataLen = number_pixel * LCD_FB_BYTE_PER_PIXEL;
    xfer.txXfer.txData = (const uint8_t *)srcAddr;

    if (flush_width != LCD_WIDTH)
    {
        uint32_t flush_height = endY - startY + 1;
        uint8_t *w = srcAddr + flush_width * LCD_FB_BYTE_PER_PIXEL;
        uint8_t *r = srcAddr + LCD_WIDTH * LCD_FB_BYTE_PER_PIXEL;

        for (uint32_t index = 0; index < flush_height; index++)
        {
            memcpy(w, r, flush_width * LCD_FB_BYTE_PER_PIXEL);
            w += flush_width * LCD_FB_BYTE_PER_PIXEL;
            r += LCD_WIDTH * LCD_FB_BYTE_PER_PIXEL;
        }
    }

    lcd_transfer_done = false;
    lcd_transfer_status = kStatus_Success;

    status_t transfer_status = LCDIC_TransferDMA(APP_LCDIC, &s_lcdHandle, &xfer);

    if (transfer_status == kStatus_Busy)
    {
        APP_ResetLcdTransferState();

        lcd_transfer_done = false;
        lcd_transfer_status = kStatus_Success;

        transfer_status = LCDIC_TransferDMA(APP_LCDIC, &s_lcdHandle, &xfer);
    }

    if (transfer_status != kStatus_Success)
    {
        PRINTF("[DISPLAY] LCD DMA start failed | status=%d\r\n", (int)transfer_status);
        APP_ResetLcdTransferState();
        return;
    }

    TickType_t start_tick = xTaskGetTickCount();
    TickType_t timeout_ticks = pdMS_TO_TICKS(DISPLAY_FLUSH_TIMEOUT_MS);

    while (!lcd_transfer_done)
    {
        if ((xTaskGetTickCount() - start_tick) >= timeout_ticks)
        {
            PRINTF("[DISPLAY] LCD DMA timeout | recovering display transfer state\r\n");
            APP_ResetLcdTransferState();
            return;
        }

        vTaskDelay(pdMS_TO_TICKS(1U));
    }

    if (lcd_transfer_status != kStatus_Success)
    {
        PRINTF("[DISPLAY] LCD DMA callback error | status=%d | recovering display transfer state\r\n", (int)lcd_transfer_status);
        APP_ResetLcdTransferState();
    }
}

static void APP_ResetLcdTransferState(void)
{
    DMA_AbortTransfer(&s_lcdDmaTxHandle);
    LCDIC_ResetState(APP_LCDIC);

    lcd_transfer_done = false;
    lcd_transfer_status = kStatus_Success;

    LCDIC_TransferCreateHandleDMA(APP_LCDIC, &s_lcdHandle, APP_LcdDoneCallback, NULL, &s_lcdDmaTxHandle, NULL, s_dmaDesc);
}

status_t BSP_I2C2_InitShared(void)
{
    if (shared_i2c2_mutex == NULL)
    {
        shared_i2c2_mutex = xSemaphoreCreateMutex();

        if (shared_i2c2_mutex == NULL)
        {
            PRINTF("[I2C2] Unable to create shared mutex\r\n");
            return kStatus_Fail;
        }
    }

    if (xSemaphoreTake(shared_i2c2_mutex, pdMS_TO_TICKS(SHARED_I2C2_MUTEX_TIMEOUT_MS)) != pdTRUE)
    {
        PRINTF("[I2C2] Init mutex timeout\r\n");
        return kStatus_Fail;
    }

    if (!shared_i2c2_initialized)
    {
        i2c_master_config_t i2cConfig = {0};

        I2C_MasterGetDefaultConfig(&i2cConfig);

        i2cConfig.baudRate_Bps = SHARED_I2C2_BAUDRATE;
        i2cConfig.enableTimeout = true;
        i2cConfig.timeout_Ms = SHARED_I2C2_HARDWARE_TIMEOUT_MS;

        I2C_MasterInit(I2C2, &i2cConfig, CLOCK_GetFlexCommClkFreq(2));

        shared_i2c2_initialized = true;

        PRINTF("[I2C2] Shared controller initialized | baudrate=%u Hz\r\n", SHARED_I2C2_BAUDRATE);
    }

    xSemaphoreGive(shared_i2c2_mutex);

    return kStatus_Success;
}

status_t BSP_I2C2_MasterTransfer(i2c_master_transfer_t *transfer)
{
    status_t status;

    if (transfer == NULL)
    {
        return kStatus_InvalidArgument;
    }

    status = BSP_I2C2_InitShared();

    if (status != kStatus_Success)
    {
        return status;
    }

    if (xSemaphoreTake(shared_i2c2_mutex, pdMS_TO_TICKS(SHARED_I2C2_MUTEX_TIMEOUT_MS)) != pdTRUE)
    {
        PRINTF("[I2C2] Transfer mutex timeout\r\n");
        return kStatus_Fail;
    }

    status = I2C_MasterTransferBlocking(I2C2, transfer);

    xSemaphoreGive(shared_i2c2_mutex);

    return status;
}

void disp_sup_pre_init(void)
{
    BSP_I2C2_InitShared();
}

void disp_sup_indev_init(void)
{
}

void disp_sup_disp_init(void)
{
    APP_InitLcdic();
    APP_InitPanel();
}

#if (DEMO_PANEL == DEMO_PANEL_LCD_PAR_S035)
static gt911_handle_t touchHandle;
#else
static ft6x06_handle_t touchHandle;
#endif

static status_t DEMO_TouchI2C_Init(void);
static status_t DEMO_TouchI2C_Send(uint8_t deviceAddress, uint32_t subAddress, uint8_t subAddressSize, const uint8_t *txBuff, uint8_t txBuffSize);
static status_t DEMO_TouchI2C_Receive(uint8_t deviceAddress, uint32_t subAddress, uint8_t subAddressSize, uint8_t *rxBuff, uint8_t rxBuffSize);

status_t BOARD_PrepareTouchPanel(void)
{
    PRINTF("%s\n", __func__);

    status_t status = DEMO_TouchI2C_Init();

    return status;
}

status_t BOARD_InitTouchPanel(void)
{
    PRINTF("%s\n", __func__);

    status_t status;

#if (DEMO_PANEL == DEMO_PANEL_LCD_PAR_S035)
    gt911_config_t touchConfig = {
        .I2C_SendFunc = DEMO_TouchI2C_Send,
        .I2C_ReceiveFunc = DEMO_TouchI2C_Receive,
        .timeDelayMsFunc = VIDEO_DelayMs,
        .intPinFunc = NULL,
        .pullResetPinFunc = NULL,
        .touchPointNum = 1,
        .i2cAddrMode = kGT911_I2cAddrAny,
        .intTrigMode = kGT911_IntFallingEdge
    };

    status = GT911_Init(&touchHandle, &touchConfig);
#else
    ft6x06_config_t touchConfig = {
        .I2C_SendFunc = DEMO_TouchI2C_Send,
        .I2C_ReceiveFunc = DEMO_TouchI2C_Receive
    };

    status = FT6X06_Init(&touchHandle, &touchConfig);
#endif

    if (status != kStatus_Success)
    {
        PRINTF("Touch panel init failed\n");
    }

    return status;
}

status_t BOARD_GetTouchPanelPoint(int *x, int *y)
{
    status_t status;

    static int touch_x = 0;
    static int touch_y = 0;

#if (DEMO_PANEL == DEMO_PANEL_LCD_PAR_S035)

    status = GT911_GetSingleTouch(&touchHandle, &touch_x, &touch_y);

    *x = touch_y;
    *y = touchHandle.resolutionX - touch_x;

#else

    touch_event_t touch_event;

    status = FT6X06_GetSingleTouch(&touchHandle, &touch_event, &touch_x, &touch_y);

    if ((status == kStatus_Success) && (touch_event == kTouch_Down) || (touch_event == kTouch_Contact))
    {
        status = kStatus_Success;
    }
    else
    {
        status = kStatus_Fail;
    }

    *x = DEMO_PANEL_WIDTH - touch_y;
    *y = touch_x;

#endif

    return status;
}

#define DEMO_TOUCH_I2C I2C2
#define DEMO_TOUCH_I2C_CLOCK_FREQ CLOCK_GetFlexCommClkFreq(2)

#define DEMO_SPI SPI1
#define DEMO_SPI_CLOCK_FREQ CLOCK_GetFlexCommClkFreq(1)
#define DEMO_SPI_IRQn FLEXCOMM1_IRQn
#define DEMO_SPI_TX_DMA_CH 3
#define DEMO_SPI_RX_DMA_CH 2

#define DEMO_DBI_DC_PORT 1
#define DEMO_DBI_DC_PIN (52 - (DEMO_DBI_DC_PORT * 32U))

static status_t DEMO_TouchI2C_Init(void)
{
    PRINTF("%s\n", __func__);
    return BSP_I2C2_InitShared();
}

static status_t DEMO_TouchI2C_Send(uint8_t deviceAddress, uint32_t subAddress, uint8_t subAddressSize, const uint8_t *txBuff, uint8_t txBuffSize)
{
    i2c_master_transfer_t masterXfer;

    masterXfer.slaveAddress = deviceAddress;
    masterXfer.direction = kI2C_Write;
    masterXfer.subaddress = subAddress;
    masterXfer.subaddressSize = subAddressSize;
    masterXfer.data = (uint8_t *)txBuff;
    masterXfer.dataSize = txBuffSize;
    masterXfer.flags = kI2C_TransferDefaultFlag;

    return BSP_I2C2_MasterTransfer(&masterXfer);
}

static status_t DEMO_TouchI2C_Receive(uint8_t deviceAddress, uint32_t subAddress, uint8_t subAddressSize, uint8_t *rxBuff, uint8_t rxBuffSize)
{
    i2c_master_transfer_t masterXfer;

    masterXfer.slaveAddress = deviceAddress;
    masterXfer.subaddress = subAddress;
    masterXfer.subaddressSize = subAddressSize;
    masterXfer.data = rxBuff;
    masterXfer.dataSize = rxBuffSize;
    masterXfer.direction = kI2C_Read;
    masterXfer.flags = kI2C_TransferDefaultFlag;

    return BSP_I2C2_MasterTransfer(&masterXfer);
}

void DEMO_InitTouch(void)
{
    PRINTF("%s\n", __func__);
    BOARD_PrepareTouchPanel();
    BOARD_InitTouchPanel();
}

void DEMO_ReadTouch(int *pressed, int *touch_x, int *touch_y)
{
    *pressed = BOARD_GetTouchPanelPoint(touch_x, touch_y);
}