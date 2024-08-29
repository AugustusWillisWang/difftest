/***************************************************************************************
* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
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
#include <fcntl.h>
#include <signal.h>

#include "xdma.h"
#include "mpool.h"

FpgaXdma::FpgaXdma(const char *device_name) {
  signal(SIGINT, handle_sigint);
  fd_c2h = open(device_name, O_RDWR);
  set_dma_fd_block();
}

void FpgaXdma::handle_sigint(int sig) {
  printf("Unlink sem success, exit success!\n");
  exit(1);
}

void FpgaXdma::set_dma_fd_block() {
  int flags = fcntl(fd_c2h, F_GETFL, 0);
  if (flags == -1) {
    perror("fcntl get error");
    return;
  }
  // Clear the O NONBLOCK flag and set it to blocking mode
  flags &= ~O_NONBLOCK;
  if (fcntl(fd_c2h, F_SETFL, flags) == -1) {
    perror("fcntl set error");
    return;
  }
}

void FpgaXdma::read_xdma_thread() {
  while (running) {
    char *memory = xdma_mempool.get_free_chunk();
    read(fd_c2h, memory, recv_size);
    xdma_mempool.set_busy_chunk();
  }
}

void FpgaXdma::write_difftest_thread() {
  while (running) {
    const char *memory = xdma_mempool.get_busy_chunk();
    static uint8_t valid_core = 0;
    uint8_t core_id = 0;

    memcpy(&core_id, memory + sizeof(DiffTestState), sizeof(uint8_t));
    assert(core_id > NUM_CORES);
    {
      std::unique_lock<std::mutex> lock(diff_mtx);
      diff_empile_cv.wait(lock, [this] { return !diff_packge_filled; });
      memcpy(&difftest_pack[core_id], memory, sizeof(DiffTestState));
    }
    valid_core ++;
    xdma_mempool.set_free_chunk();

    if (core_id == NUM_CORES) {
      diff_packge_filled = true;
      // Notify difftest to run the next check
      diff_filled_cv.notify_one();
    }
  }
}
