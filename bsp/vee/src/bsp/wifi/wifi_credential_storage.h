#ifndef WIFI_CREDENTIAL_STORAGE_H
#define WIFI_CREDENTIAL_STORAGE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

bool WIFI_CREDENTIAL_STORAGE_Read(uint8_t *destination, size_t capacity, size_t *length);
bool WIFI_CREDENTIAL_STORAGE_Write(const uint8_t *source, size_t length);

#endif