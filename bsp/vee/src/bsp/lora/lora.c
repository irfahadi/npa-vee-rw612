#include "lora.h"

/*
 * FreeRTOS
 */
#include "FreeRTOS.h"
#include "task.h"

/*
 * NXP SDK / Board
 */
#include "fsl_device_registers.h"
#include "fsl_debug_console.h"
#include "fsl_gpio.h"
#include "fsl_io_mux.h"
#include "fsl_spi.h"
#include "fsl_reset.h"

#include "pin_mux.h"
#include "clock_config.h"
#include "board.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>


/*******************************************************************************
 * Definitions
 ******************************************************************************/

#define lora_test_task_PRIORITY \
    (tskIDLE_PRIORITY + 1U)

#define LORA_TEST_TASK_STACK_SIZE \
    768U

#define LORA_SPI \
    SPI1

#define LORA_SPI_CLOCK_INDEX \
    1U

#define LORA_SPI_BAUDRATE_BPS \
    1000000U

#define LORA_RESET_GPIO_PORT \
    1U

#define LORA_RESET_GPIO_PIN \
    20U

#define RFM95_REG_VERSION \
    0x42U

#define RFM95_EXPECTED_VERSION \
    0x12U

#define LORA_SENSOR_PAYLOAD_LENGTH \
    22U

#define RFM95_FREQUENCY_HZ \
    923000000UL

#define RFM95_FXOSC_HZ \
    32000000UL

#define RFM95_MAX_PACKET_LENGTH \
    255U

#define RFM95_REG_FIFO \
    0x00U

#define RFM95_REG_OP_MODE \
    0x01U

#define RFM95_REG_FRF_MSB \
    0x06U

#define RFM95_REG_FRF_MID \
    0x07U

#define RFM95_REG_FRF_LSB \
    0x08U

#define RFM95_REG_LNA \
    0x0CU

#define RFM95_REG_FIFO_ADDR_PTR \
    0x0DU

#define RFM95_REG_FIFO_TX_BASE_ADDR \
    0x0EU

#define RFM95_REG_FIFO_RX_BASE_ADDR \
    0x0FU

#define RFM95_REG_FIFO_RX_CURRENT_ADDR \
    0x10U

#define RFM95_REG_IRQ_FLAGS \
    0x12U

#define RFM95_REG_RX_NB_BYTES \
    0x13U

#define RFM95_REG_PKT_SNR_VALUE \
    0x19U

#define RFM95_REG_PKT_RSSI_VALUE \
    0x1AU

#define RFM95_REG_MODEM_CONFIG_1 \
    0x1DU

#define RFM95_REG_MODEM_CONFIG_2 \
    0x1EU

#define RFM95_REG_PREAMBLE_MSB \
    0x20U

#define RFM95_REG_PREAMBLE_LSB \
    0x21U

#define RFM95_REG_MAX_PAYLOAD_LENGTH \
    0x23U

#define RFM95_REG_MODEM_CONFIG_3 \
    0x26U

#define RFM95_REG_DETECTION_OPTIMIZE \
    0x31U

#define RFM95_REG_INVERT_IQ \
    0x33U

#define RFM95_REG_DETECTION_THRESHOLD \
    0x37U

#define RFM95_REG_SYNC_WORD \
    0x39U

#define RFM95_REG_INVERT_IQ_2 \
    0x3BU

#define RFM95_REG_DIO_MAPPING_1 \
    0x40U

#define RFM95_MODE_LONG_RANGE \
    0x80U

#define RFM95_MODE_SLEEP \
    0x00U

#define RFM95_MODE_STANDBY \
    0x01U

#define RFM95_MODE_RX_CONTINUOUS \
    0x05U

#define RFM95_IRQ_PAYLOAD_CRC_ERROR \
    0x20U

#define RFM95_IRQ_RX_DONE \
    0x40U

typedef struct
{
    uint8_t node_id;
    uint8_t node_target;

    int16_t soil_moisture;
    int16_t soil_temperature;
    int16_t conductivity;
    int16_t soil_ph;
    int16_t nitrogen;
    int16_t phosphorus;
    int16_t potassium;
    int16_t air_temperature;
    int16_t air_humidity;
    int16_t light_intensity;

} lora_sensor_payload_t;

_Static_assert(
    sizeof(lora_sensor_payload_t) == 22U,
    "LoRa sensor payload must be exactly 22 bytes"
);

/*******************************************************************************
 * Prototypes
 ******************************************************************************/

static void lora_test_task(
    void *pvParameters
);

static void LORA_InitSpi(
    void
);

static void LORA_Reset(
    void
);

static bool LORA_ReadRegister(
    uint8_t address,
    uint8_t *value
);

static bool LORA_WriteRegister(
    uint8_t address,
    uint8_t value
);

static bool LORA_ConfigureReceiver(
    void
);

static int32_t LORA_ReceivePacket(
    uint8_t *buffer,
    uint32_t capacity,
    int32_t *rssi,
    int32_t *snrQuarterDb
);

static void LORA_PrintPacket(
    const uint8_t *buffer,
    uint32_t length,
    int32_t rssi,
    int32_t snrQuarterDb
);

static int16_t LORA_ReadInt16LE(
    const uint8_t *buffer,
    uint32_t offset
);

static void LORA_PrintSensorPayload(
    const uint8_t *buffer,
    uint32_t length
);

static bool LORA_SaveLatestPayload(
    const uint8_t *buffer,
    uint32_t length,
    int32_t rssi,
    int32_t snrQuarterDb
);

static status_t LORA_SpiTransfer(
    uint8_t *txData,
    uint8_t *rxData,
    size_t dataSize
);


/*******************************************************************************
 * Global / Module variables
 ******************************************************************************/

static lora_latest_data_t g_loraLatestData = {0};


/*******************************************************************************
 * Public functions
 ******************************************************************************/

void LORA_Start(void)
{
    PRINTF(
        "[MEM] Before LoRa task"
        " | free=%u"
        " | minimum=%u\r\n",
        (unsigned int)xPortGetFreeHeapSize(),
        (unsigned int)xPortGetMinimumEverFreeHeapSize()
    );

    if (xTaskCreate(
            lora_test_task,
            "LoRa_Test",
            LORA_TEST_TASK_STACK_SIZE,
            NULL,
            lora_test_task_PRIORITY,
            NULL) != pdPASS)
    {
        PRINTF(
            "[LORA] ERROR: task creation failed.\r\n"
        );
    }

    PRINTF(
        "[MEM] After LoRa task"
        " | free=%u"
        " | minimum=%u\r\n",
        (unsigned int)xPortGetFreeHeapSize(),
        (unsigned int)xPortGetMinimumEverFreeHeapSize()
    );
}

bool LORA_CopyLatestData(
    lora_latest_data_t *destination)
{
    if (destination == NULL)
    {
        return false;
    }

    taskENTER_CRITICAL();

    if (!g_loraLatestData.available)
    {
        taskEXIT_CRITICAL();

        return false;
    }

    *destination =
        g_loraLatestData;

    taskEXIT_CRITICAL();

    return true;
}

/*******************************************************************************
 * Internal functions
 ******************************************************************************/

static status_t LORA_SpiTransfer(
    uint8_t *txData,
    uint8_t *rxData,
    size_t dataSize)
{
    spi_transfer_t transfer = {0};

    transfer.txData =
        txData;

    transfer.rxData =
        rxData;

    transfer.dataSize =
        dataSize;

    transfer.configFlags =
        kSPI_FrameAssert;

    return SPI_MasterTransferBlocking(
        LORA_SPI,
        &transfer
    );
}

static bool LORA_ReadRegister(
    uint8_t address,
    uint8_t *value)
{
    uint8_t txData[2];
    uint8_t rxData[2];

    if (value == NULL)
    {
        return false;
    }

    txData[0] =
        address & 0x7FU;

    txData[1] =
        0x00U;

    rxData[0] =
        0x00U;

    rxData[1] =
        0x00U;

    status_t status =
        LORA_SpiTransfer(
            txData,
            rxData,
            sizeof(txData)
        );

    if (status != kStatus_Success)
    {
        return false;
    }

    *value =
        rxData[1];

    return true;
}

static bool LORA_WriteRegister(
    uint8_t address,
    uint8_t value)
{
    uint8_t txData[2];
    uint8_t rxData[2];

    status_t status;

    txData[0] =
        address | 0x80U;

    txData[1] =
        value;

    rxData[0] =
        0x00U;

    rxData[1] =
        0x00U;

    status =
        LORA_SpiTransfer(
            txData,
            rxData,
            sizeof(txData)
        );

    return status == kStatus_Success;
}

static void LORA_Reset(void)
{
    gpio_pin_config_t resetConfiguration = {
        kGPIO_DigitalOutput,
        1U
    };

    IO_MUX_SetPinMux(
        IO_MUX_GPIO52
    );

    GPIO_PinInit(
        GPIO,
        LORA_RESET_GPIO_PORT,
        LORA_RESET_GPIO_PIN,
        &resetConfiguration
    );

    GPIO_PinWrite(
        GPIO,
        LORA_RESET_GPIO_PORT,
        LORA_RESET_GPIO_PIN,
        1U
    );

    vTaskDelay(
        pdMS_TO_TICKS(2U)
    );

    GPIO_PinWrite(
        GPIO,
        LORA_RESET_GPIO_PORT,
        LORA_RESET_GPIO_PIN,
        0U
    );

    vTaskDelay(
        pdMS_TO_TICKS(10U)
    );

    GPIO_PinWrite(
        GPIO,
        LORA_RESET_GPIO_PORT,
        LORA_RESET_GPIO_PIN,
        1U
    );

    vTaskDelay(
        pdMS_TO_TICKS(10U)
    );
}

static void LORA_InitSpi(void)
{
    spi_master_config_t masterConfiguration;

    CLOCK_AttachClk(
        kSFRO_to_FLEXCOMM1
    );

    RESET_PeripheralReset(
        kFC1_RST_SHIFT_RSTn
    );

    IO_MUX_SetPinMux(
        IO_MUX_FC1_SPI_SS0
    );

    SPI_MasterGetDefaultConfig(
        &masterConfiguration
    );

    masterConfiguration.baudRate_Bps =
        LORA_SPI_BAUDRATE_BPS;

    masterConfiguration.polarity =
        kSPI_ClockPolarityActiveHigh;

    masterConfiguration.phase =
        kSPI_ClockPhaseFirstEdge;

    masterConfiguration.direction =
        kSPI_MsbFirst;

    masterConfiguration.sselNum =
        kSPI_Ssel0;

    SPI_MasterInit(
        LORA_SPI,
        &masterConfiguration,
        CLOCK_GetFlexCommClkFreq(
            LORA_SPI_CLOCK_INDEX
        )
    );
}

static bool LORA_ConfigureReceiver(void)
{
    uint64_t frequencyRegister;
    uint8_t lnaValue;

    if (!LORA_WriteRegister(
            RFM95_REG_OP_MODE,
            RFM95_MODE_LONG_RANGE |
                RFM95_MODE_SLEEP))
    {
        return false;
    }

    vTaskDelay(
        pdMS_TO_TICKS(10U)
    );

    frequencyRegister =
        (((uint64_t)RFM95_FREQUENCY_HZ) << 19U) /
        RFM95_FXOSC_HZ;

    if (!LORA_WriteRegister(
            RFM95_REG_FRF_MSB,
            (uint8_t)(frequencyRegister >> 16U)))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FRF_MID,
            (uint8_t)(frequencyRegister >> 8U)))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FRF_LSB,
            (uint8_t)frequencyRegister))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FIFO_TX_BASE_ADDR,
            0x00U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FIFO_RX_BASE_ADDR,
            0x00U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FIFO_ADDR_PTR,
            0x00U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_MODEM_CONFIG_1,
            0x72U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_MODEM_CONFIG_2,
            0x70U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_MODEM_CONFIG_3,
            0x04U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_DETECTION_OPTIMIZE,
            0xC3U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_DETECTION_THRESHOLD,
            0x0AU))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_PREAMBLE_MSB,
            0x00U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_PREAMBLE_LSB,
            0x08U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_SYNC_WORD,
            0x12U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_INVERT_IQ,
            0x27U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_INVERT_IQ_2,
            0x1DU))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_DIO_MAPPING_1,
            0x00U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_MAX_PAYLOAD_LENGTH,
            RFM95_MAX_PACKET_LENGTH))
    {
        return false;
    }

    if (!LORA_ReadRegister(
            RFM95_REG_LNA,
            &lnaValue))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_LNA,
            lnaValue | 0x03U))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_IRQ_FLAGS,
            0xFFU))
    {
        return false;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_OP_MODE,
            RFM95_MODE_LONG_RANGE |
                RFM95_MODE_STANDBY))
    {
        return false;
    }

    vTaskDelay(
        pdMS_TO_TICKS(10U)
    );

    if (!LORA_WriteRegister(
            RFM95_REG_OP_MODE,
            RFM95_MODE_LONG_RANGE |
                RFM95_MODE_RX_CONTINUOUS))
    {
        return false;
    }

    return true;
}

static int32_t LORA_ReceivePacket(
    uint8_t *buffer,
    uint32_t capacity,
    int32_t *rssi,
    int32_t *snrQuarterDb)
{
    uint8_t irqFlags;
    uint8_t packetLength;
    uint8_t currentFifoAddress;
    uint8_t rawRssi;
    uint8_t rawSnr;

    uint32_t index;

    if (buffer == NULL ||
        capacity == 0U ||
        rssi == NULL ||
        snrQuarterDb == NULL)
    {
        return -2;
    }

    if (!LORA_ReadRegister(
            RFM95_REG_IRQ_FLAGS,
            &irqFlags))
    {
        return -2;
    }

    if ((irqFlags & RFM95_IRQ_RX_DONE) == 0U)
    {
        return 0;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_IRQ_FLAGS,
            irqFlags))
    {
        return -2;
    }

    if ((irqFlags &
         RFM95_IRQ_PAYLOAD_CRC_ERROR) != 0U)
    {
        return -1;
    }

    if (!LORA_ReadRegister(
            RFM95_REG_RX_NB_BYTES,
            &packetLength))
    {
        return -2;
    }

    if (!LORA_ReadRegister(
            RFM95_REG_FIFO_RX_CURRENT_ADDR,
            &currentFifoAddress))
    {
        return -2;
    }

    if (!LORA_WriteRegister(
            RFM95_REG_FIFO_ADDR_PTR,
            currentFifoAddress))
    {
        return -2;
    }

    if ((uint32_t)packetLength > capacity)
    {
        packetLength =
            (uint8_t)capacity;
    }

    for (index = 0U;
         index < (uint32_t)packetLength;
         index++)
    {
        if (!LORA_ReadRegister(
                RFM95_REG_FIFO,
                &buffer[index]))
        {
            return -2;
        }
    }

    if (!LORA_ReadRegister(
            RFM95_REG_PKT_RSSI_VALUE,
            &rawRssi))
    {
        return -2;
    }

    if (!LORA_ReadRegister(
            RFM95_REG_PKT_SNR_VALUE,
            &rawSnr))
    {
        return -2;
    }

    *rssi =
        ((int32_t)rawRssi) - 157;

    *snrQuarterDb =
        (int32_t)((int8_t)rawSnr);

    return (int32_t)packetLength;
}

static int16_t LORA_ReadInt16LE(
    const uint8_t *buffer,
    uint32_t offset)
{
    uint16_t value;

    value =
        ((uint16_t)buffer[offset]) |
        ((uint16_t)buffer[offset + 1U] << 8U);

    return (int16_t)value;
}

static bool LORA_SaveLatestPayload(
    const uint8_t *buffer,
    uint32_t length,
    int32_t rssi,
    int32_t snrQuarterDb)
{
    if (buffer == NULL ||
        length != LORA_SENSOR_PAYLOAD_LENGTH)
    {
        return false;
    }

    taskENTER_CRITICAL();

    g_loraLatestData.node_id =
        buffer[0];

    g_loraLatestData.node_target =
        buffer[1];

    g_loraLatestData.soil_moisture =
        LORA_ReadInt16LE(
            buffer,
            2U
        );

    g_loraLatestData.soil_temperature =
        LORA_ReadInt16LE(
            buffer,
            4U
        );

    g_loraLatestData.conductivity =
        LORA_ReadInt16LE(
            buffer,
            6U
        );

    g_loraLatestData.soil_ph =
        LORA_ReadInt16LE(
            buffer,
            8U
        );

    g_loraLatestData.nitrogen =
        LORA_ReadInt16LE(
            buffer,
            10U
        );

    g_loraLatestData.phosphorus =
        LORA_ReadInt16LE(
            buffer,
            12U
        );

    g_loraLatestData.potassium =
        LORA_ReadInt16LE(
            buffer,
            14U
        );

    g_loraLatestData.air_temperature =
        LORA_ReadInt16LE(
            buffer,
            16U
        );

    g_loraLatestData.air_humidity =
        LORA_ReadInt16LE(
            buffer,
            18U
        );

    g_loraLatestData.light_intensity =
        LORA_ReadInt16LE(
            buffer,
            20U
        );

    g_loraLatestData.rssi =
        rssi;

    g_loraLatestData.snr_quarter_db =
        snrQuarterDb;

    g_loraLatestData.sequence++;

    g_loraLatestData.available =
        true;

    taskEXIT_CRITICAL();

    return true;
}

static void LORA_PrintPacket(
    const uint8_t *buffer,
    uint32_t length,
    int32_t rssi,
    int32_t snrQuarterDb)
{
    uint32_t index;

    int32_t absoluteSnr;
    int32_t snrWhole;
    int32_t snrFraction;

    const char *snrSign;

    absoluteSnr =
        snrQuarterDb < 0
            ? -snrQuarterDb
            : snrQuarterDb;

    snrSign =
        snrQuarterDb < 0
            ? "-"
            : "";

    snrWhole =
        absoluteSnr / 4;

    snrFraction =
        (absoluteSnr % 4) * 25;

    PRINTF(
        "\r\n"
        "[LORA] Packet received"
        " | length=%u"
        " | RSSI=%d dBm"
        " | SNR=%s%d.%02d dB\r\n",
        (unsigned int)length,
        (int)rssi,
        snrSign,
        (int)snrWhole,
        (int)snrFraction
    );

    PRINTF(
        "[LORA] HEX   : "
    );

    for (index = 0U;
         index < length;
         index++)
    {
        PRINTF(
            "%02X ",
            (unsigned int)buffer[index]
        );
    }

    PRINTF(
        "\r\n"
    );

    PRINTF(
        "[LORA] ASCII : "
    );

    for (index = 0U;
         index < length;
         index++)
    {
        uint8_t value;
        char visibleCharacter;

        value =
            buffer[index];

        visibleCharacter =
            value >= 32U &&
                    value <= 126U
                ? (char)value
                : '.';

        PRINTF(
            "%c",
            (int)visibleCharacter
        );
    }

    PRINTF(
        "\r\n"
    );

    if (length == 22U)
    {
        PRINTF(
            "[LORA] Sensor payload recognized: 22 bytes.\r\n"
        );

        LORA_PrintSensorPayload(
            buffer,
            length
        );
    }
}

static void LORA_PrintSensorPayload(
    const uint8_t *buffer,
    uint32_t length)
{
    uint8_t nodeId;
    uint8_t nodeTarget;

    int16_t soilMoisture;
    int16_t soilTemperature;
    int16_t conductivity;
    int16_t soilPh;
    int16_t nitrogen;
    int16_t phosphorus;
    int16_t potassium;
    int16_t airTemperature;
    int16_t airHumidity;
    int16_t lightIntensity;

    if (buffer == NULL ||
        length != 22U)
    {
        return;
    }

    nodeId =
        buffer[0];

    nodeTarget =
        buffer[1];

    soilMoisture =
        LORA_ReadInt16LE(
            buffer,
            2U
        );

    soilTemperature =
        LORA_ReadInt16LE(
            buffer,
            4U
        );

    conductivity =
        LORA_ReadInt16LE(
            buffer,
            6U
        );

    soilPh =
        LORA_ReadInt16LE(
            buffer,
            8U
        );

    nitrogen =
        LORA_ReadInt16LE(
            buffer,
            10U
        );

    phosphorus =
        LORA_ReadInt16LE(
            buffer,
            12U
        );

    potassium =
        LORA_ReadInt16LE(
            buffer,
            14U
        );

    airTemperature =
        LORA_ReadInt16LE(
            buffer,
            16U
        );

    airHumidity =
        LORA_ReadInt16LE(
            buffer,
            18U
        );

    lightIntensity =
        LORA_ReadInt16LE(
            buffer,
            20U
        );

    PRINTF(
        "[LORA] Node ID          : %u\r\n",
        (unsigned int)nodeId
    );

    PRINTF(
        "[LORA] Node Target      : %u\r\n",
        (unsigned int)nodeTarget
    );

    PRINTF(
        "[LORA] Soil Moisture    : %d.%d\r\n",
        (int)(soilMoisture / 10),
        (int)(
            soilMoisture >= 0
                ? soilMoisture % 10
                : -(soilMoisture % 10)
        )
    );

    PRINTF(
        "[LORA] Soil Temperature : %d.%d C\r\n",
        (int)(soilTemperature / 10),
        (int)(
            soilTemperature >= 0
                ? soilTemperature % 10
                : -(soilTemperature % 10)
        )
    );

    PRINTF(
        "[LORA] Conductivity     : %d\r\n",
        (int)conductivity
    );

    PRINTF(
        "[LORA] Soil pH          : %d.%d\r\n",
        (int)(soilPh / 10),
        (int)(
            soilPh >= 0
                ? soilPh % 10
                : -(soilPh % 10)
        )
    );

    PRINTF(
        "[LORA] Nitrogen         : %d\r\n",
        (int)nitrogen
    );

    PRINTF(
        "[LORA] Phosphorus       : %d\r\n",
        (int)phosphorus
    );

    PRINTF(
        "[LORA] Potassium        : %d\r\n",
        (int)potassium
    );

    PRINTF(
        "[LORA] Air Temperature  : %d.%d C\r\n",
        (int)(airTemperature / 10),
        (int)(
            airTemperature >= 0
                ? airTemperature % 10
                : -(airTemperature % 10)
        )
    );

    PRINTF(
        "[LORA] Air Humidity     : %d.%d\r\n",
        (int)(airHumidity / 10),
        (int)(
            airHumidity >= 0
                ? airHumidity % 10
                : -(airHumidity % 10)
        )
    );

    PRINTF(
        "[LORA] Light Intensity  : %d\r\n",
        (int)lightIntensity
    );
}

static void lora_test_task(
    void *pvParameters)
{
    uint8_t version;

    uint8_t packetBuffer[
        RFM95_MAX_PACKET_LENGTH
    ];

    bool registerReadSuccessful;
    bool receiverConfigured;

    int32_t packetLength;
    int32_t packetRssi;
    int32_t packetSnrQuarterDb;

    (void)pvParameters;

    version =
        0x00U;

    vTaskDelay(
        pdMS_TO_TICKS(5000U)
    );

    PRINTF(
        "\r\n"
        "[LORA] Starting RFM95W receiver...\r\n"
    );

    PRINTF(
        "[LORA] Initializing FLEXCOMM1 / SPI1...\r\n"
    );

    LORA_InitSpi();

    PRINTF(
        "[LORA] Resetting RFM95W...\r\n"
    );

    LORA_Reset();

    PRINTF(
        "[LORA] Reading RegVersion 0x42...\r\n"
    );

    registerReadSuccessful =
        LORA_ReadRegister(
            RFM95_REG_VERSION,
            &version
        );

    if (!registerReadSuccessful)
    {
        PRINTF(
            "[LORA] ERROR: SPI transfer failed.\r\n"
        );

        vTaskSuspend(
            NULL
        );

        return;
    }

    PRINTF(
        "[LORA] RegVersion = 0x%02X\r\n",
        (unsigned int)version
    );

    if (version !=
        RFM95_EXPECTED_VERSION)
    {
        PRINTF(
            "[LORA] ERROR: RFM95W was not detected.\r\n"
        );

        PRINTF(
            "[LORA] Expected 0x%02X"
            " but received 0x%02X.\r\n",
            (unsigned int)RFM95_EXPECTED_VERSION,
            (unsigned int)version
        );

        vTaskSuspend(
            NULL
        );

        return;
    }

    PRINTF(
        "[LORA] RFM95W detected successfully.\r\n"
    );

    PRINTF(
        "[LORA] Configuring receiver...\r\n"
    );

    receiverConfigured =
        LORA_ConfigureReceiver();

    if (!receiverConfigured)
    {
        PRINTF(
            "[LORA] ERROR:"
            " receiver configuration failed.\r\n"
        );

        vTaskSuspend(
            NULL
        );

        return;
    }

    PRINTF(
        "[LORA] RX continuous ready"
        " | frequency=923 MHz"
        " | SF=7"
        " | BW=125 kHz"
        " | CR=4/5"
        " | preamble=8"
        " | sync=0x12"
        " | CRC=off\r\n"
    );

    PRINTF(
        "[LORA] Waiting for packets...\r\n"
    );

    for (;;)
    {
        packetLength =
            LORA_ReceivePacket(
                packetBuffer,
                sizeof(packetBuffer),
                &packetRssi,
                &packetSnrQuarterDb
            );

        if (packetLength > 0)
        {
            LORA_PrintPacket(
                packetBuffer,
                (uint32_t)packetLength,
                packetRssi,
                packetSnrQuarterDb
            );

            if (LORA_SaveLatestPayload(
                    packetBuffer,
                    (uint32_t)packetLength,
                    packetRssi,
                    packetSnrQuarterDb))
            {
                PRINTF(
                    "[LORA] Latest sensor payload saved in native memory.\r\n"
                );
            }
        }
        else if (packetLength == -1)
        {
            PRINTF(
                "[LORA] Packet discarded"
                " because CRC error was detected.\r\n"
            );
        }
        else if (packetLength == -2)
        {
            PRINTF(
                "[LORA] ERROR:"
                " SPI/register access failed.\r\n"
            );

            vTaskDelay(
                pdMS_TO_TICKS(500U)
            );
        }

        vTaskDelay(
            pdMS_TO_TICKS(20U)
        );
    }
}