//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled

/** Models a limited quantity of syntax information on a per-character basis in a buffer.
  * This makes life easier for various code-grokking routines, which need to know if they're
  * looking at a comment, or a string literal, or actual code.
  *
  * A mode can make use of custom syntax instances if desired, but most general purpose code should
  * base behavior on the results of the syntax methods rather than object identity.
  */
abstract class Syntax {

  /** Returns true if this syntax represents a comment of some sort. */
  def isComment :Boolean

  /** Returns true if this syntax represents a char, string or integer literal. */
  def isLiteral :Boolean
}

/** Various standard syntax singletons. */
object Syntax {

  /** The default syntax. Interpreted as actual code. */
  val Default = new Syntax {
    def isComment = false
    def isLiteral = false
  }

  /** A singleton [[Syntax]] instance for tagging line comments. */
  val LineComment = new Syntax {
    def isComment = true
    def isLiteral = false
  }

  /** A singleton [[Syntax]] instance for tagging block comments. */
  val BlockComment = new Syntax {
    def isComment = true
    def isLiteral = false
  }

  /** A singleton [[Syntax]] instance for tagging doc comments. */
  val DocComment = new Syntax {
    def isComment = true
    def isLiteral = false
  }

  /** A singleton [[Syntax]] instance for tagging string literals. */
  val StringLiteral = new Syntax {
    def isComment = false
    def isLiteral = true
  }

  /** A singleton [[Syntax]] instance for tagging character literals. */
  val CharLiteral = new Syntax {
    def isComment = false
    def isLiteral = true
  }

  /** A singleton [[Syntax]] instance for tagging non-string, non-char literals (like integer or
    * floating point literals). */
  val OtherLiteral = new Syntax {
    def isComment = false
    def isLiteral = true
  }
}