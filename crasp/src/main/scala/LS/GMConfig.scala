// File: src/main/scala/LS/GMConfig.scala
package LS

/**
 * Mutator configuration used by Learners.scala and LocalSearchSA.scala.
 *
 * Keep this as the single source of truth for config fields,
 * so call sites stay stable.
 */
final case class GMConfig(
  // ----- required by existing Learners.scala -----
  alphabet: Set[String],
  constRange: Int = 1,
  outName: String = "Out",
  mutableBoolNames: Set[String] = Set.empty,  // e.g., B1..Bk + Out
  mutableCountNames: Set[String] = Set.empty, // e.g., C1..Cm
  constCountNames: Set[String] = Set.empty,   // legacy; safe to ignore

  // ----- new: finite domains for depth-1 typed mutator -----
  allowPositionPeriod: Boolean = true,
  periods: Vector[Int] = Vector(2),
  offsets: Vector[Int] = Vector(0, 1),

  allowCountingRanges: Boolean = true,
  rangeBounds: Vector[Int] = Vector(1),

  allowConditional: Boolean = true,
  allowRunParity: Boolean = true,   // BoolAtomRunParity: odd-length run detection

  // mutation mixture
  pMutateBoolRule: Double = 0.60,
  pReplaceRhs: Double = 0.45,
  pMicroOp: Double = 0.25,
  pMicroLeaf: Double = 0.30,

  pruneUnreachable: Boolean = false,

  // Ordered rule-name arrays for O(1) index→name lookup in the mutator.
  // Must match the ordering of boolRules / countRules in the program.
  // Rule names never change (only bodies mutate), so caching here is safe
  // as long as pruneUnreachable=false (the default).
  boolNamesArr: Array[String]  = Array.empty,
  countNamesArr: Array[String] = Array.empty
) {
  // Pre-computed derived values (avoid recomputing on every sampling call)
  val alphabetVec: Vector[String] = alphabet.toVector.sorted
  val hasPeriod:    Boolean = allowPositionPeriod && periods.nonEmpty && offsets.nonEmpty
  val hasRunParity: Boolean = allowRunParity && alphabetVec.nonEmpty

  // O(1) rule counts — avoids List.length (O(n)) on every mutation call.
  // Fall back to the set sizes if arrays were not provided.
  val numBoolRules: Int  = if (boolNamesArr.nonEmpty) boolNamesArr.length else mutableBoolNames.size
  val numCountRules: Int = if (countNamesArr.nonEmpty) countNamesArr.length else mutableCountNames.size
}
