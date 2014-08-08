//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled.impl

import javafx.geometry.VPos
import javafx.scene.Node
import javafx.scene.text.{TextFlow, FontSmoothingType}
import scala.collection.mutable.ArrayBuffer
import scaled._

class LineViewImpl (_line :LineV) extends TextFlow with LineView {

  override def line = _line
  private var _valid = false

  // fontProperty.bind(ctrl.fontProperty)
  // fillProperty.bind(textFill)
  // impl_selectionFillProperty().bind(highlightTextFill)

  /** Returns the x position of character at the specified column.
    * @param charWidth the current width of the (fixed) view font.
    */
  def charX (col :Int, charWidth :Double) :Double = {
    // TODO: handle tabs, other funny business?
    getLayoutX + col*charWidth
  }

  /** Updates this line to reflect the supplied style change. */
  def onStyle (loc :Loc) :Unit = invalidate()

  /** Updates this line's visibility. Lines outside the visible area of the buffer are marked
    * non-visible and defer applying line changes until they are once again visible. */
  def setViz (viz :Boolean) :Boolean = {
    val validated = if (viz && !_valid) { validate() ; true } else false
    setVisible(viz)
    validated
  }

  /** Marks this line view as invalid, clearing its children. */
  def invalidate () :Unit = if (_valid) {
    _valid = false
    getChildren.clear()
    if (isVisible) validate()
  }

  /** Validates this line, rebuilding its visualization. This is called when the line becomes
    * visible. Non-visible lines defer visualization rebuilds until they become visible. */
  def validate () :Unit = if (!_valid) {
    // go through the line and add all of the styled line fragments
    class Adder extends Function3[Seq[Tag[String]],Int,Int,Unit]() {
      private val kids = ArrayBuffer[Node]()
      private var last :Int = 0

      def add (start :Int, end :Int, styles :Seq[Tag[String]]) {
        val text = _line.sliceString(start, end)
        assert(end > start)
        assert(text.indexOf('\r') == -1 && text.indexOf('\n') == -1,
               s"Text cannot have newlines: $text")
        val tnode = new FillableText(text)
        tnode.setFontSmoothingType(FontSmoothingType.LCD)
        val sc = tnode.getStyleClass
        sc.add("textFace")
        styles.foreach(t => sc.add(t.tag))
        tnode.setTextOrigin(VPos.TOP)
        kids += tnode.fillRect
        kids += tnode
      }

      def apply (cls :Seq[Tag[String]], start :Int, end :Int) {
        // if we skipped over any unstyled text, add it now
        if (start > last) add(last, start, Nil)
        add(start, end, cls)
        last = end
      }

      def finish () {
        // if there's trailing unstyled text, add that
        if (last < _line.length) add(last, _line.length, Nil)
        if (!kids.isEmpty) getChildren.addAll(kids.toArray :_*)
      }
    }
    _valid = true // mark ourselves valid now to avoid looping if freakoutery
    val adder = new Adder()
    _line.visitTags(classOf[String])(adder)
    adder.finish()
  }

  override def layoutChildren () {
    super.layoutChildren()
    val iter = getChildren.iterator
    while (iter.hasNext) iter.next match {
      case ft :FillableText => ft.layoutRect()
      case _ => // nada
    }
  }
}
