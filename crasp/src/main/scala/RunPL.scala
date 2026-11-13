// File: src/main/scala/RunPL.scala
import Crasp._
import utils.Dataset
import utils.Verify
import LS._
import java.io.{File, PrintWriter}
import scala.collection.mutable
import scala.util.Try
import java.util.concurrent.{Executors, TimeUnit, TimeoutException, Callable}
import java.util.concurrent.atomic.AtomicReference

object RunPL {

  // -------------------------
  // tiny arg helpers
  // -------------------------
  private def getOpt(args: Seq[String], name: String): Option[String] = {
    val i = args.indexOf(name)
    if (i >= 0 && i + 1 < args.length) Some(args(i + 1)) else None
  }
  private def getInt(args: Seq[String], name: String, default: Int): Int =
    getOpt(args, name).flatMap(s => Try(s.toInt).toOption).getOrElse(default)

  private def getLong(args: Seq[String], name: String, default: Long): Long =
    getOpt(args, name).flatMap(s => Try(s.toLong).toOption).getOrElse(default)

  private def getStr(args: Seq[String], name: String, default: String): String =
    getOpt(args, name).getOrElse(default)

  private def getBool(args: Seq[String], name: String, default: Boolean): Boolean =
    getOpt(args, name).map(_.toLowerCase match {
      case "1" | "true" | "yes" | "y" => true
      case "0" | "false" | "no"  | "n" => false
      case _ => default
    }).getOrElse(default)

  private def parseTargets(s: String): List[String] =
    s.split("[,\\s]+").toList.map(_.trim).filter(_.nonEmpty)

  // -------------------------
  // dataset router (same targets you used in run.scala)
  // -------------------------
  private def genByTarget(
    target: String,
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = target match {
    case "dyck"        => Dataset.generateDyck1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "asbs"        => Dataset.generateAsBs(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "anbncn"      => Dataset.generateAnBnCn(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "aastar"      => Dataset.generateAAStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "majority"    => Dataset.generateMajority(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "exist"       => Dataset.generateExistential(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "parity"      => Dataset.generateParity(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)

    case "next_max"    => Dataset.generateNextAArgmax(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "next_all"    => Dataset.generateNextAll(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)

    case "D2"          => Dataset.generateD2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D3"          => Dataset.generateD3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D4"          => Dataset.generateD4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "D12"         => Dataset.generateD12(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)

    case "tomita-1"    => Dataset.generateTomita1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-2"    => Dataset.generateTomita2(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-3"    => Dataset.generateTomita3(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-4"    => Dataset.generateTomita4(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-5"    => Dataset.generateTomita5(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-6"    => Dataset.generateTomita6(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "tomita-7"    => Dataset.generateTomita7(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "ababstar"    => Dataset.generateABABStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-2"  => Dataset.generatePiecewiseAB(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-3"  => Dataset.generatePiecewiseABC(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-4"  => Dataset.generatePiecewise(4, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-5"  => Dataset.generatePiecewiseABCDE(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-6"  => Dataset.generatePiecewise(6, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-7"  => Dataset.generatePiecewise(7, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-8"  => Dataset.generatePiecewise(8, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-9"  => Dataset.generatePiecewise(9, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "piecewise-testtable-10" => Dataset.generatePiecewise(10, nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case "starabstar"  => Dataset.generateSigmaStarABSigmaStar(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
    case _             => Dataset.generateDyck1(nPos=nPos, nNeg=nNeg, minLen=minLen, maxLen=maxLen, seed=seed)
  }

  // -------------------------
  // tokenization helpers (shared with RunOptimize)
  // -------------------------
  private def parseWordTokens(s: String, allowSpaces: Boolean): Vector[String] = {
    val t = s.trim
    if (t.isEmpty) Vector.empty
    else if (allowSpaces && t.contains(" ")) t.split("\\s+").toVector.filter(_.nonEmpty)
    else t.toVector.map(_.toString)
  }
  private def canonStringOf(word: Vector[String]): String = word.mkString

  // -------------------------
  // per-target timeout helper (same style as RunOptimize)
  // -------------------------
  private def withTimeout[A](timeoutMs: Long)(thunk: => A): Either[String, A] = {
    val ex = Executors.newSingleThreadExecutor()
    try {
      val fut = ex.submit(new Callable[A] { def call(): A = thunk })
      try Right(fut.get(timeoutMs, TimeUnit.MILLISECONDS))
      catch {
        case _: TimeoutException =>
          fut.cancel(true)
          Left(s"timeout(${timeoutMs}ms)")
      }
    } finally {
      ex.shutdownNow()
    }
  }

  // ============================================================================================
  // FAST accuracy: evaluate with CRASPEvaluator (no Kind2 calls)
  // ============================================================================================
  private def accuracyByEvaluator(
    prog: CRASP,
    outNameExact: String,
    posRaw: List[String],
    negRaw: List[String],
    allowSpaces: Boolean
  ): Double = {
    val eval = new CRASPEvaluator(prog)

    // de-dup while preserving order (also reduces work)
    def dedup(xs: List[String]): Vector[String] = {
      val seen = mutable.HashSet.empty[String]
      val out  = mutable.ArrayBuffer.empty[String]
      xs.foreach { s =>
        val t = if (s == null) "" else s.trim
        if (t.nonEmpty && seen.add(t)) out += t
      }
      out.toVector
    }

    val pos = dedup(posRaw)
    val neg = dedup(negRaw)

    val posOk = pos.count { s =>
      val w = parseWordTokens(s, allowSpaces)
      eval.classifyWith(outNameExact, w)
    }
    val negOk = neg.count { s =>
      val w = parseWordTokens(s, allowSpaces)
      !eval.classifyWith(outNameExact, w)
    }

    val total = pos.size + neg.size
    if (total == 0) 0.0 else (posOk + negOk).toDouble / total.toDouble
  }

  // ============================================================================================
  // OPTIONAL verifier-based accuracy: parallel + cache + de-dup
  // ============================================================================================
  private def accuracyByVerifyFast(
    progFile: String,
    outNameExact: String,
    posRaw: List[String],
    negRaw: List[String],
    allowSpaces: Boolean,
    threads: Int
  ): Double = {

    // normalize once and de-dup
    def normCanon(s: String): (String, String) = {
      val w = parseWordTokens(s, allowSpaces)
      val canon = canonStringOf(w)
      val normForVerify = w.mkString(" ")
      (canon, normForVerify)
    }

    def dedupNorm(xs: List[String]): Vector[(String, String)] = {
      val seen = mutable.HashSet.empty[String]
      val out  = mutable.ArrayBuffer.empty[(String, String)]
      xs.foreach { s0 =>
        if (s0 != null) {
          val s = s0.trim
          if (s.nonEmpty) {
            val (canon, norm) = normCanon(s)
            if (seen.add(canon)) out += ((canon, norm))
          }
        }
      }
      out.toVector
    }

    val pos = dedupNorm(posRaw)
    val neg = dedupNorm(negRaw)

    // cache Verify.accept(progFile, outNameExact, normWord)
    val cache = new java.util.concurrent.ConcurrentHashMap[String, java.lang.Boolean]()

    def acceptCached(normWord: String): Boolean = {
      val hit = cache.get(normWord)
      if (hit != null) hit.booleanValue()
      else {
        val res = Verify.accept(progFile, outNameExact, normWord)
        cache.put(normWord, java.lang.Boolean.valueOf(res))
        res
      }
    }

    val pool = Executors.newFixedThreadPool(math.max(1, threads))
    try {
      def countPar(items: Vector[(String, String)])(pred: ((String, String)) => Boolean): Int = {
        val futures = items.map { it =>
          pool.submit(new Callable[Boolean] { def call(): Boolean = pred(it) })
        }
        futures.count(f => f.get())
      }

      val posOk = countPar(pos) { case (_, norm) => acceptCached(norm) }
      val negOk = countPar(neg) { case (_, norm) => !acceptCached(norm) }

      val total = pos.size + neg.size
      if (total == 0) 0.0 else (posOk + negOk).toDouble / total.toDouble
    } finally {
      pool.shutdownNow()
    }
  }

  // ============================================================================================
  // Chain seed for piecewise-n languages
  //
  // For alphabet sorted as [a0, a1, ..., a_{n-1}] the canonical piecewise-n chain is:
  //
  //   B1 = a0, B2 = a1, ..., Bn = a_{n-1}          (literal bool rules)
  //   C1 = #B1                                       (count occurrences of a0)
  //   B{n+1} = 1 <= C1                               (threshold)
  //   B{n+2} = B2 & B{n+1}                           (a1 after a0)
  //   C2 = #B{n+2}                                   (count "a1-after-a0")
  //   B{n+3} = 1 <= C2
  //   B{n+4} = B3 & B{n+3}                           (a2 after a1 after a0)
  //   ...
  //   B{3n-2} = 1 <= C{n-1}                          (final threshold)
  //   Out  = B{3n-2}
  //
  // Rule names follow UniversalLearner's convention (B1..Bk, C1..Cm, Out) so the SA
  // mutator's name arrays stay consistent with the program's rule list.
  // ============================================================================================
  private def buildPiecewiseChainSeed(
    alphabet: Set[String],
    outName: String
  ): CRASP = {
    val chars = alphabet.toVector.sorted
    val n     = chars.length

    // B1..B{3n-1} + Out  (total 3n bool rules)
    val boolNames: Vector[String] =
      (1 to (3 * n - 1)).map(i => s"B$i").toVector :+ outName
    // C1..Cn
    val countNames: Vector[String] =
      (1 to n).map(i => s"C$i").toVector

    // Literal bool rules: B{i+1} = chars(i)  for i=0..n-1
    val literalRules: List[BoolRule] = chars.zipWithIndex.map { case (ch, i) =>
      BoolRule(boolNames(i), BoolAtomInitial(ch))
    }.toList

    // For step i = 0..n-2:
    //   thresh at bool index n+2i  :  L = 1 <= C{i+1}
    //   conj   at bool index n+2i+1:  Q = B{i+2} & L
    val chainPairs: List[BoolRule] = (0 until n - 1).flatMap { i =>
      val threshIdx = n + 2 * i
      val conjIdx   = n + 2 * i + 1
      val thresh = BoolRule(
        boolNames(threshIdx),
        BoolAtomComparison(
          strict = false,
          left   = CountAtomConst(1),
          right  = CountAtomCountRule(countNames(i))
        )
      )
      val conj = BoolRule(
        boolNames(conjIdx),
        BoolExprAnd(
          BoolAtomBoolRule(boolNames(i + 1)),
          BoolAtomBoolRule(boolNames(threshIdx))
        )
      )
      List(thresh, conj)
    }.toList

    // Final threshold: B{3n-2} = 1 <= C{n}
    val finalThreshIdx = n + 2 * (n - 1)   // = 3n-2
    val finalThresh = BoolRule(
      boolNames(finalThreshIdx),
      BoolAtomComparison(
        strict = false,
        left   = CountAtomConst(1),
        right  = CountAtomCountRule(countNames(n - 1))
      )
    )

    // Out = final threshold
    val outRule = BoolRule(outName, BoolAtomBoolRule(boolNames(finalThreshIdx)))

    val allBoolRules = literalRules ++ chainPairs ++ List(finalThresh, outRule)

    // Count rules:
    //   C1     = #B1           (count literal a0)
    //   C{i+2} = #B{n+2i+1}   (count conjunction at step i, for i=0..n-2)
    val countRules: List[CountRule] = (0 until n).map { i =>
      val predName =
        if (i == 0) boolNames(0)                      // #B1 = #literal_0
        else        boolNames(n + 2 * (i - 1) + 1)   // #conj at step i-1
      CountRule(countNames(i), CountAtomCounting(BoolAtomBoolRule(predName), None, None))
    }.toList

    CRASP(alphabet = alphabet, boolRules = allBoolRules, countRules = countRules)
  }

  // ============================================================================================
  // Seed for tomita-4: no "aaa" substring
  //   B1 = chars[j]='a' AND chars[j-1]='a' AND chars[j-2]='a'  (triple-a ends here)
  //   C1 = #B1  (count of triple-a positions)
  //   Out = C1 <= 0
  // ============================================================================================
  private def buildTomita4Seed(outName: String): CRASP = {
    val aAtom    = BoolAtomInitial("a")
    val prevA    = BoolAtomComparison(false, CountAtomConst(1),
                     CountAtomCounting(aAtom, Some(1), None))  // chars[j-1]='a'
    val prevPrevA= BoolAtomComparison(false, CountAtomConst(1),
                     CountAtomCounting(aAtom, Some(2), None))  // chars[j-2]='a'
    val tripleA  = BoolExprAnd(aAtom, BoolExprAnd(prevA, prevPrevA))
    val boolRules = List(
      BoolRule("B1", tripleA),
      BoolRule(outName, BoolAtomComparison(false, CountAtomCountRule("C1"), CountAtomConst(0)))
    )
    val countRules = List(
      CountRule("C1", CountAtomCounting(BoolAtomBoolRule("B1"), None, None))
    )
    CRASP(alphabet = Set("a", "b"), boolRules = boolRules, countRules = countRules)
  }

  // ============================================================================================
  // Seed for tomita-7: a*b*a*b*
  //   Equivalent to: at most one "ba" bigram (b→a transition)
  //   B1 = chars[j]='a' AND chars[j-1]='b'  (bigram "ba")
  //   C1 = #B1
  //   Out = C1 <= 1
  // ============================================================================================
  private def buildTomita7Seed(outName: String): CRASP = {
    val aAtom = BoolAtomInitial("a")
    val bAtom = BoolAtomInitial("b")
    val prevB = BoolAtomComparison(false, CountAtomConst(1),
                  CountAtomCounting(bAtom, Some(1), None))   // chars[j-1]='b'
    val baPos = BoolExprAnd(aAtom, prevB)
    val boolRules = List(
      BoolRule("B1", baPos),
      BoolRule(outName, BoolAtomComparison(false, CountAtomCountRule("C1"), CountAtomConst(1)))
    )
    val countRules = List(
      CountRule("C1", CountAtomCounting(BoolAtomBoolRule("B1"), None, None))
    )
    CRASP(alphabet = Set("a", "b"), boolRules = boolRules, countRules = countRules)
  }

  // ============================================================================================
  // Seed for a^n b^n c^n
  //   Conditions: #a==#b, #b==#c, no 'a' after any 'b', no 'b' after any 'c'
  //
  //   B1=initial('a'), B2=initial('b'), B3=initial('c')
  //   B4 = B1 AND (C2 >= 1)  [a appearing after some b]
  //   B5 = B2 AND (C3 >= 1)  [b appearing after some c]
  //   C1=#B1, C2=#B2, C3=#B3, C4=#B4, C5=#B5
  //   B6 = (C1 == C2), B7 = (C2 == C3)
  //   B8 = C4 <= 0  (no a-after-b)
  //   B9 = C5 <= 0  (no b-after-c)
  //   Out = B6 & B7 & B8 & B9
  // ============================================================================================
  private def buildAnBnCnSeed(outName: String): CRASP = {
    val aAtom = BoolAtomInitial("a")
    val bAtom = BoolAtomInitial("b")
    val cAtom = BoolAtomInitial("c")
    val c2Ref = CountAtomCountRule("C2")
    val c3Ref = CountAtomCountRule("C3")
    // B4: 'a' at position j AND some 'b' has appeared in [0..j] (C2(j) >= 1)
    val aAfterB = BoolExprAnd(aAtom,
                    BoolAtomComparison(false, CountAtomConst(1), c2Ref))
    // B5: 'b' at position j AND some 'c' has appeared in [0..j] (C3(j) >= 1)
    val bAfterC = BoolExprAnd(bAtom,
                    BoolAtomComparison(false, CountAtomConst(1), c3Ref))
    val boolRules = List(
      BoolRule("B1", aAtom),
      BoolRule("B2", bAtom),
      BoolRule("B3", cAtom),
      BoolRule("B4", aAfterB),
      BoolRule("B5", bAfterC),
      BoolRule("B6", BoolAtomEqual(CountAtomCountRule("C1"), CountAtomCountRule("C2"))),  // #a==#b
      BoolRule("B7", BoolAtomEqual(CountAtomCountRule("C2"), CountAtomCountRule("C3"))),  // #b==#c
      BoolRule("B8", BoolAtomComparison(false, CountAtomCountRule("C4"), CountAtomConst(0))),  // C4<=0
      BoolRule("B9", BoolAtomComparison(false, CountAtomCountRule("C5"), CountAtomConst(0))),  // C5<=0
      BoolRule(outName,
        BoolExprAnd(BoolAtomBoolRule("B6"),
          BoolExprAnd(BoolAtomBoolRule("B7"),
            BoolExprAnd(BoolAtomBoolRule("B8"), BoolAtomBoolRule("B9")))))
    )
    val countRules = List(
      CountRule("C1", CountAtomCounting(BoolAtomBoolRule("B1"), None, None)),
      CountRule("C2", CountAtomCounting(BoolAtomBoolRule("B2"), None, None)),
      CountRule("C3", CountAtomCounting(BoolAtomBoolRule("B3"), None, None)),
      CountRule("C4", CountAtomCounting(BoolAtomBoolRule("B4"), None, None)),
      CountRule("C5", CountAtomCounting(BoolAtomBoolRule("B5"), None, None))
    )
    CRASP(alphabet = Set("a", "b", "c"), boolRules = boolRules, countRules = countRules)
  }

  // ============================================================================================
  // Seed for next_max: at position i=L-2, current='a' AND
  //   #("aa" bigrams in [0..i]) >= #("ab" bigrams) AND >= #("ac" bigrams)
  //
  //   Uses CountAtomCounting(..., Some(1), Some(1)) in Out to look at exactly position prePos-1.
  //
  //   C1 = #<(1) a (count 'a' at prev pos)
  //   B1 = 1<=C1  (prev is 'a')
  //   B2 = a & B1  (bigram "aa")
  //   B3 = b & B1  (bigram "ab")
  //   B4 = c & B1  (bigram "ac")
  //   C2=#B2, C3=#B3, C4=#B4  (bigram counts)
  //   B5 = C3<=C2  (Cab<=Caa: aa is >= ab)
  //   B6 = C4<=C2  (Cac<=Caa: aa is >= ac)
  //   B7 = initial('a') & B5 & B6  (NEXT_a at this position)
  //   Out = 1 <= #[1..1](B7)   (B7 holds at exactly pos-1 = L-2)
  // ============================================================================================
  private def buildNextMaxSeed(outName: String): CRASP = {
    val aAtom = BoolAtomInitial("a")
    val bAtom = BoolAtomInitial("b")
    val cAtom = BoolAtomInitial("c")
    val boolRules = List(
      BoolRule("B1", BoolAtomComparison(false, CountAtomConst(1),
                       CountAtomCounting(aAtom, Some(1), None))),   // prev is 'a'
      BoolRule("B2", BoolExprAnd(aAtom, BoolAtomBoolRule("B1"))),   // bigram aa
      BoolRule("B3", BoolExprAnd(bAtom, BoolAtomBoolRule("B1"))),   // bigram ab
      BoolRule("B4", BoolExprAnd(cAtom, BoolAtomBoolRule("B1"))),   // bigram ac
      BoolRule("B5", BoolAtomComparison(false,
                       CountAtomCountRule("C3"), CountAtomCountRule("C2"))),  // Cab<=Caa
      BoolRule("B6", BoolAtomComparison(false,
                       CountAtomCountRule("C4"), CountAtomCountRule("C2"))),  // Cac<=Caa
      BoolRule("B7", BoolExprAnd(aAtom,
                       BoolExprAnd(BoolAtomBoolRule("B5"), BoolAtomBoolRule("B6")))), // NEXT_a
      BoolRule(outName,
        // look exactly at position prePos-1 (= L-2) by using range [1..1]
        BoolAtomComparison(false, CountAtomConst(1),
          CountAtomCounting(BoolAtomBoolRule("B7"), Some(1), Some(1))))
    )
    val countRules = List(
      CountRule("C1", CountAtomCounting(aAtom, Some(1), None)),               // CPa
      CountRule("C2", CountAtomCounting(BoolAtomBoolRule("B2"), None, None)), // Caa
      CountRule("C3", CountAtomCounting(BoolAtomBoolRule("B3"), None, None)), // Cab
      CountRule("C4", CountAtomCounting(BoolAtomBoolRule("B4"), None, None))  // Cac
    )
    CRASP(alphabet = Set("a", "b", "c"), boolRules = boolRules, countRules = countRules)
  }

  // ============================================================================================
  // Seed for next_all: at position i=L-2, σ=chars[i],
  //   accept iff exists j in [0..i-1] s.t. chars[j]=σ AND chars[j+1]='a'
  //
  //   C1=#<(1)a, C2=#<(1)b, C3=#<(1)c   (is prev char a/b/c?)
  //   B1=1<=C1, B2=1<=C2, B3=1<=C3      (prev-is-a/b/c bools)
  //   B4=a&B1 (bigram aa), B5=a&B2 (bigram ba), B6=a&B3 (bigram ca)
  //   C4=#B4, C5=#B5, C6=#B6             (bigram counts)
  //   B7=1<=C4, B8=1<=C5, B9=1<=C6       (bigram-exists flags)
  //   B10 = (a&B7)|(b&B8)|(c&B9)         (NEXT_a at this position)
  //   Out = 1 <= #[1..1](B10)            (B10 at exactly L-2)
  // ============================================================================================
  private def buildNextAllSeed(outName: String): CRASP = {
    val aAtom = BoolAtomInitial("a")
    val bAtom = BoolAtomInitial("b")
    val cAtom = BoolAtomInitial("c")
    val boolRules = List(
      BoolRule("B1", BoolAtomComparison(false, CountAtomConst(1),
                       CountAtomCounting(aAtom, Some(1), None))),   // prev is 'a'
      BoolRule("B2", BoolAtomComparison(false, CountAtomConst(1),
                       CountAtomCounting(bAtom, Some(1), None))),   // prev is 'b'
      BoolRule("B3", BoolAtomComparison(false, CountAtomConst(1),
                       CountAtomCounting(cAtom, Some(1), None))),   // prev is 'c'
      BoolRule("B4", BoolExprAnd(aAtom, BoolAtomBoolRule("B1"))),   // bigram aa
      BoolRule("B5", BoolExprAnd(aAtom, BoolAtomBoolRule("B2"))),   // bigram ba
      BoolRule("B6", BoolExprAnd(aAtom, BoolAtomBoolRule("B3"))),   // bigram ca
      BoolRule("B7", BoolAtomComparison(false, CountAtomConst(1), CountAtomCountRule("C4"))), // has aa
      BoolRule("B8", BoolAtomComparison(false, CountAtomConst(1), CountAtomCountRule("C5"))), // has ba
      BoolRule("B9", BoolAtomComparison(false, CountAtomConst(1), CountAtomCountRule("C6"))), // has ca
      BoolRule("B10",
        BoolExprOr(BoolExprAnd(aAtom, BoolAtomBoolRule("B7")),
          BoolExprOr(BoolExprAnd(bAtom, BoolAtomBoolRule("B8")),
                     BoolExprAnd(cAtom, BoolAtomBoolRule("B9"))))), // NEXT_a
      BoolRule(outName,
        BoolAtomComparison(false, CountAtomConst(1),
          CountAtomCounting(BoolAtomBoolRule("B10"), Some(1), Some(1)))) // at exactly L-2
    )
    val countRules = List(
      CountRule("C1", CountAtomCounting(aAtom, Some(1), None)),               // CPa
      CountRule("C2", CountAtomCounting(bAtom, Some(1), None)),               // CPb
      CountRule("C3", CountAtomCounting(cAtom, Some(1), None)),               // CPc
      CountRule("C4", CountAtomCounting(BoolAtomBoolRule("B4"), None, None)), // C_aa bigrams
      CountRule("C5", CountAtomCounting(BoolAtomBoolRule("B5"), None, None)), // C_ba bigrams
      CountRule("C6", CountAtomCounting(BoolAtomBoolRule("B6"), None, None))  // C_ca bigrams
    )
    CRASP(alphabet = Set("a", "b", "c"), boolRules = boolRules, countRules = countRules)
  }

  // ============================================================================================
  // MAIN
  // ============================================================================================
  @main def pl(args: String*): Unit = {
    val examplesDir = getStr(args, "--examplesDir", "examples")
    val targetsStr  = getStr(args, "--targets", "D2")
    val targets = parseTargets(targetsStr)

    val minLen = getInt(args, "--minLen", 1)
    val maxLen = getInt(args, "--maxLen", 30)

    // Total samples per class before complexity scaling; split 80/20 into train/test.
    val nTotal = getInt(args, "--nTotal", getInt(args, "--nPos", 500))

    val seed   = getInt(args, "--seed", 42)
    val iters     = getInt(args, "--iters", 80_000)

    val outCsv    = getStr(args, "--out", "passive_results.csv")
    val saveProgs = getBool(args, "--saveProgs", default = true)
    val progDir   = getStr(args, "--progDir", "learned_passive")
    val outName   = getStr(args, "--outName", "Out")

    // accuracy mode knobs
    val allowSpaces    = getBool(args, "--allowSpaces", default = true)
    val useVerifierAcc = getBool(args, "--useVerifierAcc", default = false) // default FAST mode
    val accThreads     = getInt(args, "--accThreads", math.max(1, Runtime.getRuntime.availableProcessors() / 2))

    // per-target timeout (default 5 minutes)
    val timeoutMs: Long = getLong(args, "--timeoutMs", 5L * 60L * 1000L)

    if (saveProgs) new File(progDir).mkdirs()

    val cfg = UniversalLearnerConfig(sa = LocalSearchSA.SAConfig(iters = iters, hardIterCap = iters))

    val pw = new PrintWriter(new File(outCsv))
    try {
      // keep old columns + add timeout like optimize
      pw.println("target,ok,train_acc,eval_acc,learn_ms,program_size,timeout")
      println(targets.mkString(", "))

      for (t <- targets) {
        val oracleFile = s"$examplesDir/$t.crasp"
        val f = new File(oracleFile)
        if (!f.exists()) {
          println(s"[RunPL] SKIP: file not found: $oracleFile")
          pw.println(s"$t,false,0.0,0.0,0,0,false")
          pw.flush()
        } else {

          // Shared checkpoint: updated by search thread, read by main thread on timeout
          val bestCheckpoint = new AtomicReference[Option[(CRASP, Double)]](None)

          val runRes: Either[String, (Boolean, Double, Double, Long, Int)] =
            withTimeout(timeoutMs) {

              CRASPParser.parseFile(oracleFile, enforceOrder = false) match {
                case Left(err) =>
                  println(s"[RunPL] SKIP: parse failed for $t: $err")
                  (false, 0.0, 0.0, 0L, 0)

                case Right(oracleProg) =>
                  val alphabet = oracleProg.alphabet
                  if (alphabet.isEmpty) {
                    println(s"[RunPL] SKIP: empty alphabet for $t")
                    (false, 0.0, 0.0, 0L, 0)
                  } else {
                    // Derive search bounds from the oracle structure.
                    // Add a small slack (+4 bool / +2 count) to allow slightly larger programs.
                    // No artificial floor: the oracle size itself drives whether we use
                    // level-jump (small oracles) or direct search (large oracles) in passive_learning.
                    val oracleBool  = oracleProg.boolRules.size
                    val oracleCount = oracleProg.countRules.size
                    val maxBoolSearch  = oracleBool  + 4
                    val maxCountSearch = math.max(2, oracleCount + 2)

                    // Scale total samples inversely with oracle complexity, then split 80/20.
                    // Ensure at least 63 total so the 80% train portion >= 50 examples.
                    val complexityScale = math.max(1.0, oracleBool.toDouble / 8.0)
                    val totalPosCapped  = math.max(63, (nTotal / complexityScale).toInt)
                    val totalNegCapped  = math.max(63, (nTotal / complexityScale).toInt)

                    val (allPos, allNeg) = genByTarget(t, totalPosCapped, totalNegCapped, minLen, maxLen, seed)

                    // Shuffle before splitting for reproducible, unbiased 80/20 split.
                    val splitRng = new scala.util.Random(seed + 54321)
                    val shuffledPos = splitRng.shuffle(allPos)
                    val shuffledNeg = splitRng.shuffle(allNeg)
                    val nTrainPos = shuffledPos.size * 4 / 5
                    val nTrainNeg = shuffledNeg.size * 4 / 5
                    val trainPos = shuffledPos.take(nTrainPos)
                    val evalPos  = shuffledPos.drop(nTrainPos)
                    val trainNeg = shuffledNeg.take(nTrainNeg)
                    val evalNeg  = shuffledNeg.drop(nTrainNeg)

                    println(
                      s"[RunPL] oracle=(b=$oracleBool,c=$oracleCount) " +
                      s"search=(b=$maxBoolSearch,c=$maxCountSearch) " +
                      s"total=${totalPosCapped}+${totalNegCapped} → train=${nTrainPos}+${nTrainNeg} test=${evalPos.size}+${evalNeg.size} (scaled from $nTotal)"
                    )

                    // Build a warm-start seed for targets that have a known structure.
                    val chainSeed: Option[CRASP] = t match {
                      case s if s.startsWith("piecewise-testtable-") && alphabet.size >= 2 =>
                        Some(buildPiecewiseChainSeed(alphabet, outName))
                      case "tomita-4"  => Some(buildTomita4Seed(outName))
                      case "tomita-7"  => Some(buildTomita7Seed(outName))
                      case "anbncn"    => Some(buildAnBnCnSeed(outName))
                      case "next_max"  => Some(buildNextMaxSeed(outName))
                      case "next_all"  => Some(buildNextAllSeed(outName))
                      case _           => None
                    }

                    if (chainSeed.isDefined)
                      println(s"[RunPL] Using seed (b=${chainSeed.get.boolRules.size - 1}, c=${chainSeed.get.countRules.size}) for $t")

                    val t0 = System.nanoTime()
                    val learnedOpt = LsCegis.passive_learning(
                      positives0 = trainPos,
                      negatives0 = trainNeg,
                      alphabet = alphabet,
                      config = cfg,
                      save_name = s"${t}_passive",
                      outNameExact = outName,
                      seed = seed,
                      verbose = true,
                      requirePerfectFit = false,
                      maxBool = maxBoolSearch,
                      maxCount = maxCountSearch,
                      onCheckpoint = (prog, _) => bestCheckpoint.set(Some((prog, 0.0))),
                      initProgram = chainSeed
                    )
                    val t1 = System.nanoTime()
                    val learnMs = ((t1 - t0) / 1e6).toLong

                    learnedOpt match {
                      case None =>
                        println(s"[RunPL] FAIL: $t (no perfect-fit program)")
                        (false, 0.0, 0.0, learnMs, 0)

                      case Some(p) =>
                        val size = Try(Printer.pretty(p).length).getOrElse(0)

                        // save program (and also helpful if verifier-acc is enabled)
                        val learnedFile = s"cegis/${t}_passive.crasp"

                        if (saveProgs) {
                          val outFile = new File(s"$progDir/$t.crasp")
                          val ppw = new PrintWriter(outFile)
                          try ppw.println(Printer.pretty(p))
                          finally ppw.close()
                        }

                        val (trainAcc, evalAcc) =
                          if (!useVerifierAcc) {
                            val tr = accuracyByEvaluator(p, outName, trainPos, trainNeg, allowSpaces)
                            val ev = accuracyByEvaluator(p, outName, evalPos,  evalNeg,  allowSpaces)
                            (tr, ev)
                          } else {
                            // verifier acc requires the saved file (passive_learning already saves to cegis/<name>.crasp)
                            if (!new File(learnedFile).exists()) {
                              println(s"[RunPL] WARN: learned file not found at '$learnedFile' - verifier acc=0")
                              (0.0, 0.0)
                            } else {
                              val tr = accuracyByVerifyFast(learnedFile, outName, trainPos, trainNeg, allowSpaces, accThreads)
                              val ev = accuracyByVerifyFast(learnedFile, outName, evalPos,  evalNeg,  allowSpaces, accThreads)
                              (tr, ev)
                            }
                          }

                        println(f"[RunPL] OK: $t  train=$trainAcc%.4f  eval=$evalAcc%.4f  time=${learnMs}ms")
                        (true, trainAcc, evalAcc, learnMs, size)
                    }
                  }
              }
            }

          runRes match {
            case Left(toMsg) =>
              // Use the best program captured via AtomicReference during the timed-out search
              val checkpointAcc: Option[(Double, Double)] =
                Option(bestCheckpoint.get()).flatten.map { case (p, _) =>
                  // Regenerate the same 80/20 split used during the timed-out search.
                  val complexityScale2 = 1.0  // use unscaled total for checkpoint eval
                  val totalCapped2 = math.max(63, (nTotal / complexityScale2).toInt)
                  val (allPos2, allNeg2) = genByTarget(t, totalCapped2, totalCapped2, minLen, maxLen, seed)
                  val splitRng2 = new scala.util.Random(seed + 54321)
                  val shPos2 = splitRng2.shuffle(allPos2)
                  val shNeg2 = splitRng2.shuffle(allNeg2)
                  val nTr2 = shPos2.size * 4 / 5
                  val nTrN2 = shNeg2.size * 4 / 5
                  val trainPos2 = shPos2.take(nTr2);  val evalPos2 = shPos2.drop(nTr2)
                  val trainNeg2 = shNeg2.take(nTrN2); val evalNeg2 = shNeg2.drop(nTrN2)
                  val tr = accuracyByEvaluator(p, outName, trainPos2, trainNeg2, allowSpaces)
                  val ev2 = accuracyByEvaluator(p, outName, evalPos2,  evalNeg2,  allowSpaces)
                  (tr, ev2)
                }
              checkpointAcc match {
                case Some((tr, ev2)) =>
                  println(f"[RunPL] TIMEOUT: $t — best checkpoint: train=$tr%.4f eval=$ev2%.4f ($toMsg)")
                  pw.println(f"$t,false,$tr,$ev2,0,0,true")
                case None =>
                  println(s"[RunPL] TIMEOUT: $t ($toMsg)")
                  pw.println(s"$t,false,0.0,0.0,0,0,true")
              }
              pw.flush()

            case Right((ok, trainAcc, evalAcc, learnMs, size)) =>
              pw.println(s"$t,$ok,$trainAcc,$evalAcc,$learnMs,$size,false")
              pw.flush()
          }
        }
      }

      println(s"[RunPL] wrote: $outCsv")
      if (saveProgs) println(s"[RunPL] programs in: $progDir")
    } finally {
      pw.close()
    }
  }
}
