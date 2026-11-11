#include "ds3231.h"
#include "sni.h"

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define RTC_JAVA_DATETIME_LENGTH 7U

#define RTC_INDEX_YEAR 0U
#define RTC_INDEX_MONTH 1U
#define RTC_INDEX_DATE 2U
#define RTC_INDEX_DAY 3U
#define RTC_INDEX_HOUR 4U
#define RTC_INDEX_MINUTE 5U
#define RTC_INDEX_SECOND 6U

static bool RTC_IsJavaDateTimeValid(const jint *source)
{
    if (source == NULL)
    {
        return false;
    }

    if (source[RTC_INDEX_YEAR] < 2000 ||
        source[RTC_INDEX_YEAR] > 2099)
    {
        return false;
    }

    if (source[RTC_INDEX_MONTH] < 1 ||
        source[RTC_INDEX_MONTH] > 12)
    {
        return false;
    }

    if (source[RTC_INDEX_DATE] < 1 ||
        source[RTC_INDEX_DATE] > 31)
    {
        return false;
    }

    if (source[RTC_INDEX_DAY] < 1 ||
        source[RTC_INDEX_DAY] > 7)
    {
        return false;
    }

    if (source[RTC_INDEX_HOUR] < 0 ||
        source[RTC_INDEX_HOUR] > 23)
    {
        return false;
    }

    if (source[RTC_INDEX_MINUTE] < 0 ||
        source[RTC_INDEX_MINUTE] > 59)
    {
        return false;
    }

    if (source[RTC_INDEX_SECOND] < 0 ||
        source[RTC_INDEX_SECOND] > 59)
    {
        return false;
    }

    return true;
}

jboolean
Java_com_nxp_example_smartgreenhouse_services_rtc_RtcNative_initNative(
    void)
{
    return DS3231_Init()
               ? JTRUE
               : JFALSE;
}

jboolean
Java_com_nxp_example_smartgreenhouse_services_rtc_RtcNative_isTimeValidNative(
    void)
{
    return DS3231_IsTimeValid()
               ? JTRUE
               : JFALSE;
}

jboolean
Java_com_nxp_example_smartgreenhouse_services_rtc_RtcNative_readDateTimeNative(
    jint *destination)
{
    ds3231_datetime_t dateTime;
    jint destinationLength;

    if (destination == NULL)
    {
        return JFALSE;
    }

    destinationLength =
        SNI_getArrayLength(
            destination);

    if (destinationLength <
        (jint)RTC_JAVA_DATETIME_LENGTH)
    {
        return JFALSE;
    }

    if (!DS3231_ReadDateTime(
            &dateTime))
    {
        return JFALSE;
    }

    destination[RTC_INDEX_YEAR] =
        (jint)dateTime.year;

    destination[RTC_INDEX_MONTH] =
        (jint)dateTime.month;

    destination[RTC_INDEX_DATE] =
        (jint)dateTime.date;

    destination[RTC_INDEX_DAY] =
        (jint)dateTime.day;

    destination[RTC_INDEX_HOUR] =
        (jint)dateTime.hour;

    destination[RTC_INDEX_MINUTE] =
        (jint)dateTime.minute;

    destination[RTC_INDEX_SECOND] =
        (jint)dateTime.second;

    return JTRUE;
}

jboolean
Java_com_nxp_example_smartgreenhouse_services_rtc_RtcNative_writeDateTimeNative(
    jint *source)
{
    ds3231_datetime_t dateTime;
    jint sourceLength;

    if (source == NULL)
    {
        return JFALSE;
    }

    sourceLength =
        SNI_getArrayLength(
            source);

    if (sourceLength <
        (jint)RTC_JAVA_DATETIME_LENGTH)
    {
        return JFALSE;
    }

    if (!RTC_IsJavaDateTimeValid(
            source))
    {
        return JFALSE;
    }

    dateTime.year =
        (uint16_t)source[RTC_INDEX_YEAR];

    dateTime.month =
        (uint8_t)source[RTC_INDEX_MONTH];

    dateTime.date =
        (uint8_t)source[RTC_INDEX_DATE];

    dateTime.day =
        (uint8_t)source[RTC_INDEX_DAY];

    dateTime.hour =
        (uint8_t)source[RTC_INDEX_HOUR];

    dateTime.minute =
        (uint8_t)source[RTC_INDEX_MINUTE];

    dateTime.second =
        (uint8_t)source[RTC_INDEX_SECOND];

    return DS3231_SetDateTime(
               &dateTime)
               ? JTRUE
               : JFALSE;
}