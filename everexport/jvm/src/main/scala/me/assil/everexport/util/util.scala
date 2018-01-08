package me.assil.everexport

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

package object util {
  /** Returns an [[ExecutionContext]] consisting of a given number of threads. */
  def getFixedPool(poolSize: Int = 4): ExecutionContext = {
    val e = Executors.newFixedThreadPool(poolSize)
    ExecutionContext.fromExecutor(e)
  }

  /** Returns a new [[ExecutionContext]] based on a `CachedThreadPool`.  */
  def getCachedPool: ExecutionContext = {
    val e = Executors.newCachedThreadPool()
    ExecutionContext.fromExecutor(e)
  }
}
