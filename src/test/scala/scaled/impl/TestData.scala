//
// Scaled - a scalable editor extensible via JVM languages
// http://github.com/samskivert/scaled/blob/master/LICENSE

package scaled.impl

import java.io.{File, StringReader}

import reactual.Future

import scaled.Editor

/** Helper methods for creating test instances of things. */
object TestData {

  val editor = new Editor {
    val killRing = new KillRingImpl(10)
    def showURL (url :String) {}
    def miniRead (prompt :String, defval :String, completer :String => Set[String]) =
      Future.success("test")
    def emitStatus (msg :String) = println(msg)
    def clearStatus () {}
    def exit (code :Int) {}
    def buffers = Seq()
    def openBuffer (buffer :String) = false
    def newBuffer (file :File) {}
  }

  /** Creates a test buffer. For testing! */
  def buffer (name :String, text :String) =
    BufferImpl(name, new File(name), new StringReader(text))
}
