package edu.umass.cs.iesl.tackbp.vis.admin

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor

import java.util.concurrent.{ConcurrentLinkedQueue, ExecutorService, Executors, ThreadFactory, TimeUnit}
import play.api.libs.ws.WS
import scala.concurrent.ExecutionContext


trait PlayWebServicing {

  val webService = WS

  implicit val service: ExecutorService = {
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
  }

  implicit val pool = ExecutionContext.fromExecutorService(service)

  /**
   * Shut down the underlying <code>SingleThreadScheduledExecutor</code>
   */
  def shutdown(): Unit = synchronized {
    service.shutdown 
    pool.shutdown
  }

}
