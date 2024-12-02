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

#include <common.h>
#include <locale.h>
#include "device.h"
#include "ram.h"
#include "flash.h"
#ifndef CONFIG_NO_DIFFTEST
#include "difftest.h"
#include "goldenmem.h"
#include "refproxy.h"
#endif

static bool has_reset = false;
static char bin_file[256] = "ram.bin";
static char *flash_bin_file = NULL;
static bool enable_difftest = true;

extern "C" void set_bin_file(char *s) {
  printf("ram image:%s\n",s);
  strcpy(bin_file, s);
}

extern "C" void set_flash_bin(char *s) {
  printf("flash image:%s\n",s);
  flash_bin_file = (char *)malloc(256);
  strcpy(flash_bin_file, s);
}

extern "C" void set_diff_ref_so(char *s) {
#ifndef CONFIG_NO_DIFFTEST
  printf("diff-test ref so:%s\n", s);
  extern const char *difftest_ref_so;
  char* buf = (char *)malloc(256);
  strcpy(buf, s);
  difftest_ref_so = buf;
#endif
}

extern "C" void set_no_diff() {
  printf("disable diff-test\n");
  enable_difftest = false;
}

extern "C" void simv_init() {
  common_init("simv");

#ifdef CONFIG_USE_SPARSEMM
  simMemory = new SparseMemory(bin_file, DEFAULT_EMU_RAM_SIZE);
#else
  simMemory = new MmapMemory(bin_file, DEFAULT_EMU_RAM_SIZE);
#endif

  init_flash(flash_bin_file);
  init_device();
#ifndef CONFIG_NO_DIFFTEST
  difftest_init();
  init_goldenmem();
  init_nemuproxy(DEFAULT_EMU_RAM_SIZE);
#endif
}

extern "C" int simv_step() {
  if (assert_count > 0) {
    return 1;
  }

#ifndef CONFIG_NO_DIFFTEST
  if (difftest_state() != -1) {
    int trapCode = difftest_state();
    switch (trapCode) {
      case 0:
        eprintf(ANSI_COLOR_GREEN "HIT GOOD TRAP\n" ANSI_COLOR_RESET);
        printf("simulation result::PASS\n");
        break;
      default:
        eprintf(ANSI_COLOR_RED "Unknown trap code: %d\n" ANSI_COLOR_RESET, trapCode);
        printf("simulation result::FAIL\n");
    }
    return trapCode + 1;
  }
#endif

#ifndef CONFIG_NO_DIFFTEST
  return difftest_step();
#else
  return 0;
#endif
}

#ifdef DIFFTEST_DEFERRED_RESULT
static int simv_result = 0;
extern "C" void simv_nstep(uint8_t step) {
  if (simv_result)
    return;
  for (int i = 0; i < step; i++) {
    int ret = simv_step();
    if (ret)
      simv_result = ret;
  }
}
extern "C" int simv_result_fetch() {
  return simv_result;
}
#else
extern "C" int simv_nstep(uint8_t step) {
  for(int i = 0; i < step; i++) {
    int ret = simv_step();
    if(ret)
      return ret;
  }
  return 0;
}
#endif // DIFFTEST_DEFERRED_RESULT