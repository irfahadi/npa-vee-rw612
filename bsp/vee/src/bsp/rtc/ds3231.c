#include "ds3231.h"

#include "fsl_i2c.h"
#include "fsl_debug_console.h"
#include "../display_support.h"

#include <stdint.h>
#include <string.h>

#define DS3231_I2C_ADDRESS 0x68U

#define DS3231_REGISTER_SECONDS 0x00U
#define DS3231_REGISTER_MINUTES 0x01U
#define DS3231_REGISTER_HOURS 0x02U
#define DS3231_REGISTER_DAY 0x03U
#define DS3231_REGISTER_DATE 0x04U
#define DS3231_REGISTER_MONTH 0x05U
#define DS3231_REGISTER_YEAR 0x06U

#define DS3231_REGISTER_STATUS 0x0FU

#define DS3231_STATUS_OSF_MASK 0x80U

static uint8_t DS3231_BcdToDecimal(uint8_t value)
{
    return (uint8_t)(((value >> 4U) * 10U) + (value & 0x0FU));
}

static uint8_t DS3231_DecimalToBcd(uint8_t value)
{
    return (uint8_t)(((value / 10U) << 4U) | (value % 10U));
}

static bool DS3231_ReadRegister(uint8_t registerAddress, uint8_t *value)
{
    i2c_master_transfer_t transfer;
    status_t status;

    if (value == NULL)
    {
        return false;
    }

    memset(&transfer, 0, sizeof(transfer));

    transfer.slaveAddress = DS3231_I2C_ADDRESS;
    transfer.direction = kI2C_Read;
    transfer.subaddress = registerAddress;
    transfer.subaddressSize = 1U;
    transfer.data = value;
    transfer.dataSize = 1U;
    transfer.flags = kI2C_TransferDefaultFlag;

    status = BSP_I2C2_MasterTransfer(&transfer);

    if (status != kStatus_Success)
    {
        PRINTF(
            "[RTC] I2C read failed | register=0x%02X | status=%d\r\n",
            registerAddress,
            (int)status
        );

        return false;
    }

    return true;
}

static bool DS3231_WriteRegister(uint8_t registerAddress, uint8_t value)
{
    i2c_master_transfer_t transfer;
    status_t status;
    uint8_t writeValue = value;

    memset(&transfer, 0, sizeof(transfer));

    transfer.slaveAddress = DS3231_I2C_ADDRESS;
    transfer.direction = kI2C_Write;
    transfer.subaddress = registerAddress;
    transfer.subaddressSize = 1U;
    transfer.data = &writeValue;
    transfer.dataSize = 1U;
    transfer.flags = kI2C_TransferDefaultFlag;

    status = BSP_I2C2_MasterTransfer(&transfer);

    if (status != kStatus_Success)
    {
        PRINTF(
            "[RTC] I2C write failed | register=0x%02X | status=%d\r\n",
            registerAddress,
            (int)status
        );

        return false;
    }

    return true;
}

static bool DS3231_IsDateTimeRangeValid(const ds3231_datetime_t *dateTime)
{
    if (dateTime == NULL)
    {
        return false;
    }

    if (dateTime->year < 2000U || dateTime->year > 2099U)
    {
        return false;
    }

    if (dateTime->month < 1U || dateTime->month > 12U)
    {
        return false;
    }

    if (dateTime->date < 1U || dateTime->date > 31U)
    {
        return false;
    }

    if (dateTime->day < 1U || dateTime->day > 7U)
    {
        return false;
    }

    if (dateTime->hour > 23U)
    {
        return false;
    }

    if (dateTime->minute > 59U)
    {
        return false;
    }

    if (dateTime->second > 59U)
    {
        return false;
    }

    return true;
}

bool DS3231_Init(void)
{
    status_t status;

    PRINTF("[RTC] Initializing shared I2C2 access...\r\n");
    status = BSP_I2C2_InitShared();

    if (status != kStatus_Success)
    {
        PRINTF("[RTC] ERROR: Shared I2C2 initialization failed | status=%d\r\n", (int)status);
        return false;
    }

    PRINTF("[RTC] Shared I2C2 access ready.\r\n");
    return true;
}

bool DS3231_TestCommunication(void)
{
    uint8_t secondsRaw = 0U;
    uint8_t seconds;

    PRINTF("[RTC] Probing DS3231 at address 0x68...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_SECONDS,
            &secondsRaw))
    {
        PRINTF(
            "[RTC] ERROR: DS3231 not detected.\r\n"
        );

        return false;
    }

    secondsRaw &= 0x7FU;
    seconds = DS3231_BcdToDecimal(secondsRaw);

    if (seconds > 59U)
    {
        PRINTF(
            "[RTC] ERROR: Invalid seconds | raw=0x%02X | decoded=%u\r\n",
            secondsRaw,
            seconds
        );

        return false;
    }

    PRINTF(
        "[RTC] DS3231 detected successfully.\r\n"
    );

    PRINTF(
        "[RTC] Seconds register | raw=0x%02X | decoded=%u\r\n",
        secondsRaw,
        seconds
    );

    return true;
}

bool DS3231_ReadDateTime(ds3231_datetime_t *dateTime)
{
    uint8_t secondsRaw = 0U;
    uint8_t minutesRaw = 0U;
    uint8_t hoursRaw = 0U;
    uint8_t dayRaw = 0U;
    uint8_t dateRaw = 0U;
    uint8_t monthRaw = 0U;
    uint8_t yearRaw = 0U;

    if (dateTime == NULL)
    {
        return false;
    }

    PRINTF("[RTC] Reading seconds register 0x00...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_SECONDS,
            &secondsRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading minutes register 0x01...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_MINUTES,
            &minutesRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading hours register 0x02...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_HOURS,
            &hoursRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading day register 0x03...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_DAY,
            &dayRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading date register 0x04...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_DATE,
            &dateRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading month register 0x05...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_MONTH,
            &monthRaw))
    {
        return false;
    }

    PRINTF("[RTC] Reading year register 0x06...\r\n");

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_YEAR,
            &yearRaw))
    {
        return false;
    }

    dateTime->second =
        DS3231_BcdToDecimal(
            secondsRaw & 0x7FU
        );

    dateTime->minute =
        DS3231_BcdToDecimal(
            minutesRaw & 0x7FU
        );

    if ((hoursRaw & 0x40U) != 0U)
    {
        uint8_t hour12 =
            DS3231_BcdToDecimal(
                hoursRaw & 0x1FU
            );

        bool isPm =
            (hoursRaw & 0x20U) != 0U;

        if (hour12 == 12U)
        {
            dateTime->hour =
                isPm ? 12U : 0U;
        }
        else
        {
            dateTime->hour =
                isPm
                    ? (uint8_t)(hour12 + 12U)
                    : hour12;
        }
    }
    else
    {
        dateTime->hour =
            DS3231_BcdToDecimal(
                hoursRaw & 0x3FU
            );
    }

    dateTime->day =
        DS3231_BcdToDecimal(
            dayRaw & 0x07U
        );

    dateTime->date =
        DS3231_BcdToDecimal(
            dateRaw & 0x3FU
        );

    dateTime->month =
        DS3231_BcdToDecimal(
            monthRaw & 0x1FU
        );

    dateTime->year =
        (uint16_t)(
            2000U +
            DS3231_BcdToDecimal(yearRaw)
        );

    if ((monthRaw & 0x80U) != 0U)
    {
        dateTime->year += 100U;
    }

    if (!DS3231_IsDateTimeRangeValid(dateTime))
    {
        PRINTF(
            "[RTC] ERROR: Invalid date/time values read from DS3231.\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Complete date/time register read successful.\r\n"
    );

    return true;
}

bool DS3231_SetDateTime(const ds3231_datetime_t *dateTime)
{
    if (!DS3231_IsDateTimeRangeValid(dateTime))
    {
        PRINTF(
            "[RTC] ERROR: Invalid date/time supplied for write.\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Writing complete date/time to DS3231...\r\n"
    );

    PRINTF(
        "[RTC] Writing seconds register 0x00...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_SECONDS,
            DS3231_DecimalToBcd(dateTime->second)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing minutes register 0x01...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_MINUTES,
            DS3231_DecimalToBcd(dateTime->minute)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing hours register 0x02...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_HOURS,
            DS3231_DecimalToBcd(dateTime->hour)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing day register 0x03...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_DAY,
            DS3231_DecimalToBcd(dateTime->day)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing date register 0x04...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_DATE,
            DS3231_DecimalToBcd(dateTime->date)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing month register 0x05...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_MONTH,
            DS3231_DecimalToBcd(dateTime->month)))
    {
        return false;
    }

    PRINTF(
        "[RTC] Writing year register 0x06...\r\n"
    );

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_YEAR,
            DS3231_DecimalToBcd(
                (uint8_t)(dateTime->year - 2000U))))
    {
        return false;
    }

    PRINTF(
        "[RTC] Date/time registers written successfully.\r\n"
    );

    if (!DS3231_ClearOscillatorStopFlag())
    {
        PRINTF(
            "[RTC] ERROR: Unable to clear oscillator stop flag.\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Date/time write completed successfully.\r\n"
    );

    return true;
}

bool DS3231_IsTimeValid(void)
{
    uint8_t statusRegister = 0U;

    PRINTF(
        "[RTC] Reading status register 0x0F...\r\n"
    );

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_STATUS,
            &statusRegister))
    {
        PRINTF(
            "[RTC] ERROR: Unable to read status register.\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Status register = 0x%02X\r\n",
        statusRegister
    );

    if ((statusRegister & DS3231_STATUS_OSF_MASK) != 0U)
    {
        PRINTF(
            "[RTC] Oscillator Stop Flag = SET\r\n"
        );

        PRINTF(
            "[RTC] RTC time status = INVALID\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Oscillator Stop Flag = CLEAR\r\n"
    );

    PRINTF(
        "[RTC] RTC time status = VALID\r\n"
    );

    return true;
}

bool DS3231_ClearOscillatorStopFlag(void)
{
    uint8_t statusRegister = 0U;

    PRINTF(
        "[RTC] Clearing Oscillator Stop Flag...\r\n"
    );

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_STATUS,
            &statusRegister))
    {
        return false;
    }

    PRINTF(
        "[RTC] Status before clear = 0x%02X\r\n",
        statusRegister
    );

    statusRegister &=
        (uint8_t)(~DS3231_STATUS_OSF_MASK);

    if (!DS3231_WriteRegister(
            DS3231_REGISTER_STATUS,
            statusRegister))
    {
        return false;
    }

    if (!DS3231_ReadRegister(
            DS3231_REGISTER_STATUS,
            &statusRegister))
    {
        return false;
    }

    PRINTF(
        "[RTC] Status after clear = 0x%02X\r\n",
        statusRegister
    );

    if ((statusRegister & DS3231_STATUS_OSF_MASK) != 0U)
    {
        PRINTF(
            "[RTC] ERROR: Oscillator Stop Flag is still set.\r\n"
        );

        return false;
    }

    PRINTF(
        "[RTC] Oscillator Stop Flag cleared successfully.\r\n"
    );

    return true;
}

void DS3231_PrintDateTime(const ds3231_datetime_t *dateTime)
{
    if (dateTime == NULL)
    {
        return;
    }

    PRINTF(
        "[RTC] Date/time = %04u-%02u-%02u %02u:%02u:%02u | day=%u\r\n",
        dateTime->year,
        dateTime->month,
        dateTime->date,
        dateTime->hour,
        dateTime->minute,
        dateTime->second,
        dateTime->day
    );
}