package slacks.core.program

import cats._, data._, implicits._

/*
 * Canonical state class (i.e. s -> (a, s)) for managing changes to datum
 */
case class Cursor(init: String) {
  private[this] var currentCursor : String = ""

  /* Updating the cursor */
  def updateCursor : State[String, Boolean] =
    State{(newCursor: String) ⇒
      currentCursor = newCursor
      (currentCursor, true)
    }

  /* Obtain the current stored cursor */
  def getCursor : String = State.get[String].runS(currentCursor).value
}

