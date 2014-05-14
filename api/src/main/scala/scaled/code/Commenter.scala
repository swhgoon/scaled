//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled.code

import scaled._
import scaled.util.{Chars, Filler}

/** A helper class for dealing with comments in source code: wrapping, filling, etc. */
class Commenter {
  import CodeConfig._
  import Chars._

  /** The string to prepend to an auto-filled line comment. */
  def linePrefix :String = ""
  /** A matcher on [[linePrefix]]. */
  val linePrefixM = optMatcher(linePrefix)

  /** The string that opens a block comment. In C-like languages, this is slash *. */
  def blockOpen :String = ""
  /** A matcher on [[blockOpen]]. */
  val blockOpenM = optMatcher(blockOpen)

  /** The string that closes a block comment. In C-like languages, this is * slash. */
  def blockClose :String = ""
  /** A matcher on [[blockClose]]. */
  val blockCloseM = optMatcher(blockClose)

  /** The string to prepend to an auto-filled block comment. In C-like languages, this is *. */
  def blockPrefix :String = ""
  /** A matcher on [[blockPrefix]]. */
  val blockPrefixM = optMatcher(blockPrefix)

  /** The string to prepend to an auto-filled doc line. In Java-like languages, this is *. */
  def docPrefix :String = ""
  /** A matcher on [[docPrefix]]. */
  val docPrefixM = optMatcher(docPrefix)

  /** Returns the padding to be intervened between a comment delimiter and the code when
    * automatically inserting comment delimiters. */
  def padding :String = " "

  /** Returns the auto-fill comment prefix for the specified syntax. */
  def prefixFor (syntax :Syntax) :String = {
    import Syntax._
    syntax match {
      case  LineComment => linePrefix + padding
      case BlockComment => blockPrefix + padding
      case   DocComment => docPrefix + padding
      case            _ => ""
    }
  }

  /** Returns true if `p` is "inside" a comment. */
  def inComment (buffer :BufferV, p :Loc) :Boolean = buffer.syntaxNear(p).isComment

  /** Returns the column of start of the comment on `line`. If `line` does not contain comments,
    * `line.length` is returned. */
  def commentStart (line :LineV) :Int = {
    val llen = line.length ; var c = 0
    // first skip over non-comments at the start of the line
    while (c < llen && !line.syntaxAt(c).isComment) c += 1
    // next skip whitespace
    while (c < llen && isWhitespace(line.charAt(c))) c += 1
    // next skip over the comment or doc prefix and we're done
    c += commentDelimLen(line, c)
    // finally skip whitespace again, and we're done
    while (c < llen && isWhitespace(line.charAt(c))) c += 1
    c
  }

  /** Returns the length of the comment delimiter at `col` of `line`. Returns 0 if no comment
    * delimiter is matched at `col`. This is used to skip over the comment delimiter when scanning
    * for comment start. The default implementation checks for [[linePrefix]] or [[docPrefix]].
    */
  def commentDelimLen (line :LineV, col :Int) :Int = {
    if (line.matches(linePrefixM, col)) linePrefixM.matchLength
    else if (line.matches(blockPrefixM, col)) blockPrefixM.matchLength
    else if (line.matches(docPrefixM, col)) docPrefixM.matchLength
    else 0
  }

  /** Generates a comment prefix given the supplied desired comment start column. This combines the
    * appropriate number of spaces with `commentPre` and a single trailing space.
    */
  def commentPre (commentPre :String, startCol :Int) :Line = {
    val spaces = " " * (startCol - commentPre.length - padding.length)
    Line(spaces + linePrefix + padding)
  }

  /** Refills the comments region `[start, end)`. `start` may be a line which contains some
    * non-comment text prior to the comment, but subsequent lines must have only whitespace
    * preceding the start of their comments.
    * @return the refilled comments region.
    */
  def refilled (buffer :BufferV, fillColumn :Int, start :Loc, end :Loc) :Seq[Line] = {
    // the first line dictates the prefix width and fill width
    val firstLine = buffer.line(start)
    val firstCol = commentStart(firstLine)
    // the first line's prefix is preserved as is, for the second+ lines we use a "repeat" prefix
    val firstPre = firstLine.view(start.col, firstCol)
    val repeatPre = {
      // if we have more than one row in our comment block, just use the second line's prefix as our
      // repeat prefix because it's most likely to be correct
      if (end.row > start.row) buffer.line(start.nextStart).view(0, firstPre.length)
      // otherwise create a prefix using the auto-fill prefix for the comment type at firstCol
      else commentPre(prefixFor(firstLine.syntaxAt(firstCol)), firstPre.length)
    }

    // append all of the to-be-filled text to a filler configured with our wrap width
    val filler = new Filler(fillColumn - firstCol)
    filler.append(firstLine.view(firstCol, firstLine.length))
    var loc = start.nextStart ; while (loc < end) {
      val line = buffer.line(loc)
      val last = if (loc.row == end.row) end.col else line.length
      filler.append(line.view(commentStart(line), last))
      loc = loc.nextStart
    }

    // now prepend the appropriate prefix back onto each filled line and replace the original
    // buffer region with our new wrapped results
    val filled = filler.result
    var pres = Seq(firstPre) ++ Seq.fill(filled.length-1)(repeatPre)
    pres zip filled map { case (pre, line) => pre merge line }
  }

  /** Returns the region `[start, end)` commented out. The default implementation prefixes each line
    * by the line comment prefix, indented "appropriately". */
  def lineCommented (buffer :BufferV, start :Loc, end :Loc) :Seq[Line] = {
    val lin = buffer.region(start, end)
    val spaces = " " * lin.filter(_.length > 0).map(Indenter.readIndent).min
    val prefix = Line(spaces + linePrefix + padding)
    val lout = Seq.newBuilder[Line]
    lin foreach { l =>
      lout += (if (l.length > 0) prefix.merge(l) else l)
    }
    lout.result
  }

  /** Inserts block comments around the region `[start,end)` in `buffer`.
    * @return the location after the last inserted comment. */
  def blockComment (buffer :Buffer, start :Loc, end :Loc) :Loc = {
    val cend = buffer.insert(end, Line(padding + blockClose))
    val inserted = buffer.insert(start, Line(blockOpen + padding)).col - start.col
    cend + (0, if (start.row == end.row) inserted else 0)
  }

  /** Trims block comments from the ends of the region `[start,end)` in `buffer`.
    * @return true if block comment delimiters were found and removed, false otherwise. */
  def unBlockComment (buffer :Buffer, start :Loc, end :Loc) :Boolean = {
    val bend = buffer.backward(end, blockClose.length)
    if (!buffer.line(start).matches(blockOpenM, start.col) ||
        !buffer.line(bend).matches(blockCloseM, bend.col)) false
    else {
      // include any whitespace preceding the block end delimiter
      val destart = buffer.line(bend).lastIndexOf(isNotWhitespace, bend.col-1)+1
      buffer.delete(bend.atCol(destart), end)
      // include any whitespace following the block open delimiter
      val send = start.col + blockOpen.length
      val dsend = buffer.line(start).indexOf(isNotWhitespace, send) match {
        case -1 => start + (0, send)
        case ii => start.atCol(ii)
      }
      buffer.delete(start, dsend)
      true
    }
  }

  /** Trims line comments from the starts of every line in `[start,end)` in `buffer`. */
  def unLineComment (buffer :Buffer, start :Loc, end :Loc) {
    var loc = start ; while (loc < end) {
      val line = buffer.line(loc)
      val from = loc.col ; val to = if (loc.row == end.row) end.col else line.length
      val cs = line.indexOf(linePrefixM, from) ; val ce = cs + linePrefix.length
      if (cs != -1 && ce <= to) {
        // delete up to `padding.length` spaces following the line comment delimiter
        val mm = math.min(to, ce + padding.length)
        var de = ce ; while (de < mm && isWhitespace(line.charAt(de))) de += 1
        buffer.delete(loc.atCol(cs), loc.atCol(de))
      }
      loc = loc.nextStart
    }
  }

  // returns a non-matching matcher if text is empty; an exact matcher otherwise
  private def optMatcher (text :String) =
    Matcher.exact(if (text == "") "NOTUSED" else text)
}
