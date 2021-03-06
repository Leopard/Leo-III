package leo.modules.external

import java.io.{OutputStream, InputStream}
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

/**
  * <p>
  * Companionobject to the KillableProcess.
  * Allows sigkill to be send to the currently executing process.
  * </p>
  * <p>
  * Known exit Codes (maybe) generated by Java:
  * <dl>
  *   <dt>255</dt>
  *   <dd>Wrong exitCode OR wrong parameter for the call</dd>
  * </dl>
  * </p>
  */
object KillableProcess {

  private[external] val isUNIXSystem : Boolean= {
    try {
      val process = Runtime.getRuntime.exec("echo a")
      process.getClass().getName().equals("java.lang.UNIXProcess")    // If it allows unix like processes
    } catch{
      case _ : Exception => false
    }
  }

  /**
    * Creates a killable process.
    *
    * @param cmd the command to be executed
    * @return A Processhandle to the killable process
    */
  def apply(cmd : String) : KillableProcess = Command(cmd).exec()
}

/**
  * Killable Process. Calls for SigKill.
  */
trait KillableProcess {

  /**
    *
    * Waits for timeout[unit] time for the process to finish.
    * Returns true, if the process has finished, false otherwise.
    *
    * @param timout The amount of time to wait for the process to finish
    * @param unit The unit of the time waiting
    * @return true iff the process has successfully terminated
    */
  @throws[InterruptedException]
  def waitFor(timout : Long, unit : TimeUnit) : Boolean

  /**
    *
    * Waits for the processes to finish
    *
    * @return true iff the process has successfully terminated
    */
  @throws[InterruptedException]
  def waitFor() : Boolean

  /**
    * Returns the exit value of a process
    *
    * @return The exit Value of the process
    * @throws IllegalThreadStateException if the process has not yet finished
    */
  @throws[IllegalThreadStateException]
  def exitValue : Int

  /**
    * Checks for the process, if it is still alive.
    * @return
    */
  def isAlive : Boolean

  /**
    *
    * Returns the output of the spawned process in a ReadOnly Stream
    *
    * @return stdout of the process
    */
  @throws[InterruptedException]
  def output : InputStream

  /**
    * Allows to write to the process in a WriteOnly Stream
    *
    * @return stdin of the process
    */
  @throws[InterruptedException]
  def input : OutputStream

  /**
    * Returns the error of the spawned process in a ReadOnly Stream
    *
    * @return stderr of the process
    */
  @throws[InterruptedException]
  def error : InputStream

  /**
    * Sends a sigkill to the process
    */
  @throws[InterruptedException]
  def kill : Unit
}

case class Command(cmd : String) {
  def exec() : KillableProcess = {
    import scala.sys.process._
    val cmd1 : Array[String] = if(KillableProcess.isUNIXSystem) Array("/bin/bash","-c",cmd) else {Array(cmd)}   // If it is a Unix like system, we allow chaining
    val process = Runtime.getRuntime.exec(cmd1)
    new KillableProcessImpl(process)
  }
}


private class KillableProcessImpl(process : Process) extends KillableProcess {

  override def isAlive = process.isAlive

  override def exitValue: Int = process.exitValue()

  override def output: InputStream = process.getInputStream
  override def input: OutputStream = process.getOutputStream
  override def error: InputStream = process.getErrorStream

  override def kill: Unit = {
    pid match {
      case Some(pid1) => Runtime.getRuntime.exec("kill -9 "+pid1)
      case None => process.destroyForcibly()
    }
  }

  private def pid : Option[Int] = {
    if(process.getClass().getName().equals("java.lang.UNIXProcess")) {
      /* get the PID on unix/linux systems */
      try {
        val f : Field = process.getClass().getDeclaredField("pid")
        f.setAccessible(true)
        val p = f.getInt(process)
        return Some(p)
      } catch {
        case _ : Throwable => return None
      }
    } else {
      return None
    }
  }
  override def waitFor(timout: Long, unit: TimeUnit): Boolean = process.waitFor(timout, unit)
  override def waitFor(): Boolean = process.waitFor() > 0
}
