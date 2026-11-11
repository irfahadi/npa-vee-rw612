/*
 * Copyright 2024 NXP
 * All rights reserved.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

#ifndef _DISPLAY_SUPPORT_H_
#define _DISPLAY_SUPPORT_H_

#include "panel_func.h"
#include "fsl_i2c.h"
/*******************************************************************************
 * API
 ******************************************************************************/
#if defined(__cplusplus)
extern "C" {
#endif /* __cplusplus */
/*******************************************************************************
 * Definitions
 ******************************************************************************/
#define APP_LCDIC         LCDIC
#define APP_DMA           DMA0
#define APP_LCD_TX_DMA_CH 0
#define APP_COLOR_RED   0xF800U
#define APP_COLOR_GREEN 0x07E0U
#define APP_COLOR_BLUE  0x001FU
#define APP_COLOR_WHITE 0xFFFFU

#define APP_MEM_WRITE_CMD 0x2CU


void disp_sup_pre_init(void);
void disp_sup_disp_init(void);
void disp_sup_indev_init(void);
void disp_sup_flush(uint8_t* srcAddr, uint32_t startX, uint32_t startY, uint32_t endX, uint32_t endY, uint32_t number_pixel);
uint8_t* disp_sup_get_fb_address();

status_t BSP_I2C2_InitShared(void);
status_t BSP_I2C2_MasterTransfer(i2c_master_transfer_t *transfer);

void DEMO_InitTouch(void);
void DEMO_ReadTouch(int *pressed, int *touch_x, int *touch_y);

#if defined(__cplusplus)
}
#endif /* __cplusplus */

#endif /* _DISPLAY_SUPPORT_H_ */
