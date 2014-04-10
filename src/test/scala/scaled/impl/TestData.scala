//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled.impl

import java.io.{File, StringReader}

import reactual.{Future, Promise}

import scaled._
import scaled.major.TextMode

/** Helper methods for creating test instances of things. */
object TestData {

  val editor = new Editor {
    def showURL (url :String) {}
    def defer (op :Runnable) = op.run()
    def mini[R] (mode :String, result :Promise[R], args :Any*) :Future[R] = result
    def emitStatus (msg :String) = println(msg)
    def emitError (err :Throwable) = err.printStackTrace(System.err)
    def clearStatus () {}
    def exit (code :Int) {}
    def buffers = Seq()
    def openBuffer (buffer :String) {}
    def visitFile (file :File) = null
    def visitConfig (name :String) = null
    def killBuffer (buffer :String) = false
  }

  val config = new ConfigImpl("scaled", EditorConfig :: Nil, None)

  val resolver = new ModeResolver(editor) {
    override protected def locate (major :Boolean, mode :String) = Future.success(classOf[TextMode])
    override protected def resolveConfig (mode :String, defs :List[Config.Defs]) =
      modeConfig(mode, defs)
  }

  def modeConfig (mode :String, defs :List[Config.Defs]) = new ConfigImpl(mode, defs, Some(config))

  def env (view_ :RBufferView) = new Env {
    val editor = TestData.editor
    val view = view_
    val disp = null
    def resolveConfig (mode :String, defs :List[Config.Defs]) = modeConfig(mode, defs)
  }

  /** Creates a test buffer. For testing! */
  def buffer (name :String, text :String) =
    BufferImpl(name, new File(name), new StringReader(text))
}
