#include "lora.h"

#include "sni.h"

#include <stddef.h>
#include <stdint.h>


/*******************************************************************************
 * Definitions
 ******************************************************************************/

#define LORA_JAVA_SNAPSHOT_LENGTH \
    15U

#define LORA_SNAPSHOT_SEQUENCE_INDEX \
    0U

#define LORA_SNAPSHOT_NODE_ID_INDEX \
    1U

#define LORA_SNAPSHOT_NODE_TARGET_INDEX \
    2U

#define LORA_SNAPSHOT_SOIL_MOISTURE_INDEX \
    3U

#define LORA_SNAPSHOT_SOIL_TEMPERATURE_INDEX \
    4U

#define LORA_SNAPSHOT_CONDUCTIVITY_INDEX \
    5U

#define LORA_SNAPSHOT_SOIL_PH_INDEX \
    6U

#define LORA_SNAPSHOT_NITROGEN_INDEX \
    7U

#define LORA_SNAPSHOT_PHOSPHORUS_INDEX \
    8U

#define LORA_SNAPSHOT_POTASSIUM_INDEX \
    9U

#define LORA_SNAPSHOT_AIR_TEMPERATURE_INDEX \
    10U

#define LORA_SNAPSHOT_AIR_HUMIDITY_INDEX \
    11U

#define LORA_SNAPSHOT_LIGHT_INTENSITY_INDEX \
    12U

#define LORA_SNAPSHOT_RSSI_INDEX \
    13U

#define LORA_SNAPSHOT_SNR_INDEX \
    14U


/*******************************************************************************
 * Native implementation
 ******************************************************************************/

jboolean
Java_com_nxp_example_smartgreenhouse_services_lora_LoRaNative_readLatestNative(
    jint *destination)
{
    lora_latest_data_t snapshot;

    jint destinationLength;

    if (destination == NULL)
    {
        return JFALSE;
    }

    destinationLength =
        SNI_getArrayLength(
            destination
        );

    if (destinationLength <
        (jint)LORA_JAVA_SNAPSHOT_LENGTH)
    {
        return JFALSE;
    }

    if (!LORA_CopyLatestData(
            &snapshot))
    {
        return JFALSE;
    }

    destination[
        LORA_SNAPSHOT_SEQUENCE_INDEX
    ] = (jint)snapshot.sequence;


    destination[
        LORA_SNAPSHOT_NODE_ID_INDEX
    ] = (jint)snapshot.node_id;


    destination[
        LORA_SNAPSHOT_NODE_TARGET_INDEX
    ] = (jint)snapshot.node_target;


    destination[
        LORA_SNAPSHOT_SOIL_MOISTURE_INDEX
    ] = (jint)snapshot.soil_moisture;


    destination[
        LORA_SNAPSHOT_SOIL_TEMPERATURE_INDEX
    ] = (jint)snapshot.soil_temperature;


    destination[
        LORA_SNAPSHOT_CONDUCTIVITY_INDEX
    ] = (jint)snapshot.conductivity;


    destination[
        LORA_SNAPSHOT_SOIL_PH_INDEX
    ] = (jint)snapshot.soil_ph;


    destination[
        LORA_SNAPSHOT_NITROGEN_INDEX
    ] = (jint)snapshot.nitrogen;


    destination[
        LORA_SNAPSHOT_PHOSPHORUS_INDEX
    ] = (jint)snapshot.phosphorus;


    destination[
        LORA_SNAPSHOT_POTASSIUM_INDEX
    ] = (jint)snapshot.potassium;


    destination[
        LORA_SNAPSHOT_AIR_TEMPERATURE_INDEX
    ] = (jint)snapshot.air_temperature;


    destination[
        LORA_SNAPSHOT_AIR_HUMIDITY_INDEX
    ] = (jint)snapshot.air_humidity;


    destination[
        LORA_SNAPSHOT_LIGHT_INTENSITY_INDEX
    ] = (jint)snapshot.light_intensity;


    destination[
        LORA_SNAPSHOT_RSSI_INDEX
    ] = (jint)snapshot.rssi;


    destination[
        LORA_SNAPSHOT_SNR_INDEX
    ] = (jint)snapshot.snr_quarter_db;


    return JTRUE;
}