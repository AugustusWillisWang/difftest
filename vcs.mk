#***************************************************************************************
# Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
# Copyright (c) 2020-2021 Peng Cheng Laboratory
#
# XiangShan is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
#
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
#
# See the Mulan PSL v2 for more details.
#***************************************************************************************

VCS_RTL = simv_rtl
VCS_PG  = simv_pg

VCS_CSRC_DIR = $(abspath ./src/test/csrc/vcs)
VCS_CXXFILES = $(SIM_CXXFILES) $(DIFFTEST_CXXFILES) $(PLUGIN_CXXFILES) $(shell find $(VCS_CSRC_DIR) -name "*.cpp")
VCS_CXXFLAGS += -std=c++11 -static -Wall -I$(VCS_CSRC_DIR) -I$(SIM_CSRC_DIR) -I$(DIFFTEST_CSRC_DIR) -I$(PLUGIN_CHEAD_DIR)
VCS_LDFLAGS  += -Wl,--no-as-needed -lpthread -lSDL2 -ldl -lz -lsqlite3

ifeq ($(RELEASE),1)
VCS_CXXFLAGS += -DBASIC_DIFFTEST_ONLY
VCS_FLAGS    += +define+SNPS_FAST_SIM_FFV +define+USE_RF_DEBUG
endif

# if fsdb is considered
# CONSIDER_FSDB ?= 0
ifeq ($(CONSIDER_FSDB),1)
EXTRA = +define+CONSIDER_FSDB
# if VERDI_HOME is not set automatically after 'module load', please set manually.
ifndef VERDI_HOME
$(error VERDI_HOME is not set. Try whereis verdi, abandon /bin/verdi and set VERID_HOME manually)
else
NOVAS_HOME = $(VERDI_HOME)
NOVAS = $(NOVAS_HOME)/share/PLI/VCS/LINUX64
EXTRA += -P $(NOVAS)/novas.tab $(NOVAS)/pli.a
endif
endif

VCS_VSRC_DIR = $(shell pwd)/src/test/vsrc/vcs
VCS_VFILES   = $(SIM_VSRC) $(shell find $(VCS_VSRC_DIR) -name "*.v")

VCS_SEARCH_DIR = $(abspath $(BUILD_DIR))
VCS_BUILD_DIR  = $(abspath $(BUILD_DIR)/simv-compile)

VCS_FLAGS += -full64 +v2k -timescale=1ns/1ns -sverilog -debug_access+all +lint=TFIPC-L -l vcs.log -top tb_top
VCS_FLAGS += -fgp -lca -kdb +nospecify +notimingcheck
# DiffTest
VCS_FLAGS += +define+DIFFTEST
# X prop
VCS_FLAGS += -xprop
# randomize all undefined signals (instead of using X)
# VCS_FLAGS += +define+RANDOMIZE_GARBAGE_ASSIGN
# VCS_FLAGS += +define+RANDOMIZE_INVALID_ASSIGN
VCS_FLAGS += +define+PRINTF_COND=1
# VCS_FLAGS += +define+RANDOMIZE_REG_INIT
# manually set RANDOMIZE_DELAY to avoid VCS from incorrect random initialize
# NOTE: RANDOMIZE_DELAY must NOT be rounded to 0
VCS_FLAGS += +define+RANDOMIZE_DELAY=1
# SRAM lib defines
# VCS_FLAGS += +define+UNIT_DELAY +define+no_warning
# C++ flags
VCS_FLAGS += -CFLAGS "$(VCS_CXXFLAGS)" -LDFLAGS "$(VCS_LDFLAGS)" -j200
# search build for other missing verilog files
VCS_FLAGS += -y $(VCS_SEARCH_DIR) +libext+.v
# build files put into $(VCS_BUILD_DIR)
# VCS_FLAGS += -Mdir=$(VCS_BUILD_DIR)
# enable fsdb dump
VCS_FLAGS += $(EXTRA)

SIM_FLIST := $(shell pwd)/sim_flist.f
$(VCS_RTL): $(SIM_TOP_V) $(VCS_CXXFILES) $(VCS_VFILES)
	$(shell if [ ! -e $(VCS_SIM_DIR)/rtl/comp ];then mkdir -p $(VCS_SIM_DIR)/rtl/comp; fi)
	$(shell find $(VCS_VSRC_DIR) -name "*.v" > $(SIM_FLIST))
	$(shell find $(SIM_VSRC_DIR) -name "*.v" -or -name "*.sv" >> $(SIM_FLIST))
	$(shell echo -f $(BUILD_DIR)/cpu_flist.f >> $(SIM_FLIST))
	cp $(SIM_FLIST) $(VCS_SIM_DIR)/rtl/comp
	cd $(VCS_SIM_DIR)/rtl/comp && vcs $(VCS_FLAGS) -f $(SIM_FLIST) $(VCS_CXXFILES)

vcs-clean:
	rm -rf $(VCS_SIM_DIR) $(VCS_BUILD_DIR)

