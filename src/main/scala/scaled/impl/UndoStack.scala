//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled.impl

import scala.collection.mutable.ArrayBuffer

import scaled._

/** Tracks changes to a buffer, aggregating sequences of individual changes into single undoable
  * actions. Then handles reversing said actions, on request.
  */
class UndoStack (view :RBufferView) extends Undoer {

  view.buffer.edited.onValue { edit => accum += edit }
  view.buffer.lineEdited.onValue { edit => accum += edit }

  def actionWillStart () :Unit = _point = view.point

  def actionDidComplete () :Unit = if (!_edits.isEmpty) {
    accumTo(_edits, _actions)
    // since we've applied one or more normal edits, clear the redo buffer
    _redoActions.clear()
  }

  override def undo () :Boolean = {
    if (_actions.isEmpty) false
    else {
      _undoing = true
      try {
        _actions.last.undo()
        _actions.trimEnd(1)
        accumTo(_redoEdits, _redoActions)
      } finally {
        _undoing = false
      }
      true
    }
  }

  override def redo () :Boolean = {
    if (_redoActions.isEmpty) false
    else {
      _redoActions.last.undo()
      _redoActions.trimEnd(1)
      // accumulate the redone actions immediately so that the actionDidComplete that naturally
      // follows a redo does not see uncommitted edits and think that the user just made a normal
      // edit (which would clear the redo list)
      accumTo(_edits, _actions)
      true
    }
  }

  // returns the buffer onto which to accumulate buffer edits; normally we accumulate to the edits
  // buffer, but when we're undoing, we accumulate edits to the redo buffer because the edits that
  // come in during that time are triggered by our undoing, not by user actions
  private def accum = if (_undoing) _redoEdits else _edits

  private def accumTo (edits :ArrayBuffer[Undoable], actions :ArrayBuffer[Action]) {
    if (!edits.isEmpty) {
      // determine whether the edits we're accumulating are a simple single character insert (which
      // we call 'typing') and whether the inserted character is a word break character
      val (isTyping, isWordBreak) = edits match {
        case Seq(le :Line.Edit) =>
          val isSingleChar = le.deleted == 0 && le.added == 1
          (isSingleChar, isSingleChar && isBreakChar(le.addedChar(0)))
        case _ => (false, false)
      }

      // if we're typing and the top of the undo stack is more typing merge this insertion as well;
      // this enables us to undo simple typing in larger chunks; we stop merging when we hit a word
      // break character (i.e. space)
      val action = if (isTyping && actions.size > 0 && actions.last.isTyping && !isWordBreak) {
        val accum = actions.last.accum(edits)
        actions.trimEnd(1)
        accum
      }
      else Action(_point, Seq() ++ edits, isTyping)
      actions += action
      edits.clear()
    }
  }

  private def isBreakChar (c :Char) = Character.isWhitespace(c) // TODO: delegate this to the mode

  private val _edits = ArrayBuffer[Undoable]()
  private val _actions = ArrayBuffer[Action]()
  private val _redoEdits = ArrayBuffer[Undoable]()
  private val _redoActions = ArrayBuffer[Action]()
  private var _point = Loc(0, 0)
  private var _undoing = false
  private var _redoing = false

  private case class Action (point :Loc, edits :Seq[Undoable], isTyping :Boolean) {
    def undo () :Unit = {
      // undo the edits in the reverse of the order they were accumulated
      edits.reverse.foreach { _.undo() }
      // then restore the point
      view.point = point
    }

    // accumulates additional edits to this action
    def accum (edits :Seq[Undoable]) = Action(point, this.edits ++ edits, isTyping)
  }
}