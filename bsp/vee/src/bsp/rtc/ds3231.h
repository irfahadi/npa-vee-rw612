#ifndef DS3231_H
#define DS3231_H

#include <stdbool.h>
#include <stdint.h>

typedef struct
{
    uint16_t year;
    uint8_t month;
    uint8_t date;
    uint8_t day;
    uint8_t hour;
    uint8_t minute;
    uint8_t second;
} ds3231_datetime_t;

bool DS3231_Init(void);
bool DS3231_TestCommunication(void);

bool DS3231_ReadDateTime(ds3231_datetime_t *dateTime);
bool DS3231_SetDateTime(const ds3231_datetime_t *dateTime);

bool DS3231_IsTimeValid(void);
bool DS3231_ClearOscillatorStopFlag(void);

void DS3231_PrintDateTime(const ds3231_datetime_t *dateTime);

#endif