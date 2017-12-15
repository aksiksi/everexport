package me.assil.everexport

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

package object util {
  def getExecutionContext(poolSize: Int = 4): ExecutionContext = {
    val e = Executors.newFixedThreadPool(poolSize)
    ExecutionContext.fromExecutor(e)
  }
}
