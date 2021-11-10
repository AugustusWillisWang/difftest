/***************************************************************************************
* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* XiangShan is licensed under Mulan PSL v2.
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

package difftest

import chisel3._
import chisel3.util._
import Chisel.BlackBox
import chisel3.experimental.{DataMirror, ExtModule}

trait DifftestParameter {
}

trait DifftestWithClock {
  val clock  = Input(Clock())
}

trait DifftestWithCoreid {
  val coreid = Input(UInt(8.W))
}

trait DifftestWithIndex {
  val index = Input(UInt(8.W))
}

abstract class DifftestBundle extends Bundle
  with DifftestParameter
  with DifftestWithClock
  with DifftestWithCoreid

class DiffArchEventIO extends DifftestBundle {
  val intrNO = Input(UInt(32.W))
  val cause = Input(UInt(32.W))
  val exceptionPC = Input(UInt(64.W))
  val exceptionInst = Input(UInt(32.W))
}

class DiffInstrCommitIO extends DifftestBundle with DifftestWithIndex {
  val valid    = Input(Bool())
  val pc       = Input(UInt(64.W))
  val instr    = Input(UInt(32.W))
  val special  = Input(UInt(8.W))
  val skip     = Input(Bool())
  val isRVC    = Input(Bool())
  val scFailed = Input(Bool())
  val wen      = Input(Bool())
  val wdest    = Input(UInt(8.W))
  val wdata    = Input(UInt(64.W))
}

class DiffTrapEventIO extends DifftestBundle {
  val valid    = Input(Bool())
  val code     = Input(UInt(3.W))
  val pc       = Input(UInt(64.W))
  val cycleCnt = Input(UInt(64.W))
  val instrCnt = Input(UInt(64.W))
}

class DiffCSRStateIO extends DifftestBundle {
  val priviledgeMode = Input(UInt(2.W))
  val mstatus = Input(UInt(64.W))
  val sstatus = Input(UInt(64.W))
  val mepc = Input(UInt(64.W))
  val sepc = Input(UInt(64.W))
  val mtval = Input(UInt(64.W))
  val stval = Input(UInt(64.W))
  val mtvec = Input(UInt(64.W))
  val stvec = Input(UInt(64.W))
  val mcause = Input(UInt(64.W))
  val scause = Input(UInt(64.W))
  val satp = Input(UInt(64.W))
  val mip = Input(UInt(64.W))
  val mie = Input(UInt(64.W))
  val mscratch = Input(UInt(64.W))
  val sscratch = Input(UInt(64.W))
  val mideleg = Input(UInt(64.W))
  val medeleg = Input(UInt(64.W))
}

class DiffArchIntRegStateIO extends DifftestBundle {
  val gpr = Input(Vec(32, UInt(64.W)))
}

class DiffArchFpRegStateIO extends DifftestBundle {
  val fpr  = Input(Vec(32, UInt(64.W)))
}

class DiffSbufferEventIO extends DifftestBundle with DifftestWithIndex{
  val sbufferResp = Input(Bool())
  val sbufferAddr = Input(UInt(64.W))
  val sbufferData = Input(Vec(64, UInt(8.W)))
  val sbufferMask = Input(UInt(64.W))
}

class DiffStoreEventIO extends DifftestBundle with DifftestWithIndex {
  val valid       = Input(Bool())
  val storeAddr   = Input(UInt(64.W))
  val storeData   = Input(UInt(64.W))
  val storeMask   = Input(UInt(8.W))
}

class DiffLoadEventIO extends DifftestBundle with DifftestWithIndex {
  val valid  = Input(Bool())
  val paddr  = Input(UInt(64.W))
  val opType = Input(UInt(8.W))
  val fuType = Input(UInt(8.W))
}

class DiffAtomicEventIO extends DifftestBundle {
  val atomicResp = Input(Bool())
  val atomicAddr = Input(UInt(64.W))
  val atomicData = Input(UInt(64.W))
  val atomicMask = Input(UInt(8.W))
  val atomicFuop = Input(UInt(8.W))
  val atomicOut  = Input(UInt(64.W))
}

class DiffPtwEventIO extends DifftestBundle {
  val ptwResp = Input(Bool())
  val ptwAddr = Input(UInt(64.W))
  val ptwData = Input(Vec(4, UInt(64.W)))
}

class DiffRefillEventIO extends DifftestBundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(64.W))
  val data  = Input(Vec(8, UInt(64.W)))
}

class DiffRunaheadEventIO extends DifftestBundle with DifftestWithIndex {
  val valid         = Input(Bool())
  val branch        = Input(Bool())
  val may_replay    = Input(Bool())
  val pc            = Input(UInt(64.W))
  val checkpoint_id = Input(UInt(64.W))
}

class DiffRunaheadCommitEventIO extends DifftestBundle with DifftestWithIndex {
  val valid         = Input(Bool())
  val pc            = Input(UInt(64.W))
}

class DiffRunaheadRedirectEventIO extends DifftestBundle {
  val valid         = Input(Bool())
  val pc            = Input(UInt(64.W)) // for debug only
  val target_pc     = Input(UInt(64.W)) // for debug only
  val checkpoint_id = Input(UInt(64.W))
}

class DiffRunaheadMemdepPredIO extends DifftestBundle with DifftestWithIndex {
  val valid         = Input(Bool())
  val is_load       = Input(Bool())
  val need_wait     = Input(Bool())
  val pc            = Input(UInt(64.W)) // for debug only
  val oracle_vaddr  = Output(UInt(64.W))
}

abstract class DifftestModule extends ExtModule with HasExtModuleInline {
  val io: DifftestBundle

  def getDirectionString(data: Data): String = {
    if (DataMirror.directionOf(data) == ActualDirection.Input) "input " else "output"
  }

  def getDPICArgString(argName: String, data: Data): String = {
    val directionString = getDirectionString(data)
    val typeString = data.getWidth match {
      case 1                                  => "bit"
      case width if width > 1  && width <= 8  => "byte"
      case width if width > 8  && width <= 32 => "int"
      case width if width > 32 && width <= 64 => "longint"
      case _ => s"unsupported io type of width ${data.getWidth}!!\n"
    }
    val argString = Seq(directionString, f"${typeString}%7s", argName)
    argString.mkString(" ")
  }

  def getModArgString(argName: String, data: Data): String = {
    val widthString = if (data.getWidth == 1) "      " else f"[${data.getWidth - 1}%2d:0]"
    val argString = Seq(getDirectionString(data), widthString, s"$argName")
    argString.mkString(" ")
  }

  lazy val moduleName = this.getClass.getSimpleName
  lazy val moduleBody: String = {
    // ExtModule implicitly adds io_* prefix to the IOs (because the IO val is named as io).
    // This is different from BlackBoxes.
    val interfaces = io.elements.toSeq.reverse.flatMap{ case (name, data) =>
      data match {
        case vec: Vec[Data] => vec.zipWithIndex.map{ case (v, i) => (s"io_${name}_$i", v) }
        case _ => Seq((s"io_$name", data))
      }
    }
    // (1) DPI-C function prototype
    val dpicInterfaces = interfaces.filterNot(_._1 == "io_clock")
    val dpicPrototype = dpicInterfaces.map(i => getDPICArgString(i._1, i._2)).mkString(",\n")
    val dpicDeclBase = Seq("import", "\"DPI-C\"", "function", "void").mkString(" ")
    val dpicDeclName = s"v_difftest_${moduleName.replace("Difftest", "")}"
    val dpicDecl = Seq(Seq(dpicDeclBase, dpicDeclName, "(").mkString(" "), dpicPrototype, ");").mkString("\n")
    // (2) module definition
    val modPrototype = interfaces.map(i => getModArgString(i._1, i._2)).mkString(",\n")
    val modDecl = Seq(Seq("module", moduleName, "(").mkString(" "), modPrototype, ");").mkString("\n")
    val modBody = Seq(
      "`ifndef SYNTHESIS",
      dpicDecl,
      "  always @(posedge io_clock) begin",
      "    " + Seq(dpicDeclName, "(", dpicInterfaces.map(_._1).mkString(", "), ");").mkString(" "),
      "  end",
      "`endif"
    ).mkString("\n")
    val modEnd = "endmodule"
    val modDef = Seq(modDecl, modBody, modEnd).mkString("\n")
    modDef
  }
  def instantiate(): Unit = setInline(s"$moduleName.v", moduleBody)
}

class DifftestArchEvent extends DifftestModule {
  val io = IO(new DiffArchEventIO)
  instantiate()
}

class DifftestInstrCommit extends DifftestModule {
  val io = IO(new DiffInstrCommitIO)
  instantiate()
}

class DifftestTrapEvent extends DifftestModule {
  val io = IO(new DiffTrapEventIO)
  instantiate()
}

class DifftestCSRState extends DifftestModule {
  val io = IO(new DiffCSRStateIO)
  instantiate()
}

class DifftestArchIntRegState extends DifftestModule {
  val io = IO(new DiffArchIntRegStateIO)
  instantiate()
}

class DifftestArchFpRegState extends DifftestModule {
  val io = IO(new DiffArchFpRegStateIO)
  instantiate()
}

class DifftestSbufferEvent extends DifftestModule {
  val io = IO(new DiffSbufferEventIO)
  instantiate()
}

class DifftestStoreEvent extends DifftestModule {
  val io = IO(new DiffStoreEventIO)
  instantiate()
}

class DifftestLoadEvent extends DifftestModule {
  val io = IO(new DiffLoadEventIO)
  instantiate()
}

class DifftestAtomicEvent extends DifftestModule {
  val io = IO(new DiffAtomicEventIO)
  instantiate()
}

class DifftestPtwEvent extends DifftestModule {
  val io = IO(new DiffPtwEventIO)
  instantiate()
}

class DifftestRefillEvent extends DifftestModule {
  val io = IO(new DiffRefillEventIO)
  instantiate()
}

class DifftestRunaheadEvent extends DifftestModule {
  val io = IO(new DiffRunaheadEventIO)
  instantiate()
}

class DifftestRunaheadCommitEvent extends DifftestModule {
  val io = IO(new DiffRunaheadCommitEventIO)
  instantiate()
}

class DifftestRunaheadRedirectEvent extends DifftestModule {
  val io = IO(new DiffRunaheadRedirectEventIO)
  instantiate()
}

class DifftestRunaheadMemdepPred extends DifftestModule {
  val io = IO(new DiffRunaheadMemdepPredIO)
  instantiate()
}

// Difftest emulator top

// XiangShan log / perf ctrl, should be inited in SimTop IO
// If not needed, just ingore these signals
class PerfInfoIO extends Bundle {
  val clean = Input(Bool())
  val dump = Input(Bool())
}

class LogCtrlIO extends Bundle {
  val log_begin, log_end = Input(UInt(64.W))
  val log_level = Input(UInt(64.W)) // a cpp uint
}

// UART IO, if needed, should be inited in SimTop IO
// If not needed, just hardwire all output to 0
class UARTIO extends Bundle {
  val out = new Bundle {
    val valid = Output(Bool())
    val ch = Output(UInt(8.W))
  }
  val in = new Bundle {
    val valid = Output(Bool())
    val ch = Input(UInt(8.W))
  }
}
