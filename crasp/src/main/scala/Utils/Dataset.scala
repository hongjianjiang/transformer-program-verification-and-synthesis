package utils

import java.io.{File, PrintWriter}
import scala.io.Source
import scala.util.Random

object Dataset {

  // ==============================================================================================
  // Dyck-1
  // ==============================================================================================

  /** Backwards-compatible wrapper: generates n positives and n negatives. */
  def generateDyck1(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateDyck1(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  /** New API: generate nPos positives and nNeg negatives. (NO default args here) */
  def generateDyck1(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isValid(s: String): Boolean = {
      var balance = 0
      for (c <- s) {
        if (c == 'l') balance += 1 else balance -= 1
        if (balance < 0) return false
      }
      balance == 0
    }

    def generateBalanced(len0: Int): String = {
      val len = if (len0 % 2 == 0) len0 else len0 + 1
      if (len == 0) return ""

      val chars = new Array[Char](len)
      var balance = 0
      var openRemaining = len / 2
      var closeRemaining = len / 2

      for (i <- 0 until len) {
        val canOpen  = openRemaining > 0
        val canClose = closeRemaining > 0 && balance > 0

        if (canOpen && canClose) {
          if (rnd.nextBoolean()) { chars(i) = 'l'; balance += 1; openRemaining -= 1 }
          else                   { chars(i) = 'r'; balance -= 1; closeRemaining -= 1 }
        } else if (canOpen) {
          chars(i) = 'l'; balance += 1; openRemaining -= 1
        } else {
          chars(i) = 'r'; balance -= 1; closeRemaining -= 1
        }
      }
      new String(chars)
    }

    def generateRandom(len: Int): String =
      (1 to len).map(_ => if (rnd.nextBoolean()) 'l' else 'r').mkString

    val positives = scala.collection.mutable.Set[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      positives.add(generateBalanced(len))
    }

    val negatives = scala.collection.mutable.Set[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = generateRandom(len)
      if (!isValid(s) && !positives.contains(s)) negatives.add(s)
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // b^n a^n (name kept as your original generateAsBs)
  // ==============================================================================================

  def generateAsBs(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateAsBs(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateAsBs(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isValid(s: String): Boolean = {
      val len = s.length
      if (len % 2 != 0) return false
      val mid = len / 2
      s.take(mid).forall(_ == 'b') && s.drop(mid).forall(_ == 'a')
    }

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val k = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = ("b" * k) + ("a" * k)
      positives += s
      posSet += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = 2 * (minLen + rnd.nextInt(maxLen - minLen + 1))
      val r = rnd.nextDouble()

      val s = if (r < 0.4) {
        (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      } else if (r < 0.8) {
        val i = rnd.nextInt(len)
        val j = len - i
        if (i == j) {
          if (i > 0) ("b" * (i - 1)) + ("a" * (j + 1)) else ("b" * (i + 1)) + ("a" * (j - 1))
        } else ("b" * i) + ("a" * j)
      } else {
        val k = len / 2
        val chars = (("b" * k) + ("a" * k)).toArray
        val p1 = rnd.nextInt(len)
        val p2 = rnd.nextInt(len)
        val tmp = chars(p1); chars(p1) = chars(p2); chars(p2) = tmp
        new String(chars)
      }

      if (!isValid(s) && !posSet.contains(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }
  // ==============================================================================================
  // next_all (binary): Out=1 iff at the LAST step i=L-2, NEXT_a(i) holds.
  // NEXT_a(i) holds iff the bigram (x_i, 'a') occurred previously somewhere in the prefix.
  // Σ = {a,b,c} with deterministic sampling.
  // ==============================================================================================

  /** Backwards-compatible wrapper: generates n positives and n negatives. */
  def generateNextAArgmax(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateNextAArgmax(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  /** New API: generate nPos positives and nNeg negatives. */
  def generateNextAArgmax(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 2, s"minLen must be >= 2 (need i=|s|-2), got $minLen")
    require(maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    require(nPos >= 0 && nNeg >= 0, s"nPos/nNeg must be non-negative, got nPos=$nPos nNeg=$nNeg")

    val rnd = new Random(seed)
    val alphabet: Array[Char] = Array('a', 'b', 'c')

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    def randWord(len: Int): String =
      (0 until len).map(_ => alphabet(rnd.nextInt(alphabet.length))).mkString

    /**
     * NEXT_a at last prediction step i = L-2 using argmax bigram counts from 'a':
     * - require current symbol at i is 'a'
     * - let Caa(i), Cab(i), Cac(i) be counts of "aa","ab","ac" among bigrams ending at positions j<=i
     * - accept iff Caa(i) >= Cab(i) and Caa(i) >= Cac(i)
     */
    def nextAArgmaxAtLast(src: String): Boolean = {
      val chars = src.toCharArray
      val L = chars.length
      val i = L - 2
      if (chars(i) != 'a') return false

      var Caa = 0
      var Cab = 0
      var Cac = 0

      // count bigrams (a, x) where the second token is at position j, and j <= i
      // i.e., consider pairs (j-1, j) for j = 1..i
      var j = 1
      while (j <= i) {
        if (chars(j - 1) == 'a') {
          chars(j) match {
            case 'a' => Caa += 1
            case 'b' => Cab += 1
            case 'c' => Cac += 1
            case _   => ()
          }
        }
        j += 1
      }

      (Caa >= Cab) && (Caa >= Cac)
    }

    def isPos(s: String): Boolean = nextAArgmaxAtLast(s)

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]

    // guard against infinite loops when constraints are too tight
    var guard = 0
    val guardMax = 4_000_000

    // Allow duplicates when the space is small relative to requested nPos/nNeg.
    // (This mirrors patterns used in other generators.)
    def allowDupPos: Boolean = (maxLen - minLen + 1) < nPos
    def allowDupNeg: Boolean = (maxLen - minLen + 1) < nNeg

    while (positives.size < nPos || negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax) {
        throw new RuntimeException(
          s"Too many attempts generating NEXT_a(argmax) dataset " +
          s"(pos=${positives.size}/$nPos neg=${negatives.size}/$nNeg). " +
          s"Try widening [minLen,maxLen] or lowering nPos/nNeg."
        )
      }

      val s = randWord(sampleLen())
      if (isPos(s)) {
        if (positives.size < nPos && (!posSet.contains(s) || allowDupPos)) {
          positives += s
          posSet += s
        }
      } else {
        // avoid overlap with positives
        if (negatives.size < nNeg && !posSet.contains(s) && (!negSet.contains(s) || allowDupNeg)) {
          negatives += s
          negSet += s
        }
      }
    }

    (positives.toList, negatives.toList)
  }
  
  def generateNextAll(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateNextAll(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  /** New API: generate nPos positives and nNeg negatives. */
  def generateNextAll(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 2, s"minLen must be >= 2 (need i=|s|-2), got $minLen")
    require(maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    require(nPos >= 0 && nNeg >= 0, s"nPos/nNeg must be non-negative, got nPos=$nPos nNeg=$nNeg")

    val rnd = new Random(seed)
    val alphabet: Array[Char] = Array('a', 'b', 'c')

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    def randWord(len: Int): String =
      (0 until len).map(_ => alphabet(rnd.nextInt(alphabet.length))).mkString

    /**
     * NEXT_a at last prediction step i = L-2 under "All Possible" definition:
     *   Let σ = chars(i). Return true iff there exists j in [0..i-1] such that
     *     chars(j) = σ and chars(j+1) = 'a'.
     *
     * This matches OR_{σ∈Σ} (Q_σ(i) ∧ EXISTS_{σa}(i)):
     * at position i, σ is fixed to current symbol; EXISTS_{σa}(i) means the bigram σa occurred.
     */
    def nextAllAtLast(src: String): Boolean = {
      val chars = src.toCharArray
      val L = chars.length
      val i = L - 2
      val sigma = chars(i)

      // need some bigram sigma a ending at position j+1 <= i, i.e. j <= i-1
      var j = 0
      while (j <= i - 1) {
        if (chars(j) == sigma && chars(j + 1) == 'a') return true
        j += 1
      }
      false
    }

    def isPos(s: String): Boolean = nextAllAtLast(s)

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]

    var guard = 0
    val guardMax = 4_000_000

    // allow duplicates if the space is too small relative to requested size
    def allowDupPos: Boolean = (maxLen - minLen + 1) < nPos
    def allowDupNeg: Boolean = (maxLen - minLen + 1) < nNeg

    while (positives.size < nPos || negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax) {
        throw new RuntimeException(
          s"Too many attempts generating NEXT_all dataset " +
          s"(pos=${positives.size}/$nPos neg=${negatives.size}/$nNeg). " +
          s"Try widening [minLen,maxLen] or lowering nPos/nNeg."
        )
      }

      val s = randWord(sampleLen())
      if (isPos(s)) {
        if (positives.size < nPos && (!posSet.contains(s) || allowDupPos)) {
          positives += s
          posSet += s
        }
      } else {
        if (negatives.size < nNeg && !posSet.contains(s) && (!negSet.contains(s) || allowDupNeg)) {
          negatives += s
          negSet += s
        }
      }
    }

    (positives.toList, negatives.toList)
  }


  // ==============================================================================================
  // next_max (binary): Out=1 iff the next_max prediction at the LAST step is 'a'.
  // Σ = {a,b,c} by default.
  // History-only counts: predict argmax_v count[x_i][v] BEFORE updating with true (x_i,x_{i+1})
  // Tie-break: a < b < c (alphabet order)
  // ==============================================================================================

  def generateNextMax(
    n: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int = 42
  ): (List[String], List[String]) =
    generateNextMax(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateNextMax(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 2, s"minLen must be >=2, got $minLen")
    require(maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")

    val rnd = new Random(seed)
    val alphabet = Vector('a','b','c')
    val idxOf: Map[Char, Int] = alphabet.zipWithIndex.toMap

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    def randWord(len: Int): String =
      (0 until len).map(_ => alphabet(rnd.nextInt(alphabet.length))).mkString

    def argmaxTie(counts: Array[Int]): Char = {
      var best = 0
      var j = 1
      while (j < counts.length) {
        if (counts(j) > counts(best)) best = j
        // ties keep earlier => deterministic
        j += 1
      }
      alphabet(best)
    }

    // Return the predicted next token at the LAST step i=L-2
    def nextMaxAtLast(src: String): Char = {
      val K = alphabet.length
      val count = Array.fill(K, K)(0)
      val chars = src.toCharArray

      var i = 0
      var lastPred: Char = 'a' // overwritten when i reaches L-2
      while (i < chars.length - 1) {
        val u = idxOf(chars(i))
        lastPred = argmaxTie(count(u))      // predict from history so far
        val v = idxOf(chars(i + 1))
        count(u)(v) += 1                    // then update with true bigram
        i += 1
      }
      lastPred
    }

    def isPos(s: String): Boolean = nextMaxAtLast(s) == 'a'

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]

    var guard = 0
    val guardMax = 4000000

    while (positives.size < nPos || negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(
          s"Too many attempts generating next_max (pos=${positives.size}/$nPos neg=${negatives.size}/$nNeg). " +
          s"Try widening length range."
        )

      val s = randWord(sampleLen())
      if (isPos(s)) {
        if (positives.size < nPos && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
          positives += s; posSet += s
        }
      } else {
        if (negatives.size < nNeg && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
          negatives += s; negSet += s
        }
      }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // a^n b^n c^n
  // ==============================================================================================

  def generateAnBnCn(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateAnBnCn(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateAnBnCn(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isValid(s: String): Boolean = {
      val len = s.length
      if (len % 3 != 0) return false
      val k = len / 3
      s == ("a" * k) + ("b" * k) + ("c" * k)
    }

    def randomABC(len: Int): String =
      (1 to len).map(_ => Vector('a', 'b', 'c')(rnd.nextInt(3))).mkString

    def permutedBlocks(k: Int): String = {
      val perms = Vector("acb", "bac", "bca", "cab", "cba")
      val p = perms(rnd.nextInt(perms.length))
      p.flatMap {
        case 'a' => "a" * k
        case 'b' => "b" * k
        case 'c' => "c" * k
      }
    }

    def multiSwapWrongOrder(k: Int): String = {
      val base = (("a" * k) + ("b" * k) + ("c" * k)).toCharArray
      val len = base.length
      val swaps = 1 + rnd.nextInt(math.max(1, len / 6))
      var t = 0
      while (t < swaps) {
        val i = rnd.nextInt(len)
        val j = rnd.nextInt(len)
        val tmp = base(i); base(i) = base(j); base(j) = tmp
        t += 1
      }
      new String(base)
    }

    def boundaryCorruption(k: Int): String = {
      if (k <= 0) "b"
      else ("a" * k) + ("b" * math.max(0, k - 1)) + "c" + "b" + ("c" * math.max(0, k - 1))
    }

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val k = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = ("a" * k) + ("b" * k) + ("c" * k)
      positives += s
      posSet += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()

    var guard = 0
    val guardMax = 2000000

    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives (got ${negatives.size}/$nNeg). Consider widening ranges.")

      val k = minLen + rnd.nextInt(maxLen - minLen + 1)
      val len = 3 * k
      val r = rnd.nextDouble()

      val s =
        if (r < 0.25) randomABC(len)
        else if (r < 0.45) {
          val choice = rnd.nextInt(3)
          val delta = 1 + rnd.nextInt(math.max(1, k))
          val (i, j, m) = choice match {
            case 0 => (k + delta, k, math.max(0, k - delta))
            case 1 => (k, k + delta, math.max(0, k - delta))
            case _ => (math.max(0, k - delta), k, k + delta)
          }
          val raw = ("a" * i) + ("b" * j) + ("c" * m)
          if (raw.length == len) raw
          else if (raw.length > len) raw.take(len)
          else raw + ("c" * (len - raw.length))
        }
        else if (r < 0.65) permutedBlocks(k)
        else if (r < 0.85) multiSwapWrongOrder(k)
        else boundaryCorruption(k)

      if (!isValid(s) && !posSet.contains(s) && !negSet.contains(s)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // (aa)*
  // ==============================================================================================

  def generateAAStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateAAStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateAAStar(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      var len = minLen + rnd.nextInt(maxLen - minLen + 1)
      if (len % 2 != 0) len += 1
      if (len == 0) len = 2
      val s = "a" * len
      positives += s
      posSet += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      var l = len
      if (l % 2 == 0) l += 1
      val s = "a" * l
      if (!posSet.contains(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // b*  (name kept as your original generateAStar)
  // ==============================================================================================

  def generateAStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateAStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateAStar(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isValid(s: String): Boolean = s.forall(_ == 'b')

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = "b" * len
      positives += s
      posSet += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      var s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (isValid(s)) {
        val chars = s.toArray
        chars(rnd.nextInt(len)) = 'a'
        s = new String(chars)
      }
      if (!posSet.contains(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // (ab)*
  // ==============================================================================================

  def generateABStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateABStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateABStar(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isValid(s: String): Boolean =
      s.length % 2 == 0 && s.indices.forall(i => if (i % 2 == 0) s(i) == 'a' else s(i) == 'b')

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      var len = minLen + rnd.nextInt(maxLen - minLen + 1)
      if (len % 2 != 0) len += 1
      if (len == 0) len = 2
      positives += ("ab" * (len / 2))
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val r = rnd.nextDouble()
      val s =
        if (r < 0.3) (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
        else if (r < 0.6) {
          var l = len; if (l % 2 == 0) l += 1
          (0 until l).map(i => if (i % 2 == 0) 'a' else 'b').mkString
        } else {
          var l = len; if (l % 2 != 0) l += 1
          val chars = (0 until l).map(i => if (i % 2 == 0) 'a' else 'b').toArray
          val idx = rnd.nextInt(l)
          chars(idx) = if (chars(idx) == 'a') 'b' else 'a'
          new String(chars)
        }
      if (!isValid(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // σ*(ab)σ*
  // ==============================================================================================

  def generateContainsAB(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateContainsAB(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateContainsAB(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    val alphabet = Array('a', 'b')
    def containsAB(s: String): Boolean = s.contains("ab")

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val len = math.max(2, minLen + rnd.nextInt(maxLen - minLen + 1))
      val abPos = rnd.nextInt(len - 1)
      val chars = Array.fill(len)(alphabet(rnd.nextInt(2)))
      chars(abPos) = 'a'
      chars(abPos + 1) = 'b'
      val s = new String(chars)
      if (!posSet.contains(s)) { positives += s; posSet += s }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()

    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => alphabet(rnd.nextInt(2))).mkString
      if (!containsAB(s) && !negSet.contains(s)) { negatives += s; negSet += s }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // Majority (#b >= #a)
  // ==============================================================================================

  def generateMajority(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateMajority(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateMajority(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isMajority(s: String): Boolean = s.count(_ == 'b') >= s.count(_ == 'a')

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) 'a' else 'b').mkString
      if (isMajority(s) && !posSet.contains(s)) { positives += s; posSet += s }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) 'a' else 'b').mkString
      if (!isMajority(s) && !negSet.contains(s)) { negatives += s; negSet += s }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // Existential (kept your semantics: positives are mostly 'a' with some 'b'; negatives are all 'b')
  // ==============================================================================================

  def generateExistential(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateExistential(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateExistential(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val chars = Array.fill(len)('a')
      val numB = 1 + rnd.nextInt(len)
      for (_ <- 1 to numB) chars(rnd.nextInt(len)) = 'b'
      val s = new String(chars)
      if (!posSet.contains(s)) { positives += s; posSet += s }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    // allow duplicates if range is small
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      negatives += ("b" * len)
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // Parity (even #b)
  // ==============================================================================================

  def generateParity(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateParity(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateParity(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isEvenBs(s: String): Boolean = s.count(_ == 'b') % 2 == 0

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s0 = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      val s = if (isEvenBs(s0)) s0 else {
        val idx = rnd.nextInt(len)
        val chars = s0.toCharArray
        chars(idx) = if (chars(idx) == 'b') 'a' else 'b'
        new String(chars)
      }
      if (!posSet.contains(s)) { positives += s; posSet += s }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()

    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s0 = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      val s = if (!isEvenBs(s0)) s0 else {
        val idx = rnd.nextInt(len)
        val chars = s0.toCharArray
        chars(idx) = if (chars(idx) == 'b') 'a' else 'b'
        new String(chars)
      }
      if (!negSet.contains(s)) { negatives += s; negSet += s }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // LastB
  // ==============================================================================================

  def generateLastB(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateLastB(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateLastB(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    val chars = Vector('a', 'b', 'e')

    def isValid(s: String): Boolean = {
      val lastNonE = s.lastIndexWhere(_ != 'e')
      lastNonE != -1 && s(lastNonE) == 'b'
    }

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()

    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val raw = (1 to len).map(_ => chars(rnd.nextInt(3))).mkString
      val lastNonE = raw.lastIndexWhere(_ != 'e')
      val s =
        if (lastNonE == -1) {
          val arr = raw.toCharArray; arr(rnd.nextInt(len)) = 'b'; new String(arr)
        } else if (raw(lastNonE) == 'b') raw
        else {
          val arr = raw.toCharArray; arr(lastNonE) = 'b'; new String(arr)
        }
      if (isValid(s) && !posSet.contains(s)) { positives += s; posSet += s }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()

    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val raw = (1 to len).map(_ => chars(rnd.nextInt(3))).mkString
      val lastNonE = raw.lastIndexWhere(_ != 'e')
      val s =
        if (lastNonE == -1) raw
        else if (raw(lastNonE) == 'a') raw
        else {
          val arr = raw.toCharArray; arr(lastNonE) = 'a'; new String(arr)
        }
      if (!isValid(s) && !negSet.contains(s)) { negatives += s; negSet += s }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // Tomita (a/b only)
  // ==============================================================================================

  def generateTomita1(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita1(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita1(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    val positives = new scala.collection.mutable.ListBuffer[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      positives += ("a" * len)
    }
    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      negatives += (if (s.forall(_ == 'a')) s + "b" else s)
    }
    (positives.toList, negatives.toList)
  }

  // Tomita2: (ab)*
  def generateTomita2(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita2(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita2(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    val positives = new scala.collection.mutable.ListBuffer[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val k = math.max(1, len / 2)
      positives += ("ab" * k)
    }
    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      negatives += (if (s.matches("(ab)*")) s + "a" else s)
    }
    (positives.toList, negatives.toList)
  }

  def generateTomita3(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita3(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita3(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    def isInvalid(s: String): Boolean = {
      var i = 0
      while (i < s.length) {
        if (s(i) == 'a') {
          var runA = 0
          while (i < s.length && s(i) == 'a') { runA += 1; i += 1 }
          if (runA % 2 != 0 && i < s.length && s(i) == 'b') {
            var runB = 0
            while (i < s.length && s(i) == 'b') { runB += 1; i += 1 }
            if (runB % 2 != 0) return true
          }
        } else i += 1
      }
      false
    }

    // Critical boundary examples that cover all 7 DFA states.
    // These are the shortest strings that distinguish each state transition.
    // Always include them so the learner sees the hardest cases regardless of random seed.
    val criticalNeg = List("ab","abbb","aaab","aaabbb","aabab","aababbb")
    val criticalPos = List("a","b","aa","bb","aab","abb","aabb","ba","bba","aabba","aabbab")

    val posSet = scala.collection.mutable.LinkedHashSet.empty[String]
    criticalPos.filter(!isInvalid(_)).foreach(posSet.addOne)
    while (posSet.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (!isInvalid(s)) posSet.addOne(s)
    }

    val negSet = scala.collection.mutable.LinkedHashSet.empty[String]
    criticalNeg.filter(isInvalid).foreach(negSet.addOne)
    while (negSet.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (isInvalid(s)) negSet.addOne(s)
    }

    (posSet.toList.take(nPos), negSet.toList.take(nNeg))
  }

  def generateTomita4(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita4(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita4(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isValid(s: String): Boolean = !s.contains("aaa")
    def randomWord(len: Int): String = (0 until len).map(_ => if (rnd.nextBoolean()) 'a' else 'b').mkString

    val positives = scala.collection.mutable.LinkedHashSet.empty[String]
    val negatives = scala.collection.mutable.LinkedHashSet.empty[String]

    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = randomWord(len)
      if (isValid(s)) positives += s
    }

    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      if (len >= 3) {
        val pos = rnd.nextInt(len - 2)
        val prefix = (0 until pos).map(_ => if (rnd.nextBoolean()) 'b' else 'a').mkString
        val suffixLen = len - pos - 3
        val suffix = (0 until suffixLen).map(_ => if (rnd.nextBoolean()) 'b' else 'a').mkString
        negatives += (prefix + "aaa" + suffix)
      }
    }

    (positives.toList, negatives.toList)
  }

  def generateTomita5(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita5(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita5(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isValid(s: String): Boolean = s.count(_ == 'b') % 2 == 0 && s.count(_ == 'a') % 2 == 0

    val positives = new scala.collection.mutable.ListBuffer[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (isValid(s)) positives += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (!isValid(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  def generateTomita6(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita6(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita6(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isValid(s: String): Boolean = (s.count(_ == 'b') - s.count(_ == 'a')) % 3 == 0

    val positives = new scala.collection.mutable.ListBuffer[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (isValid(s)) positives += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (!isValid(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  def generateTomita7(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateTomita7(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateTomita7(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)
    def isValidAB(s: String): Boolean = s.matches("a*b*a*b*")

    def sample4(len: Int): (Int, Int, Int, Int) = {
      val c1 = rnd.nextInt(len + 1)
      val c2 = rnd.nextInt(len + 1)
      val c3 = rnd.nextInt(len + 1)
      val cuts = Array(c1, c2, c3).sorted
      val k1 = cuts(0)
      val k2 = cuts(1) - cuts(0)
      val k3 = cuts(2) - cuts(1)
      val k4 = len - cuts(2)
      (k1, k2, k3, k4)
    }

    val positives = new scala.collection.mutable.LinkedHashSet[String]()
    while (positives.size < nPos) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val (k1, k2, k3, k4) = sample4(len)
      positives += (("a" * k1) + ("b" * k2) + ("a" * k3) + ("b" * k4))
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) "b" else "a").mkString
      if (!isValidAB(s)) negatives += s
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // a*b*
  // ==============================================================================================

  def generateAStarBStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateAStarBStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateAStarBStar(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    require(minLen >= 0, s"minLen must be >= 0, got $minLen")
    require(maxLen >= minLen, s"maxLen must be >= minLen, got minLen=$minLen maxLen=$maxLen")

    val rnd = new Random(seed)

    def isValid(s: String): Boolean = {
      var seenB = false
      var i = 0
      while (i < s.length) {
        s.charAt(i) match {
          case 'a' => if (seenB) return false
          case 'b' => seenB = true
          case _   => return false
        }
        i += 1
      }
      true
    }

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    def mkPos(len: Int): String = {
      val k = if (len == 0) 0 else rnd.nextInt(len + 1)
      ("a" * k) + ("b" * (len - k))
    }

    def mkNeg(len: Int): String = {
      val L = math.max(2, len)
      val i = rnd.nextInt(L - 1)
      val j = i + 1 + rnd.nextInt(L - i - 1)

      val arr = Array.fill(L)('a')
      arr(i) = 'b'
      arr(j) = 'a'

      var t = 0
      while (t < L) {
        if (t != i && t != j) arr(t) = (if (rnd.nextBoolean()) 'a' else 'b')
        t += 1
      }
      new String(arr)
    }

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()
    while (positives.size < nPos) {
      val len = sampleLen()
      val s = mkPos(len)
      positives += s
      posSet += s
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()
    var guard = 0
    val guardMax = 2000000

    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives (got ${negatives.size}/$nNeg). Consider widening ranges.")

      val len = sampleLen()
      val s = mkNeg(len)
      if (!isValid(s) && !posSet.contains(s) && !negSet.contains(s)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }
  def generateExistsB(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)

    // Positive: at least one 'b'
    def mkPos(len0: Int): String = {
      val len = math.max(1, len0) // need room to place a b
      val arr = Array.fill(len)('a')
      val bPos = rnd.nextInt(len)
      arr(bPos) = 'b'
      // optionally sprinkle extra bs
      var i = 0
      while (i < len) {
        if (i != bPos && rnd.nextDouble() < 0.15) arr(i) = 'b'
        i += 1
      }
      new String(arr)
    }

    // Negative: no 'b' => all 'a'
    def mkNeg(len: Int): String =
      "a" * len

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    val positives = new scala.collection.mutable.ListBuffer[String]()
    val posSet = new scala.collection.mutable.HashSet[String]()
    while (positives.size < nPos) {
      val len = sampleLen()
      val s = mkPos(len)
      if (!posSet.contains(s) || (maxLen - minLen + 1) < nPos) {
        positives += s
        posSet += s
      }
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    val negSet = new scala.collection.mutable.HashSet[String]()
    while (negatives.size < nNeg) {
      val len = sampleLen()
      val s = mkNeg(len)
      // if the length-range is small and nNeg is large, uniqueness may be impossible,
      // so allow duplicates once exhausted
      if (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // Optional backwards-compatible wrapper
  def generateExistsB(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateExistsB(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generatePiecewiseAB(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    // Positive: ensure there exists i<j with s(i)='a' and s(j)='b'
    // Construct by forcing an 'a' then later a 'b', and randomizing the rest.
    def mkPos(len0: Int): String = {
      val len = math.max(2, len0) // need at least 2 positions to guarantee a before b
      val arr = Array.fill(len)('a')
      val i = rnd.nextInt(len - 1)                 // position for 'a'
      val j = i + 1 + rnd.nextInt(len - i - 1)     // later position for 'b'
      // randomize everything
      var t = 0
      while (t < len) {
        arr(t) = if (rnd.nextBoolean()) 'a' else 'b'
        t += 1
      }
      // enforce the witness
      arr(i) = 'a'
      arr(j) = 'b'
      new String(arr)
    }

    // Negative: strings in b* a* (no 'a' before any later 'b')
    def mkNeg(len: Int): String = {
      val split = if (len == 0) 0 else rnd.nextInt(len + 1) // b^split a^(len-split)
      ("b" * split) + ("a" * (len - split))
    }

    // membership checks (cheap + exact)
    def isPos(s: String): Boolean = {
      // exists a before b  <=>  there is some 'b' with an 'a' earlier
      var seenA = false
      var k = 0
      while (k < s.length) {
        if (s.charAt(k) == 'a') seenA = true
        else if (s.charAt(k) == 'b' && seenA) return true
        k += 1
      }
      false
    }
    def isNeg(s: String): Boolean = !isPos(s)

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    while (positives.size < nPos) {
      val s = mkPos(sampleLen())
      // should always be positive, but keep the guard anyway
      if (isPos(s) && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
        positives += s
        posSet += s
      }
    }

    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]
    while (negatives.size < nNeg) {
      val s = mkNeg(sampleLen())
      if (isNeg(s) && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // optional wrapper (keeps old call-sites working)
  def generatePiecewiseAB(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generatePiecewiseAB(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  // ==============================================================================================
  // Dn: D1 = ε, Dn = a D(n-1) b  ==>  D2="ab", D3="aabb", D4="aaabbb"
  // ==============================================================================================

  private def dnWord(n: Int): String = {
    require(n >= 2, s"need n>=2 for Dn, got n=$n")
    val k = n - 1
    ("a" * k) + ("b" * k)
  }

  private def randAB(rnd: Random, len: Int): String =
    (0 until len).map(_ => if (rnd.nextBoolean()) 'a' else 'b').mkString

  private def generateDnWithRange(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int,
    n: Int
  ): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)
    val target = dnWord(n)

    // Positives: singleton language => duplicates are inevitable
    val positives = List.fill(nPos)(target)

    // Negatives: sample in [minLen,maxLen], avoid exactly target
    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]

    var guard = 0
    val guardMax = 2000000

    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives for D$n (got ${negatives.size}/$nNeg).")

      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = randAB(rnd, len)

      if (s != target && !negSet.contains(s)) {
        negatives += s
        negSet += s
      }
    }

    (positives, negatives.toList)
  }

  // -------- D2 --------
  def generateD2(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateD2(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateD2(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) =
    generateDnWithRange(nPos, nNeg, minLen, maxLen, seed, n = 2)

  // -------- D3 --------
  def generateD3(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateD3(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateD3(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) =
    generateDnWithRange(nPos, nNeg, minLen, maxLen, seed, n = 3)

  // -------- D4 --------
  def generateD4(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateD4(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateD4(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) =
    generateDnWithRange(nPos, nNeg, minLen, maxLen, seed, n = 4)

  // -------- D12 --------
  def generateD12(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateD12(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateD12(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) =
    generateDnWithRange(nPos, nNeg, minLen, maxLen, seed, n = 12)

  // ==============================================================================================
  // (a|b)* ab (a|b)*
  // ==============================================================================================

  def generateSigmaStarABSigmaStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateSigmaStarABSigmaStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generateSigmaStarABSigmaStar(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)
    val sigma = Array('a','b')

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    def isPos(s: String): Boolean = s.contains("ab")

    def mkPos(len0: Int): String = {
      val len = math.max(2, len0)
      val arr = Array.fill(len)(sigma(rnd.nextInt(2)))
      val i = rnd.nextInt(len - 1)
      arr(i) = 'a'
      arr(i + 1) = 'b'
      new String(arr)
    }

    // build a string with no "ab" (i.e., in b* a*)
    def mkNeg(len0: Int): String = {
      val len = math.max(0, len0)
      val split = if (len == 0) 0 else rnd.nextInt(len + 1) // b^split a^(len-split)
      ("b" * split) + ("a" * (len - split))
    }

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    while (positives.size < nPos) {
      val s = mkPos(sampleLen())
      if (isPos(s) && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
        positives += s
        posSet += s
      }
    }

    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]
    while (negatives.size < nNeg) {
      val s = mkNeg(sampleLen())
      // mkNeg guarantees !isPos(s), but keep guard anyway
      if (!isPos(s) && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }


  // ==============================================================================================
  // (abab)*
  // ==============================================================================================

  def generateABABStar(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generateABABStar(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)
  
  def generateABABStar(nPos: Int, nNeg: Int, minLen: Int, maxLen: Int, seed: Int): (List[String], List[String]) = {
    val rnd = new Random(seed)

    val positives = new scala.collection.mutable.ListBuffer[String]()
    while (positives.size < nPos) {
      val k = (minLen + rnd.nextInt(maxLen - minLen + 1)) / 4
      val effectiveK = if (k == 0) 1 else k
      positives += ("abab" * effectiveK)
    }

    val negatives = new scala.collection.mutable.ListBuffer[String]()
    while (negatives.size < nNeg) {
      val len = minLen + rnd.nextInt(maxLen - minLen + 1)
      val s = (1 to len).map(_ => if (rnd.nextBoolean()) 'a' else 'b').mkString
      negatives += (if (s.matches("(abab)*")) s + "a" else s)
    }

    (positives.toList, negatives.toList)
  }
  
  def generatePiecewiseABC(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    // Membership: Σ* a Σ* b Σ* c Σ*
    def isPos(s: String): Boolean = {
      // seenA: have we seen an 'a' somewhere?
      // seenAB: have we seen an 'a' then later a 'b'?
      var seenA = false
      var seenAB = false
      var i = 0
      while (i < s.length) {
        s.charAt(i) match {
          case 'a' => seenA = true
          case 'b' => if (seenA) seenAB = true
          case 'c' => if (seenAB) return true
          case _   => return false
        }
        i += 1
      }
      false
    }

    def isNeg(s: String): Boolean = !isPos(s)

    // Positive constructor: force i<j<k with a,b,c and randomize the rest.
    def mkPos(len0: Int): String = {
      val len = math.max(3, len0) // need room for a,b,c in order
      val arr = Array.fill(len)('a')

      // pick i<j<k safely
      val i = rnd.nextInt(len - 2)                  // 0..len-3
      val j = i + 1 + rnd.nextInt(len - i - 1)      // i+1..len-1
      val kMin = j + 1
      val k =
        if (kMin >= len) {
          // if j ended up at len-1, repair by using fixed tail witness
          val j2 = len - 2
          val i2 = rnd.nextInt(j2)
          var t = 0
          while (t < len) { arr(t) = Vector('a','b','c')(rnd.nextInt(3)); t += 1 }
          arr(i2) = 'a'; arr(j2) = 'b'; arr(len - 1) = 'c'
          return new String(arr)
        } else kMin + rnd.nextInt(len - kMin)

      // randomize all chars
      var t = 0
      while (t < len) { arr(t) = Vector('a','b','c')(rnd.nextInt(3)); t += 1 }

      // enforce witness
      arr(i) = 'a'
      arr(j) = 'b'
      arr(k) = 'c'
      new String(arr)
    }

    // Negative constructor (safe subset of complement):
    // Any block-permutation that is NOT "abc" avoids subsequence a..b..c.
    def mkNeg(len: Int): String = {
      // choose a permutation of blocks among the 6; exclude "abc"
      val perms = Vector("acb", "bac", "bca", "cab", "cba")
      val p = perms(rnd.nextInt(perms.length))

      // split len into 3 block sizes
      val c1 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val c2 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val cuts = Array(c1, c2).sorted
      val k1 = cuts(0)
      val k2 = cuts(1) - cuts(0)
      val k3 = len - cuts(1)

      def block(ch: Char, k: Int): String = ch.toString * k

      def blockOf(sym: Char, k: Int): String =
        sym match {
          case 'a' => block('a', k)
          case 'b' => block('b', k)
          case _   => block('c', k)
        }

      val s1 = blockOf(p(0), k1)
      val s2 = blockOf(p(1), k2)
      val s3 = blockOf(p(2), k3)
      s1 + s2 + s3
    }


    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    while (positives.size < nPos) {
      val s = mkPos(sampleLen())
      if (isPos(s) && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
        positives += s
        posSet += s
      }
    }

    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]
    var guard = 0
    val guardMax = 2000000

    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives (got ${negatives.size}/$nNeg). Consider widening ranges.")

      val s = mkNeg(sampleLen())
      if (isNeg(s) && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // optional wrapper (old API)
  def generatePiecewiseABC(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generatePiecewiseABC(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  def generatePiecewiseABCDE(
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd = new Random(seed)
    val sigma = Vector('a','b','c','d','e')

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    // Membership: Σ* a Σ* b Σ* c Σ* d Σ* e Σ*
    def isPos(s: String): Boolean = {
      var stage = 0 // 0 need a, 1 need b, 2 need c, 3 need d, 4 need e, 5 done
      var i = 0
      while (i < s.length && stage < 5) {
        val ch = s.charAt(i)
        stage match {
          case 0 => if (ch == 'a') stage = 1
          case 1 => if (ch == 'b') stage = 2
          case 2 => if (ch == 'c') stage = 3
          case 3 => if (ch == 'd') stage = 4
          case 4 => if (ch == 'e') stage = 5
          case _ =>
        }
        i += 1
      }
      stage == 5
    }

    def isNeg(s: String): Boolean = !isPos(s)

    // Positive constructor: force i1<i2<i3<i4<i5 with a,b,c,d,e; randomize the rest.
    def mkPos(len0: Int): String = {
      val len = math.max(5, len0)
      val arr = Array.fill(len)('a')

      // pick 5 increasing indices using sorted unique cuts approach
      // choose 5 distinct positions
      val idxs = scala.collection.mutable.HashSet.empty[Int]
      while (idxs.size < 5) idxs += rnd.nextInt(len)
      val sorted = idxs.toArray.sorted

      // randomize everything
      var t = 0
      while (t < len) { arr(t) = sigma(rnd.nextInt(sigma.length)); t += 1 }

      // enforce witness
      arr(sorted(0)) = 'a'
      arr(sorted(1)) = 'b'
      arr(sorted(2)) = 'c'
      arr(sorted(3)) = 'd'
      arr(sorted(4)) = 'e'
      new String(arr)
    }

    // Negative constructor: safe subset of complement by using a block-permutation
    // that is NOT "abcde". Any other block order prevents the subsequence abcde.
    def mkNeg(len: Int): String = {
      val perms = Vector(
        "abced", "abdce", "abdec", "abecd", "abedc",
        "acbde", "acbed", "acdeb", "acedb", "ac edb".replace(" ",""),
        "bacde", "bcade", "bdcae", "cabde", "cdeab",
        "edcba", "ebdca", "dceba", "ceadb", "bdeca"
      )
      // The list just needs to avoid "abcde"; duplicates are fine.
      val p = perms(rnd.nextInt(perms.length))

      // split len into 5 block sizes via 4 cuts
      val c1 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val c2 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val c3 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val c4 = if (len == 0) 0 else rnd.nextInt(len + 1)
      val cuts = Array(c1, c2, c3, c4).sorted
      val k1 = cuts(0)
      val k2 = cuts(1) - cuts(0)
      val k3 = cuts(2) - cuts(1)
      val k4 = cuts(3) - cuts(2)
      val k5 = len - cuts(3)

      def rep(ch: Char, k: Int): String = ch.toString * k

      val s1 = rep(p(0), k1)
      val s2 = rep(p(1), k2)
      val s3 = rep(p(2), k3)
      val s4 = rep(p(3), k4)
      val s5 = rep(p(4), k5)
      s1 + s2 + s3 + s4 + s5
    }

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    while (positives.size < nPos) {
      val s = mkPos(sampleLen())
      if (isPos(s) && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
        positives += s
        posSet += s
      }
    }

    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]
    var guard = 0
    val guardMax = 2000000

    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives (got ${negatives.size}/$nNeg). Consider widening ranges.")

      val s = mkNeg(sampleLen())
      if (isNeg(s) && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
        negatives += s
        negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // optional wrapper
  def generatePiecewiseABCDE(n: Int, minLen: Int, maxLen: Int, seed: Int = 42): (List[String], List[String]) =
    generatePiecewiseABCDE(nPos = n, nNeg = n, minLen = minLen, maxLen = maxLen, seed = seed)

  /**
   * Generic piecewise-testable generator for n letters (n >= 2).
   * Accepts strings that contain a,b,...,letters(n-1) as an ordered subsequence.
   */
  def generatePiecewise(
    n: Int,
    nPos: Int,
    nNeg: Int,
    minLen: Int,
    maxLen: Int,
    seed: Int
  ): (List[String], List[String]) = {
    require(n >= 2, s"n must be >= 2, got $n")
    require(minLen >= 0 && maxLen >= minLen, s"bad range: minLen=$minLen maxLen=$maxLen")
    val rnd    = new Random(seed)
    val sigma  = (0 until n).map(i => ('a' + i).toChar).toVector

    def sampleLen(): Int =
      if (maxLen == minLen) minLen else minLen + rnd.nextInt(maxLen - minLen + 1)

    // Membership: must find sigma(0), sigma(1), ..., sigma(n-1) as ordered subsequence
    def isPos(s: String): Boolean = {
      var stage = 0
      var i = 0
      while (i < s.length && stage < n) {
        if (s.charAt(i) == sigma(stage)) stage += 1
        i += 1
      }
      stage == n
    }

    // Positive: force n sorted positions with sigma(0..n-1), randomize rest
    def mkPos(len0: Int): String = {
      val len = math.max(n, len0)
      val arr = Array.fill(len)(sigma(rnd.nextInt(n)))
      val idxs = scala.collection.mutable.HashSet.empty[Int]
      while (idxs.size < n) idxs += rnd.nextInt(len)
      val sorted = idxs.toArray.sorted
      for (k <- 0 until n) arr(sorted(k)) = sigma(k)
      new String(arr)
    }

    // Negative: block-permutation that is NOT the identity permutation
    def mkNeg(len: Int): String = {
      // pick a random non-identity permutation of 0..n-1
      var perm = (0 until n).toArray
      // swap first two elements to guarantee non-identity
      val tmp = perm(0); perm(0) = perm(1); perm(1) = tmp
      // additionally shuffle to add variety
      for (i <- n - 1 to 2 by -1) {
        val j = rnd.nextInt(i + 1)
        val t = perm(i); perm(i) = perm(j); perm(j) = t
      }
      // split len into n blocks
      val cuts = Array.fill(n - 1)(if (len == 0) 0 else rnd.nextInt(len + 1)).sorted
      val sizes = new Array[Int](n)
      sizes(0) = cuts(0)
      for (k <- 1 until n - 1) sizes(k) = cuts(k) - cuts(k - 1)
      sizes(n - 1) = len - cuts(n - 2)
      (0 until n).map(k => sigma(perm(k)).toString * sizes(k)).mkString
    }

    val positives = scala.collection.mutable.ListBuffer.empty[String]
    val posSet    = scala.collection.mutable.HashSet.empty[String]
    while (positives.size < nPos) {
      val s = mkPos(sampleLen())
      if (isPos(s) && (!posSet.contains(s) || (maxLen - minLen + 1) < nPos)) {
        positives += s; posSet += s
      }
    }

    val negatives = scala.collection.mutable.ListBuffer.empty[String]
    val negSet    = scala.collection.mutable.HashSet.empty[String]
    var guard = 0
    val guardMax = 2000000
    while (negatives.size < nNeg) {
      guard += 1
      if (guard > guardMax)
        throw new RuntimeException(s"Too many attempts generating negatives (got ${negatives.size}/$nNeg).")
      val s = mkNeg(sampleLen())
      if (!isPos(s) && !posSet.contains(s) && (!negSet.contains(s) || (maxLen - minLen + 1) < nNeg)) {
        negatives += s; negSet += s
      }
    }

    (positives.toList, negatives.toList)
  }

  // ==============================================================================================
  // Save / Load
  // ==============================================================================================

  def save(pos: List[String], neg: List[String], filename: String): Unit = {
    val file = new File("dataset/" + filename)
    if (file.getParentFile != null) file.getParentFile.mkdirs()
    val pw = new PrintWriter(file)
    try {
      pos.foreach(s => pw.println(s"+,$s"))
      neg.foreach(s => pw.println(s"-,$s"))
    } finally pw.close()
  }

  def save(filename: String, positives: List[String], negatives: List[String]): Unit =
    save(positives, negatives, filename)

  def load(filename: String): (List[String], List[String]) = {
    val f = new File(filename)
    val finalPath = if (f.exists()) filename else "dataset/" + filename
    val lines = Source.fromFile(finalPath).getLines().toList
    val (pos, neg) = lines.partition(_.startsWith("+"))
    (
      pos.map(_.stripPrefix("+,").trim),
      neg.map(_.stripPrefix("-,").trim)
    )
  }
}


