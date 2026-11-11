#include "wifi_credential_storage.h"

#include "mflash_file.h"

#include <string.h>

#define WIFI_CREDENTIAL_STORAGE_MAX_SIZE 512U

static char g_wifiCredentialPath[] = "/smartgreenhouse_wifi";

static mflash_file_t g_wifiCredentialFiles[] = {
    {
        .path = g_wifiCredentialPath,
        .max_size = WIFI_CREDENTIAL_STORAGE_MAX_SIZE
    },
    {0}
};

static bool g_wifiCredentialStorageInitialized = false;

static bool WIFI_CREDENTIAL_STORAGE_EnsureInitialized(void)
{
    status_t status;

    if (g_wifiCredentialStorageInitialized)
    {
        return true;
    }

    status = mflash_init(g_wifiCredentialFiles, true);

    if (status != kStatus_Success)
    {
        return false;
    }

    g_wifiCredentialStorageInitialized = true;

    return true;
}

bool WIFI_CREDENTIAL_STORAGE_Read(uint8_t *destination, size_t capacity, size_t *length)
{
    uint8_t *storedData = NULL;
    uint32_t storedLength = 0U;
    status_t status;

    if (destination == NULL || length == NULL)
    {
        return false;
    }

    *length = 0U;

    if (!WIFI_CREDENTIAL_STORAGE_EnsureInitialized())
    {
        return false;
    }

    status = mflash_file_mmap(g_wifiCredentialPath, &storedData, &storedLength);

    if (status != kStatus_Success)
    {
        return false;
    }

    if (storedData == NULL || storedLength == 0U)
    {
        return true;
    }

    if ((size_t)storedLength > capacity)
    {
        return false;
    }

    memcpy(destination, storedData, storedLength);
    *length = (size_t)storedLength;

    return true;
}

bool WIFI_CREDENTIAL_STORAGE_Write(const uint8_t *source, size_t length)
{
    status_t status;

    if (source == NULL || length == 0U || length > WIFI_CREDENTIAL_STORAGE_MAX_SIZE)
    {
        return false;
    }

    if (!WIFI_CREDENTIAL_STORAGE_EnsureInitialized())
    {
        return false;
    }

    status = mflash_file_save(g_wifiCredentialPath, (uint8_t *)source, (uint32_t)length);

    return status == kStatus_Success;
}