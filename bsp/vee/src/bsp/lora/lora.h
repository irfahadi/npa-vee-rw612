#ifndef LORA_H
#define LORA_H

#include <stdbool.h>
#include <stdint.h>

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

    int32_t rssi;
    int32_t snr_quarter_db;

    uint32_t sequence;
    bool available;

} lora_latest_data_t;

void LORA_Start(void);

bool LORA_CopyLatestData(
    lora_latest_data_t *destination
);

#endif