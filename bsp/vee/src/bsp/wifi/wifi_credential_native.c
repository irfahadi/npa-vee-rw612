#include "wifi_credential_storage.h"

#include "sni.h"

#include <stddef.h>
#include <stdint.h>

jint Java_com_nxp_example_smartgreenhouse_services_wifi_WifiCredentialNative_readNative(jbyte *destination)
{
    jint destinationLength;
    size_t storedLength = 0U;

    if (destination == NULL)
    {
        return (jint)-1;
    }

    destinationLength = SNI_getArrayLength(destination);

    if (destinationLength <= 0)
    {
        return (jint)-1;
    }

    if (!WIFI_CREDENTIAL_STORAGE_Read((uint8_t *)destination, (size_t)destinationLength, &storedLength))
    {
        return (jint)-1;
    }

    return (jint)storedLength;
}

jboolean Java_com_nxp_example_smartgreenhouse_services_wifi_WifiCredentialNative_writeNative(jbyte *source, jint length)
{
    jint sourceLength;

    if (source == NULL || length <= 0)
    {
        return JFALSE;
    }

    sourceLength = SNI_getArrayLength(source);

    if (sourceLength < length)
    {
        return JFALSE;
    }

    if (!WIFI_CREDENTIAL_STORAGE_Write((const uint8_t *)source, (size_t)length))
    {
        return JFALSE;
    }

    return JTRUE;
}