package brasp

import java.io.ByteArrayOutputStream
import scala.collection.mutable

/** Binary AIGER (.aig) circuit builder for the direct summary circuits
  * (`DirectSummary`), checked by ABC: 2-input AND gates with literal
  * negation for free, per the AIGER convention.
  *
  * Two format quirks this needs to work around, found by hex-diffing
  * ABC's own `write_aiger` output against a hand-derivation from its
  * reader source (`ioReadAiger.c`):
  *   - AIGER numbers variables *positionally*: `[1, I]` are inputs, `[I+1,
  *     I+L]` latches, and everything after that an AND gate — there's no
  *     per-line keyword tagging a variable's role, so every input and
  *     latch variable must be allocated before any AND gate is created.
  *   - This build's binary reader only supports the plain (pre-1.9)
  *     latch format: one line per latch, just its `next` literal — no
  *     explicit reset field, always reset-to-0. So every latch is
  *     canonicalized to physically reset to 0 via the standard
  *     XOR-with-init trick (`Builder.flip`).
  */

final case class AigerError(message: String) extends RuntimeException(message)

object Aiger:

  private[brasp] final class Builder:
    private var nextVar = 1
    val andGates = mutable.ArrayBuffer.empty[(Int, Int, Int)] // (outputLit, aLit, bLit)
    // Structural hashing.
    // Without it every `(cell, symbol)` pair rebuilds the same subcircuits
    // from scratch: `monotone_past` at sigma=64 emitted 393,020 gates that
    // ABC's own hashing then folded to 39,250 on read, and paid ~4s to do it.
    private val gateCache = mutable.Map.empty[(Int, Int), Int]
    def freshVar(): Int =
      val v = nextVar
      nextVar += 1
      v
    def maxVar: Int = nextVar - 1

    def lit(v: Int, negated: Boolean = false): Int = 2 * v + (if negated then 1 else 0)
    val True = 1
    val False = 0
    def not(a: Int): Int = a ^ 1
    def and(a: Int, b: Int): Int =
      if a == False || b == False then False
      else if a == True then b
      else if b == True then a
      else if a == b then a
      else if a == (b ^ 1) then False // x and not x
      else
        // Reuse only ever points at an earlier gate, so an AND's output
        // variable still exceeds both of its inputs' --- the invariant the
        // delta encoding below relies on.
        gateCache.getOrElseUpdate(if a < b then (a, b) else (b, a), {
          val v = freshVar()
          val out = lit(v)
          andGates += ((out, a, b))
          out
        })
    def or(a: Int, b: Int): Int = not(and(not(a), not(b)))
    def ite(c: Int, t: Int, f: Int): Int = or(and(c, t), and(not(c), f))
    def xnor(a: Int, b: Int): Int = or(and(a, b), and(not(a), not(b)))
    def flip(literal: Int, negate: Boolean): Int = if negate then not(literal) else literal

  /** AIGER's variable-length delta encoding for AND-gate literals (see
    * `Io_ReadAigerDecode` in ABC's `ioReadAiger.c`): little-endian, 7 bits
    * per byte, high bit set while more bytes follow.
    */
  private[brasp] def encodeDelta(out: ByteArrayOutputStream, valueIn: Int): Unit =
    var value = valueIn
    while (value & ~0x7f) != 0 do
      out.write((value & 0x7f) | 0x80)
      value = value >>> 7
    out.write(value & 0x7f)
