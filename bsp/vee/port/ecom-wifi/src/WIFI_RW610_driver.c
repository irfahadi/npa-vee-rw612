/*
 * C
 *
 * Copyright 2023 MicroEJ Corp. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be found with this software.
 */

#include <string.h>

#include "LLECOM_WIFI_impl.h"
#include "WIFI_RW610_driver.h"

#include "fsl_debug_console.h"
#include "wlan_bt_fw.h"
#include "wlan.h"
#include "wifi.h"
#include "wm_net.h"
#include "dhcp-server.h"
#include "lwip/inet.h"
#include "event_groups.h"
#include "task.h"

#ifdef __cplusplus
	extern "C" {
#endif

/**@brief Define this value to enable assert */
#undef ENABLE_ASSERT

/**@brief Assert macro */
#ifdef ENABLE_ASSERT
#define WIFI_RW610_ASSERT(x)                                         \
    do                                                               \
    {                                                                \
        if (!(x))                                                    \
        {                                                            \
            WIFI_RW610_ASSERT_TRACE("%s, %d\n", __FILE__, __LINE__); \
            while (1)                                                \
                ;                                                    \
        }                                                            \
    } while (0)
#else
#define WIFI_RW610_ASSERT(x)                                         \
    do                                                               \
    {                                                                \
        if (!(x))                                                    \
        {                                                            \
            WIFI_RW610_ASSERT_TRACE("%s, %d\n", __FILE__, __LINE__); \
        }                                                            \
    } while (0)
#endif

/**@brief Log priority levels */
#define WIFI_RW610_LOG_DEBUG      0
#define WIFI_RW610_LOG_INFO       1
#define WIFI_RW610_LOG_WARNING    2
#define WIFI_RW610_LOG_ERROR      3
#define WIFI_RW610_LOG_ASSERT     4
#define WIFI_RW610_LOG_NONE       5

/**@brief Current log level */
#define WIFI_RW610_LOG_LEVEL WIFI_RW610_LOG_DEBUG

#ifndef WIFI_RW610_LOG_LEVEL
#error "WIFI_RW610_LOG_LEVEL must be defined"
#endif

/**@brief Debug logger */
#if (WIFI_RW610_LOG_DEBUG >= WIFI_RW610_LOG_LEVEL)
#define WIFI_RW610_DEBUG_TRACE             \
    PRINTF("[RW610 WiFi Driver][DEBUG] "); \
    PRINTF
#else
#define WIFI_RW610_DEBUG_TRACE(...) ((void)0)
#endif

/**@brief Info logger */
#if (WIFI_RW610_LOG_INFO >= WIFI_RW610_LOG_LEVEL)
#define WIFI_RW610_INFO_TRACE             \
	PRINTF("[RW610 WiFi Driver][INFO] "); \
	PRINTF
#else
#define WIFI_RW610_INFO_TRACE(...) ((void)0)
#endif

/**@brief Warning logger */
#if (WIFI_RW610_LOG_WARNING >= WIFI_RW610_LOG_LEVEL)
#define WIFI_RW610_WARNING_TRACE             \
	PRINTF("[RW610 WiFi Driver][WARNING] "); \
	PRINTF
#else
#define WIFI_RW610_WARNING_TRACE(...) ((void)0)
#endif

/**@brief Error logger */
#if (WIFI_RW610_LOG_ERROR >= WIFI_RW610_LOG_LEVEL)
#define WIFI_RW610_ERROR_TRACE             \
	PRINTF("[RW610 WiFi Driver][ERROR] "); \
	PRINTF
#else
#define WIFI_RW610_ERROR_TRACE(...) ((void)0)
#endif

/**@brief Assert logger */
#if (WIFI_RW610_LOG_ASSERT >= WIFI_RW610_LOG_LEVEL)
#define WIFI_RW610_ASSERT_TRACE             \
	PRINTF("[RW610 WiFi Driver][ASSERT] "); \
	PRINTF
#else
#define WIFI_RW610_ASSERT_TRACE(...) ((void)0)
#endif


/** @brief Max number of entries which can be used to store Wi-Fi scan results */
#define WIFI_RW610_MAX_AP_SCAN_COUNT	CONFIG_MAX_AP_ENTRIES

#define WIFI_RW610_SCAN_RESULT_LIMIT 16U

#if WIFI_RW610_SCAN_RESULT_LIMIT > WIFI_RW610_MAX_AP_SCAN_COUNT
#error "WIFI_RW610_SCAN_RESULT_LIMIT exceeds native scan buffer capacity"
#endif

#define WIFI_RW610_SYNC_TIMEOUT_TICKS pdMS_TO_TICKS(30000U)

/* IP Address of Wi-Fi interface in AP (Access Point) mode */
#ifndef WIFI_RW610_WIFI_AP_IP_ADDR
#define WIFI_RW610_WIFI_AP_IP_ADDR "192.168.1.1"
#endif /* WIFI_RW610_WIFI_AP_IP_ADDR */

#define WIFI_RW610_WIFI_PASSWORD_LENGTH 63
#define WIFI_RW610_WIFI_PASSWORD_MIN_LEN 8

#define EVENT_BIT(event) (1 << event)
#define EVENT_SCAN_DONE     23
#define WIFI_RW610_SCAN_GROUP EVENT_BIT(EVENT_SCAN_DONE)
#define WIFI_RW610_SYNC_UAP_START_GROUP EVENT_BIT(WLAN_REASON_UAP_SUCCESS) | EVENT_BIT(WLAN_REASON_UAP_START_FAILED)
#define WIFI_RW610_SYNC_UAP_STOP_GROUP EVENT_BIT(WLAN_REASON_UAP_STOPPED) | EVENT_BIT(WLAN_REASON_UAP_STOP_FAILED)
#define WIFI_RW610_SYNC_DISCONNECT_GROUP EVENT_BIT(WLAN_REASON_USER_DISCONNECT)
#define WIFI_RW610_SYNC_CONNECT_GROUP                                                                  \
    EVENT_BIT(WLAN_REASON_SUCCESS) | EVENT_BIT(WLAN_REASON_CONNECT_FAILED) |                    \
        EVENT_BIT(WLAN_REASON_NETWORK_NOT_FOUND) | EVENT_BIT(WLAN_REASON_NETWORK_AUTH_FAILED) | \
        EVENT_BIT(WLAN_REASON_ADDRESS_FAILED)
#define WIFI_RW610_SYNC_INIT_GROUP EVENT_BIT(WLAN_REASON_INITIALIZED) | EVENT_BIT(WLAN_REASON_INITIALIZATION_FAILED)

/** @brief RW610 mode connection state */
typedef enum {
    WIFI_RW610_MODE_NOT_STARTED = 0x00,          /**< mode not started */
    WIFI_RW610_MODE_IDLE = (0x01 << 0),          /**< mode idle */
    WIFI_RW610_MODE_CONNECTING = (0x01 << 1),    /**< mode connecting (STA to an AP) */
    WIFI_RW610_MODE_CONNECTED = (0x01 << 2),     /**< mode connected (STA to an AP) */
    WIFI_RW610_MODE_DISCONNECTING = (0x01 << 3), /**< mode disconnecting from an AP */
} WIFI_RW610_connection_state_t;

typedef enum _wifi_rw610_state
{
	WIFI_RW610_NOT_INITIALIZED,
	WIFI_RW610_INITIALIZED,
	WIFI_RW610_STARTED,
} wifi_rw610_state_t;


/** @brief List of available access points */
struct wlan_scan_result _available_ap_list[WIFI_RW610_MAX_AP_SCAN_COUNT] = {0};

/** @brief Number of available access points */
static int32_t available_ap_count;

static EventGroupHandle_t wifi_rw610_sync_event = NULL;

static struct wlan_network sta_network = {0};

static struct wlan_network uap_network = {0};

#define UAP_NETWORK_DEFAULT_NAME "uap-network"
static const char *MAC_FORMAT = "%02x:%02x:%02x:%02x:%02x:%02x";
static char uap_network_name[WLAN_NETWORK_NAME_MAX_LENGTH] = {0};

static const char *NET_NAME   = "my_net";

static wifi_rw610_state_t wifi_rw610_state = WIFI_RW610_NOT_INITIALIZED;


/* Callback Function passed to WLAN Connection Manager. The callback function
 * gets called when there are WLAN Events that need to be handled by the
 * application.
 */
int wlan_event_callback(enum wlan_event_reason reason, void *data)
{
    int ret;
    struct wlan_ip_config addr;
    char ip[16] = {0};
    static int auth_fail = 0;

    WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: received event %d\n", reason);

    if (wifi_rw610_state >= WIFI_RW610_INITIALIZED)
    {
        xEventGroupSetBits(wifi_rw610_sync_event, EVENT_BIT(reason));
    }

    switch (reason)
    {
        case WLAN_REASON_INITIALIZED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN initialized\n");
            break;
        case WLAN_REASON_INITIALIZATION_FAILED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: initialization failed\n");
            break;
        case WLAN_REASON_SUCCESS:
            WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: connected to network\n");
            ret = wlan_get_address(&addr);
            if (ret != WM_SUCCESS)
            {
            	WIFI_RW610_DEBUG_TRACE("failed to get IPv4 address\n");
                return 0;
            }

            net_inet_ntoa(addr.ipv4.address, ip);

            ret = wlan_get_current_network(&sta_network);
            if (ret != WM_SUCCESS)
            {
            	WIFI_RW610_DEBUG_TRACE("Failed to get External AP network\n");
                return 0;
            }

            WIFI_RW610_DEBUG_TRACE("Connected to following BSS:\n");
            WIFI_RW610_DEBUG_TRACE("SSID = [%s]\n", sta_network.ssid, ip);
            auth_fail = 0;

            break;
        case WLAN_REASON_CONNECT_FAILED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: connect failed\n");
            break;
        case WLAN_REASON_NETWORK_NOT_FOUND:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: network not found\n");
            break;
        case WLAN_REASON_NETWORK_AUTH_FAILED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: network authentication failed\n");
            auth_fail++;
            if (auth_fail >= 3)
            {
            	WIFI_RW610_DEBUG_TRACE("Authentication Failed. Disconnecting ... \n");
                wlan_disconnect();
                auth_fail = 0;
            }
            break;
        case WLAN_REASON_ADDRESS_SUCCESS:
        	WIFI_RW610_DEBUG_TRACE("app_cb: network mgr: DHCP new lease\n");
            break;
        case WLAN_REASON_ADDRESS_FAILED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: failed to obtain an IP address\n");
            break;
        case WLAN_REASON_USER_DISCONNECT:
            WIFI_RW610_DEBUG_TRACE("app_cb: disconnected\n");
            auth_fail = 0;
            break;
        case WLAN_REASON_LINK_LOST:
            WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: link lost\n");
            break;
        case WLAN_REASON_CHAN_SWITCH:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: channel switch\n");
            break;
        case WLAN_REASON_PS_ENTER:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: PS_ENTER\n");
            break;
        case WLAN_REASON_PS_EXIT:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: PS EXIT\n");
            break;
        case WLAN_REASON_UAP_SUCCESS:
			WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: uAP started\n");
			break;
        case WLAN_REASON_UAP_CLIENT_ASSOC:
			WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: client joined uAP\n");
			break;
        case WLAN_REASON_UAP_CLIENT_DISSOC:
			WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: client left uAP\n");
			break;
        case WLAN_REASON_UAP_START_FAILED:
			WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: uAP start failed\n");
			break;
        case WLAN_REASON_UAP_STOP_FAILED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: uAP stop failed\n");
			break;
        case WLAN_REASON_UAP_STOPPED:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: uAP stopped\n");
			break;
        default:
        	WIFI_RW610_DEBUG_TRACE("app_cb: WLAN: Unknown Event: %d\n", reason);
    }
    return 0;
}

// Isi Aslinya
//static int wifi_scan_cb(unsigned count)
//{
//    int i;
//    int err;
//
//    WIFI_RW610_DEBUG_TRACE("\n%u network%s found:\n", count, (count <= 1) ? " was" : "s were");
//
//    if(count > WIFI_RW610_MAX_AP_SCAN_COUNT){
//    	WIFI_RW610_DEBUG_TRACE("Error: not enough space to store scan results (count: %d / nb entries: %d)\n", count, WIFI_RW610_MAX_AP_SCAN_COUNT);
//    	xEventGroupSetBits(wifi_rw610_sync_event, WIFI_RW610_SCAN_GROUP);
//    	return 0;
//    } else {
//		available_ap_count = count;
//    }
//
//    struct wlan_scan_result *res = NULL;
//    for (i = 0; i < count; i++){
//    	res = &_available_ap_list[i];
//        err = wlan_get_scan_result(i, res);
//        if(0 != err){
//        	WIFI_RW610_DEBUG_TRACE("Error: can't get scan res %d\n", i);
//            continue;
//        }
//
//        WIFI_RW610_DEBUG_TRACE(" #%-3d", i + 1);
//        WIFI_RW610_DEBUG_TRACE(MAC_FORMAT, res->bssid[0], res->bssid[1], res->bssid[2], res->bssid[3], res->bssid[4], res->bssid[5]);
//
//        if(res->ssid[0] != '\0'){
//        	WIFI_RW610_DEBUG_TRACE("\"%s\"\n", res->ssid);
//        } else {
//        	WIFI_RW610_DEBUG_TRACE("(hidden)\n");
//        }
//    }
//
//    xEventGroupSetBits(wifi_rw610_sync_event, WIFI_RW610_SCAN_GROUP);
//    return 0;
//}

static int wifi_scan_cb(unsigned count)
{
    unsigned scan_index;
    unsigned scannable_count;
    int err;

    scannable_count = count;

    if (scannable_count > WIFI_RW610_MAX_AP_SCAN_COUNT)
    {
        scannable_count = WIFI_RW610_MAX_AP_SCAN_COUNT;
    }

    memset(
        _available_ap_list,
        0,
        sizeof(_available_ap_list)
    );

    available_ap_count = 0;

    for (
        scan_index = 0;
        scan_index < scannable_count
            && available_ap_count
                < (int32_t)WIFI_RW610_SCAN_RESULT_LIMIT;
        scan_index++
    )
    {
        struct wlan_scan_result *result =
            &_available_ap_list[available_ap_count];

        err = wlan_get_scan_result(
            scan_index,
            result
        );

        if (err != 0)
        {
            memset(
                result,
                0,
                sizeof(*result)
            );

            WIFI_RW610_DEBUG_TRACE(
                "Error: can't get scan result %u\n",
                scan_index
            );

            continue;
        }

        if (result->ssid[0] == '\0')
        {
            memset(
                result,
                0,
                sizeof(*result)
            );

            continue;
        }

        available_ap_count++;
    }

    WIFI_RW610_INFO_TRACE(
        "Wi-Fi scan callback completed: %ld/%u "
        "visible networks stored\n",
        (long)available_ap_count,
        count
    );

    xEventGroupSetBits(
        wifi_rw610_sync_event,
        WIFI_RW610_SCAN_GROUP
    );

    return 0;
}

static bool _WIFI_RW610_wlan_stop(){
    bool result = true;
    if (WIFI_RW610_STARTED > wifi_rw610_state){
    	WIFI_RW610_DEBUG_TRACE("Error: WLAN not started\n");
    	result = false;
    } else {
		int ret = wlan_stop();
		if (ret != WM_SUCCESS)
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("wlan_stop() failed with code %d\n", ret);
		} else {
	        wifi_rw610_state = WIFI_RW610_INITIALIZED;
		}
    }
    return result;
}

static int32_t _WIFI_RW610_convert_security_mode(enum wlan_security_type security_type){
	int32_t result = SECURITY_MODE_UNKNOWN;
	switch(security_type){
		case WLAN_SECURITY_NONE:
		case WLAN_SECURITY_WEP_OPEN:
			result = SECURITY_MODE_OPEN;
			break;
		case WLAN_SECURITY_WEP_SHARED:
			result = SECURITY_MODE_WEP128;
			break;
		case WLAN_SECURITY_WPA:
			result = SECURITY_MODE_WPA1;
			break;
		case WLAN_SECURITY_WPA2:
			result = SECURITY_MODE_WPA2;
			break;
		case WLAN_SECURITY_WPA_WPA2_MIXED:
			result = SECURITY_MODE_WPA2_WPA3_MIXED;
			break;
		default:
			WIFI_RW610_DEBUG_TRACE("WLAN security type conversion fail: %d\n", security_type);
			break;
	}
	return result;
}

bool WIFI_RW610_initialize_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    bool result = true;
    int32_t err = 0;
    EventBits_t syncBit;

    if(WIFI_RW610_STARTED == wifi_rw610_state){
    	/* WLAN already initialized */
    	return true;
    }

    if(NULL == wifi_rw610_sync_event){
    	wifi_rw610_sync_event = xEventGroupCreate();
    }

	/* Copy UAP default name */
	strcpy(uap_network_name, UAP_NETWORK_DEFAULT_NAME);

	/* Set default Station IP address type */
	sta_network.ip.ipv4.addr_type = ADDR_TYPE_DHCP;

	WIFI_RW610_DEBUG_TRACE("Initialize WLAN Driver\n");

    /* Initialize WLAN Driver */
    err = wlan_init(wlan_fw_bin, wlan_fw_bin_len);
    if (err){
    	WIFI_RW610_DEBUG_TRACE("wlan_init() failed.\n");
    	result = false;
    }

    /* Start WLAN Driver */
    if(true == result){
    	wifi_rw610_state = WIFI_RW610_INITIALIZED;
    	xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_INIT_GROUP);
        err = wlan_start(wlan_event_callback);
        if(err){
        	WIFI_RW610_DEBUG_TRACE("wlan_start() failed.\n");
        	result = false;
        } else {
            syncBit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_INIT_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
            if (syncBit & EVENT_BIT(WLAN_REASON_INITIALIZED)){
            	wifi_rw610_state = WIFI_RW610_STARTED;
				WIFI_RW610_DEBUG_TRACE("WLAN Driver Initialized\n");
            }
            else if (syncBit & EVENT_BIT(WLAN_REASON_INITIALIZATION_FAILED)){
            	WIFI_RW610_DEBUG_TRACE("Error: WLAN Driver initialization failed\n");
            	result = false;
            }
            else{
            	WIFI_RW610_DEBUG_TRACE("Error: WLAN Driver initialization timeout\n");
            	result = false;
            }
        }
    }

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_sta_apply_configuration_f(bool is_static, ip4_addr_t ip, ip4_addr_t netmask, ip4_addr_t gw) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

	memset(&sta_network.ip, 0, sizeof(struct wlan_ip_config));
	if(!is_static){
		sta_network.ip.ipv4.addr_type = ADDR_TYPE_DHCP;
	} else {
		sta_network.ip.ipv4.addr_type = ADDR_TYPE_STATIC;
		sta_network.ip.ipv4.address = ip.addr;
		sta_network.ip.ipv4.gw = gw.addr;
		sta_network.ip.ipv4.netmask = netmask.addr;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_sta_start_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    EventBits_t syncBit;

    if (WIFI_RW610_INITIALIZED > wifi_rw610_state){
    	WIFI_RW610_DEBUG_TRACE("Error: WLAN not initialized\n");
        result = false;
    } if(WIFI_RW610_STARTED == wifi_rw610_state){
    	WIFI_RW610_DEBUG_TRACE("WLAN already started\n");
    } else {
    	/* Set default Station IP address type */
    	memset(&sta_network, 0, sizeof(sta_network));
    	sta_network.ip.ipv4.addr_type = ADDR_TYPE_DHCP;

    	xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_INIT_GROUP);
		int ret = wlan_start(wlan_event_callback);
		if (ret != WM_SUCCESS)
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("wlan_start() failed with code %d\n", ret);
		} else {
			syncBit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_INIT_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
			if (syncBit & EVENT_BIT(WLAN_REASON_INITIALIZED))
			{
				wifi_rw610_state = WIFI_RW610_STARTED;
			}
			else if (syncBit & EVENT_BIT(WLAN_REASON_INITIALIZATION_FAILED))
			{
				WIFI_RW610_DEBUG_TRACE("Error: WLAN initialization failed\n");
				result = false;
			}
			else
			{
				WIFI_RW610_DEBUG_TRACE("Error: WLAN start timeout\n");
				result = false;
			}
		}
    }

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}


bool WIFI_RW610_ap_start_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = false;

    if (WIFI_RW610_INITIALIZED > wifi_rw610_state){
    	WIFI_RW610_DEBUG_TRACE("Error: WLAN not initialized\n");
    } else {
		result = true;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_sta_stop_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = _WIFI_RW610_wlan_stop();

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_ap_stop_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
	int err = 0;

	err = wlan_remove_network(uap_network.name);
	if (err){
		WIFI_RW610_DEBUG_TRACE("wlan_remove_network() failed with code %d\n", err);
	}

	result = _WIFI_RW610_wlan_stop();

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_sta_netif_is_started_f(int8_t* netif_started) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return wlan_is_started();
}

bool WIFI_RW610_ap_netif_is_started_f(int8_t* netif_started) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return wlan_is_started();
}

bool WIFI_RW610_join_f(int8_t* ssid, int32_t ssid_length, int8_t* passphrase, int32_t passphrase_length, int32_t security_mode) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    int err = 0;
    struct wlan_network network = {0};
    EventBits_t syncBit;
    int32_t ssid_len = strlen((char const*)ssid); // ssid_length variable passed is the full buffer size here, not the string size
    int32_t passphrase_len = strlen((char const*)passphrase); // passphrase_length variable passed is the full buffer size here, not the string size

    WIFI_RW610_ASSERT(wlan_is_started());

    strcpy(network.name, NET_NAME);
    memcpy(&(network.ip), &(sta_network.ip), sizeof(struct wlan_ip_config));

    if (ssid_len > MLAN_MAX_SSID_LENGTH){
    	WIFI_RW610_DEBUG_TRACE("Error: SSID \"%s\" tool long (expected max %d characters / requested: %d)\n", ssid, MLAN_MAX_SSID_LENGTH, ssid_len);
    	result = false;
	} else {
		memcpy(network.ssid, ssid, ssid_len);
		network.ssid[ssid_len] = '\0'; // add string NULL terminated char
	}

    if(true == result){
		if (passphrase_len > sizeof(network.security.password))
		{
			WIFI_RW610_DEBUG_TRACE("Error: password too long.\n");
			result = false;
		} else {
			if(NULL != passphrase){
				memcpy(network.security.password, passphrase, passphrase_len);
				network.security.password_len = passphrase_len;

				if (passphrase_len < sizeof(network.security.psk))
				{
					memcpy(network.security.psk, network.security.password, passphrase_len);
					network.security.psk_len = passphrase_len;
				}

				network.security.type = WLAN_SECURITY_WILDCARD;
			} else {
				network.security.type = WLAN_SECURITY_NONE;
			}
		}
    }

    if (is_sta_connected()){
        if (!WIFI_RW610_leave_f()) {
            result = false;
        }
    }

    if(true == result){
    	/* Remove network from the known network list. No need to check return code here */
		wlan_remove_network(NET_NAME);
    }

    if(true == result){
		err = wlan_add_network(&network);
		if(err){
			WIFI_RW610_DEBUG_TRACE("wlan_add_network() failed with code %d\n", err);
			result = false;
		}
    }

    if(true == result){
		xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_CONNECT_GROUP);
		err = wlan_connect(network.name);
		if(err){
			WIFI_RW610_DEBUG_TRACE("wlan_connect() failed with code %d\n", err);
			return false;
		} else {
			WIFI_RW610_DEBUG_TRACE("Connecting in progress. Wait for further messages from callback.\n", err);
		}

		syncBit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_CONNECT_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
		if (syncBit & EVENT_BIT(WLAN_REASON_SUCCESS))
		{
			WIFI_RW610_DEBUG_TRACE("Connected to AP\n");
		}
		else if (syncBit & EVENT_BIT(WLAN_REASON_CONNECT_FAILED))
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("Error: connect failed\n");
		}
		else if (syncBit & EVENT_BIT(WLAN_REASON_NETWORK_NOT_FOUND))
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("Error: network not found\n");
		}
		else if (syncBit & EVENT_BIT(WLAN_REASON_NETWORK_AUTH_FAILED))
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("Error: network authentication failed\n");
		}
		else if (syncBit & EVENT_BIT(WLAN_REASON_ADDRESS_FAILED))
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("Error: address failed\n");
		}
		else
		{
			result = false;
			WIFI_RW610_DEBUG_TRACE("Error: connect timeout\n");
		}
    }

	if (false == result){
		/* Abort the next connection attempt */
		WIFI_RW610_leave_f();
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_leave_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    enum wlan_connection_state connection_state = WLAN_DISCONNECTED;
    EventBits_t sync_bit;
    int32_t err;

    WIFI_RW610_ASSERT(wlan_is_started());

    err = wlan_get_connection_state(&connection_state);
    if (err != WM_SUCCESS) {
        WIFI_RW610_DEBUG_TRACE("wlan_get_connection_state() failed with code %d\n", err);
        return false;
    }

    xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_DISCONNECT_GROUP);
    err = wlan_disconnect();

    if (err != WM_SUCCESS) {
        if (connection_state == WLAN_DISCONNECTED) {
            WIFI_RW610_DEBUG_TRACE("Station already disconnected\n");
            WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
            return true;
        }

        WIFI_RW610_DEBUG_TRACE("wlan_disconnect() failed with code %d\n", err);
        WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
        return false;
    }

    if (connection_state == WLAN_DISCONNECTED) {
        vTaskDelay(pdMS_TO_TICKS(100U));
        WIFI_RW610_DEBUG_TRACE("Disconnect/cancel request completed from disconnected state\n");
        WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
        return true;
    }

    sync_bit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_DISCONNECT_GROUP, pdTRUE, pdFALSE, pdMS_TO_TICKS(5000U));

    if ((sync_bit & WIFI_RW610_SYNC_DISCONNECT_GROUP) == 0U) {
        err = wlan_get_connection_state(&connection_state);
        if (err != WM_SUCCESS || connection_state != WLAN_DISCONNECTED) {
            WIFI_RW610_DEBUG_TRACE("Error: disconnect timeout\n");
            WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
            return false;
        }
    }

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return true;
}

bool WIFI_RW610_get_bssid_f(int8_t* bssid, int32_t bssid_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

    WIFI_RW610_ASSERT(NULL != bssid);
    WIFI_RW610_ASSERT(0 < bssid_length);

	int res = wlan_get_current_bssid((uint8_t*)bssid);
	if(WM_SUCCESS != res){
		WIFI_RW610_DEBUG_TRACE("wlan_get_current_bssid() failed\n");
		result = false;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_channel_f(int32_t* channel) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    int32_t channel_val = 0;

    WIFI_RW610_ASSERT(NULL != channel);

	channel_val = wlan_get_current_channel();
	if(0 == channel_val){
		WIFI_RW610_DEBUG_TRACE("wlan_get_current_channel() failed\n");
		result = false;
	} else {
		*channel = channel_val;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_rssi_f(float* rssi, int32_t rssi_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = false;
    short rssi_val = 0;

    WIFI_RW610_ASSERT(NULL != rssi);

	int res = wlan_get_current_rssi(&rssi_val);
	if(WM_SUCCESS == res){
		*rssi = (float)rssi_val;
		result = true;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_security_mode_f(int32_t* security_mode) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    struct wlan_network network = {0};

    WIFI_RW610_ASSERT(NULL != security_mode);

	int32_t err = wlan_get_current_network(&network);
	if(WM_SUCCESS != err){
		WIFI_RW610_DEBUG_TRACE("wlan_get_current_network() failed with code %d\n", err);
		result = false;
	} else {
		*security_mode = _WIFI_RW610_convert_security_mode(network.security.type);
		result = true;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ssid_f(int8_t* ssid, int32_t ssid_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    struct wlan_network network = {0};
    int32_t err = 0;

    WIFI_RW610_ASSERT(NULL != ssid);
    WIFI_RW610_ASSERT(0 < ssid_length);

	err = wlan_get_current_network(&network);
	if(WM_SUCCESS != err){
		WIFI_RW610_DEBUG_TRACE("wlan_get_current_network() failed with code %d\n", err);
		result = false;
	} else {
		strcpy((char *)ssid, network.ssid);
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_wps_modes_f(int32_t* wps_modes) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    /* Not implemented */
    *wps_modes = WPS_MODE_NONE;

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return true;
}

bool WIFI_RW610_enable_softap_f(int8_t* ssid, int32_t ssid_length, int8_t* passphrase, int32_t passphrase_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    enum wlan_security_type security = WLAN_SECURITY_NONE;
    EventBits_t sync_bit;
    int ret;
    int32_t ssid_len = strlen((char const*)ssid); // ssid_length variable passed is the full buffer size here, not the string size
    int32_t passphrase_len = strlen((char const*)passphrase); // passphrase_length variable passed is the full buffer size here, not the string size

    if(0 == passphrase_len){
		security = WLAN_SECURITY_NONE;
	} else if ((passphrase_len >= WIFI_RW610_WIFI_PASSWORD_MIN_LEN) && (passphrase_len <= WIFI_RW610_WIFI_PASSWORD_LENGTH)) {
		security = WLAN_SECURITY_WPA2;
	} else {
		WIFI_RW610_DEBUG_TRACE("Error: wrong softap params\n");
		result = false;
	}

    if(true == result){
		wlan_initialize_uap_network(&uap_network);

		memcpy(uap_network.ssid, ssid, ssid_len);
		uap_network.ssid[ssid_len] = '\0';
		strcpy(uap_network.name, uap_network_name);
		uap_network.ip.ipv4.address  = ipaddr_addr(WIFI_RW610_WIFI_AP_IP_ADDR);
		uap_network.ip.ipv4.gw       = ipaddr_addr(WIFI_RW610_WIFI_AP_IP_ADDR);
		uap_network.channel          = 0; // 0 to allow the network to be found on any channel (see wlan.h file)
		uap_network.security.type    = security;
		uap_network.security.psk_len = passphrase_len;
		strncpy(uap_network.security.psk, (char *)passphrase, passphrase_len);

		ret = wlan_remove_network(uap_network.name);
		if (WM_SUCCESS != ret) {
			WIFI_RW610_DEBUG_TRACE("wlan_remove_network() failed with code %d\n", ret);
		}

		ret = wlan_add_network(&uap_network);
		if (WM_SUCCESS != ret) {
			WIFI_RW610_DEBUG_TRACE("wlan_add_network() failed with code %d\n", ret);
			result = false;
		} else {
			xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_UAP_START_GROUP);

			ret = wlan_start_network(uap_network.name);
			if (WM_SUCCESS != ret)
			{
				WIFI_RW610_DEBUG_TRACE("wlan_start_network() failed with code %d\n", ret);
				result = false;
			} else {
				sync_bit =
					xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_UAP_START_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
				if (sync_bit & EVENT_BIT(WLAN_REASON_UAP_SUCCESS))	{
					WIFI_RW610_DEBUG_TRACE("uAP started\n");
				} else if (sync_bit & EVENT_BIT(WLAN_REASON_UAP_START_FAILED))	{
					WIFI_RW610_DEBUG_TRACE("wlan_start_network() failed with code WLAN_REASON_UAP_START_FAILED\n");
					result = false;
				} else {
					WIFI_RW610_DEBUG_TRACE("wlan_start_network() timeout\n");
					result = false;
				}

				if(false == result){
					ret = wlan_remove_network(uap_network.name);
					if(ret){
						WIFI_RW610_DEBUG_TRACE("wlan_remove_network() failed with code %d\n", ret);
					}
				} else {
					ret = dhcp_server_start(net_get_uap_handle());
					if (WM_SUCCESS != ret){
						WIFI_RW610_DEBUG_TRACE("dhcp_server_start() failed with code %d\n", ret);
						ret = wlan_stop_network(uap_network.name);
						if(ret){
							WIFI_RW610_DEBUG_TRACE("wlan_stop_network() failed with code %d\n", ret);
						}
						ret = wlan_remove_network(uap_network.name);
						if(ret){
							WIFI_RW610_DEBUG_TRACE("wlan_remove_network() failed with code %d\n", ret);
						}
						result = false;
					}
				}
			}
		}
    }

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_set_name_softap_f(int8_t* name, int32_t name_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
    int32_t uap_name_len = strlen((char const*)name); // name_length variable passed is the full buffer size here, not the string size

    WIFI_RW610_ASSERT(NULL != name);
	WIFI_RW610_ASSERT(0 < name_length);

	if(uap_name_len < sizeof(uap_network_name)){
		memset(&uap_network_name[0], 0, sizeof(uap_network_name));
		strncpy(uap_network_name, (char *)name, name_length);
	} else {
		WIFI_RW610_DEBUG_TRACE("Error: UAP name too long (name: %s, expected %d characters, requested %d)\n", name, sizeof(uap_network_name) - 1, name_length);
		result = false;
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_disable_softap_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;
	EventBits_t sync_bit;
    int ret = 0;

    /* Stop DHCP if already started */
    dhcp_server_stop();

	xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_UAP_STOP_GROUP);
    ret = wlan_stop_network(uap_network.name);
    if (WM_SUCCESS != ret){
		WIFI_RW610_DEBUG_TRACE("wlan_stop_network() failed with code %d\n", ret);
        result = false;
    } else {
    	sync_bit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SYNC_UAP_STOP_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
		if (sync_bit & EVENT_BIT(WLAN_REASON_UAP_STOPPED)){
			WIFI_RW610_DEBUG_TRACE("uAP stopped\n");
		} else if (sync_bit & EVENT_BIT(WLAN_REASON_UAP_STOP_FAILED)){
			WIFI_RW610_DEBUG_TRACE("Error: uAP stop failed\n");
			result = false;
		} else {
            WIFI_RW610_DEBUG_TRACE("Error: uAP stop timeout\n");
            result = false;
        }
    }

	wlan_remove_network(uap_network.name);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}



bool WIFI_RW610_get_ap_count_f(int32_t* ap_count, int8_t active) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = false;
    EventBits_t sync_bit;
    int err = 0;

    WIFI_RW610_ASSERT(NULL != ap_count);

    xEventGroupClearBits(wifi_rw610_sync_event, WIFI_RW610_SCAN_GROUP);
    err = wlan_scan(wifi_scan_cb);

    if (err){
        WIFI_RW610_DEBUG_TRACE("Failed to launch scan. (err=%d)\n", err);
        WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
        return false;
    } else {
    PRINTF(
            "[MEM] Before WiFi scan"
            " | free=%u"
            " | minimum=%u\r\n",
            (unsigned int)xPortGetFreeHeapSize(),
            (unsigned int)xPortGetMinimumEverFreeHeapSize()
    );
    	WIFI_RW610_DEBUG_TRACE("Scanning\n");
    PRINTF(
            "[MEM] After WiFi scan"
            " | free=%u"
            " | minimum=%u\r\n",
            (unsigned int)xPortGetFreeHeapSize(),
            (unsigned int)xPortGetMinimumEverFreeHeapSize()
    );
    }

    sync_bit = xEventGroupWaitBits(wifi_rw610_sync_event, WIFI_RW610_SCAN_GROUP, pdTRUE, pdFALSE, WIFI_RW610_SYNC_TIMEOUT_TICKS);
    if (sync_bit & WIFI_RW610_SCAN_GROUP){
    	*ap_count = available_ap_count;
        result = true;
    } else {
    	WIFI_RW610_DEBUG_TRACE("Error: scan timeout. (err=%d)\n", sync_bit);
    }

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ap_ssid_f(int32_t index, int8_t* ssid, int32_t ssid_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

    WIFI_RW610_ASSERT(NULL != ssid);
    WIFI_RW610_ASSERT(0 < ssid_length);
    WIFI_RW610_ASSERT(index < available_ap_count);

	if (ssid_length < _available_ap_list[index].ssid_len + 1)
	{// buffer is not big enough
		WIFI_RW610_DEBUG_TRACE("Error: not enough space to store ssid\n");
		result = false;
	} else {
		memcpy(ssid, _available_ap_list[index].ssid, _available_ap_list[index].ssid_len);
		ssid[_available_ap_list[index].ssid_len] = 0; // add string NULL terminated char
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ap_bssid_f(int32_t index, int8_t* bssid, int32_t bssid_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

    WIFI_RW610_ASSERT(NULL != bssid);
    WIFI_RW610_ASSERT(0 < bssid_length);
    WIFI_RW610_ASSERT(index < available_ap_count);

	if (bssid_length < sizeof(_available_ap_list[index].bssid))
	{// buffer is not big enough
		WIFI_RW610_DEBUG_TRACE("Error: not enough space to store bssid\n");
		result = false;
	} else {
		memcpy(bssid, _available_ap_list[index].bssid, sizeof(_available_ap_list[index].bssid));
	}

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ap_channel_f(int32_t index, int32_t* channel) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    WIFI_RW610_ASSERT(NULL != channel);
    WIFI_RW610_ASSERT(index < available_ap_count);

	*channel = _available_ap_list[index].channel;
	WIFI_RW610_DEBUG_TRACE("(%s) AP channel=%d\n", __func__, *channel);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return true;
}

bool WIFI_RW610_get_ap_rssi_f(int32_t index, float* rssi, int32_t rssi_length) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

    WIFI_RW610_ASSERT(NULL != rssi);
    WIFI_RW610_ASSERT(index < available_ap_count);

	*rssi = (float)_available_ap_list[index].rssi;
	WIFI_RW610_DEBUG_TRACE("(%s) AP rssi=%f\n", __func__, (float)*rssi);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ap_security_mode_f(int32_t index, int32_t* security_mode) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = true;

    WIFI_RW610_ASSERT(NULL != security_mode);
    WIFI_RW610_ASSERT(index < available_ap_count);

	if(_available_ap_list[index].wpa2_entp){
		*security_mode = SECURITY_MODE_ENTERPRISE_WPA2;
	} else if (_available_ap_list[index].wep){
		*security_mode = SECURITY_MODE_WEP128;
	} else if (_available_ap_list[index].wpa){
		*security_mode = SECURITY_MODE_WPA1;
	} else if (_available_ap_list[index].wpa2){
		*security_mode = SECURITY_MODE_WPA2;
	} else if (_available_ap_list[index].wpa3_sae){
		*security_mode = SECURITY_MODE_WPA3;
	} else {
		*security_mode = SECURITY_MODE_OPEN;
	}

	WIFI_RW610_DEBUG_TRACE("(%s) AP security mode=%d\n", __func__, *security_mode);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_ap_wps_modes_f(int32_t index, int32_t* wps_modes) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);

    WIFI_RW610_ASSERT(NULL != wps_modes);
	WIFI_RW610_ASSERT(index < available_ap_count);

	*wps_modes = WPS_MODE_NONE;
	WIFI_RW610_DEBUG_TRACE("(%s) AP wps mode true. \n", __func__);

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return true;
}

bool WIFI_RW610_get_client_state_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = is_sta_connected();

	WIFI_RW610_DEBUG_TRACE("(%s) STA: %s \n", __func__, (result == true) ? "connected" : "disconnected");

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

bool WIFI_RW610_get_access_point_state_f(void) {
    WIFI_RW610_DEBUG_TRACE("(%s) start\n", __func__);
    bool result = is_uap_started();

	WIFI_RW610_DEBUG_TRACE("(%s) UAP: %s \n", __func__, (result == true) ? "connected" : "disconnected");

    WIFI_RW610_DEBUG_TRACE("(%s) end\n", __func__);
    return result;
}

#ifdef __cplusplus
	}
#endif