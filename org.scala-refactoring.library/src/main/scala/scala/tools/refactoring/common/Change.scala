/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package common

import scala.tools.nsc.io.AbstractFile
import scala.reflect.internal.util.SourceFile

/**
 * The common interface for all changes.
 *
 * Note: in versions < 0.4 Change was a case-class, now it's the
 * super type of `TextChange` and `NewFileChange`. `NewFileChanges
 * are used by refactorings that create new source files (Move Class).
 *
 * Additionally, the `file` attribute is now of type `SourceFile`,
 * because parts of the refactoring process need to access the content
 * of the  underlying source file.
 */
sealed trait Change {
  val text: String

  @deprecated("Pattern-match on the subclasses.", "0.4.0")
  def file: AbstractFile

  @deprecated("Pattern-match on the subclasses.", "0.4.0")
  def from: Int

  @deprecated("Pattern-match on the subclasses.", "0.4.0")
  def to: Int
}

case class TextChange(sourceFile: SourceFile, from: Int, to: Int, text: String) extends Change {

  def file = sourceFile.file

  /**
   * Instead of a change to an existing file, return a change that creates a new file
   * with the change applied to the original file.
   *
   * @param fullNewName The fully qualified package name of the target.
   */
  def toNewFile(fullNewName: String) = {
    val src = Change.applyChanges(List(this), new String(sourceFile.content))
    NewFileChange(fullNewName, src)
  }
}

/**
 * The changes creates a new source file, indicated by the `fullName` parameter. It is of
 * the form "some.package.FileName".
 */
case class NewFileChange(fullName: String, text: String) extends Change {

  def file = throw new UnsupportedOperationException
  def from = throw new UnsupportedOperationException
  def to   = throw new UnsupportedOperationException
}

object Change {
  /**
   * Applies the list of changes to the source string. NewFileChanges are ignored.
   * Primarily used for testing / debugging.
   */
  def applyChanges(ch: List[Change], source: String): String = {
    val changes = ch collect {
      case tc: TextChange => tc
    }

    val sortedChanges = changes.sortBy(-_.to)

    /* Test if there are any overlapping text edits. This is
       not necessarily an error, but Eclipse doesn't allow
       overlapping text edits, and this helps us catch them
       in our own tests. */
    sortedChanges.sliding(2).toList foreach {
      case List(TextChange(_, from, _, _), TextChange(_, _, to, _)) =>
        assert(from >= to)
      case _ => ()
    }

    (source /: sortedChanges) { (src, change) =>
      src.substring(0, change.from) + change.text + src.substring(change.to)
    }
  }
}
