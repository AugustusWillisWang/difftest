/***************************************************************************************
* Copyright (c) 2020-2023 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* DiffTest is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "common.h"
#include "sdcard.h"

FILE *fp = NULL;

extern "C" {

void check_sdcard() {
  if (!fp) {
    eprintf(ANSI_COLOR_MAGENTA "[warning] sdcard img not found\n");
  }
}

void sd_setaddr(uint32_t addr) {
  check_sdcard();
#ifdef SDCARD_IMAGE
  fseek(fp, addr, SEEK_SET);
#endif
  //printf("set addr to 0x%08x\n", addr);
  //assert(0);
}

void sd_read(uint32_t *data) {
  check_sdcard();
#ifdef SDCARD_IMAGE
  fread(data, 4, 1, fp);
#endif
  //printf("read data = 0x%08x\n", *data);
  //assert(0);
}

void init_sd(void) {
#ifdef SDCARD_IMAGE
  fp = fopen(SDCARD_IMAGE, "r");
  check_sdcard();
#endif
}

}
