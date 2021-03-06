//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/scaled/scaled/blob/master/LICENSE

package scaled

/** Provides an interface to the undo-tracking mechanism. */
trait Undoer {

  /** Undoes the most recently accumulated action. The changes made by undoing the action will be
    * accumulated as a redo action.
    *
    * @return the point that should be restored to the view if an action was undone, `None` if the
    * undo stack was empty. */
  def undo () :Option[Loc]

  /** Redoes the most recently undone action. The changes made by redoing the action will be
    * accumualted as a normal edit (and can thus be re-undone).
    *
    * @return the point that should be restored to the view if an action was redone, `None` if the
    * redo stack was empty. */
  def redo () :Option[Loc]
}

/** A trait implemented by undoable edits. */
trait Undoable {

  /** Undoes this edit. */
  def undo () :Unit
}
