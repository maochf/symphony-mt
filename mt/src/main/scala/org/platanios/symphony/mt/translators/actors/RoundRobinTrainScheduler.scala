/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.symphony.mt.translators.actors

import org.platanios.symphony.mt.Language
import org.platanios.symphony.mt.data.{BilingualDatasetIterator, ParallelDataset}
import org.platanios.symphony.mt.translators.actors.Messages.{AgentSelfTrainRequest, AgentTrainRequest}
import org.platanios.tensorflow.api.learn.StopCriteria

import akka.actor._

import java.util.concurrent.atomic.AtomicInteger

/**
  * @author Emmanouil Antonios Platanios
  */
class RoundRobinTrainScheduler[T <: ParallelDataset[T]] protected (
    override protected val dataset: ParallelDataset[T],
    override protected val agents: Map[Language, ActorRef],
    val selfTrainSteps: Long = 0L,
    val trainStepsPerRequest: Long = 10L
)(implicit sender: ActorRef = Actor.noSender) extends TrainScheduler[T](dataset, agents) {
  protected var completedSteps: Long = 0L

  /** Contains the train datasets used by this train scheduler. */
  protected val datasets: Map[Language, Seq[(Language, BilingualDatasetIterator[T])]] = {
    val aggregated = dataset.languagePairs.map {
      case (srcLang, tgtLang) =>
        srcLang -> ((tgtLang, BilingualDatasetIterator(
          dataset, srcLang, tgtLang, dataset.dataConfig, repeat = true, isEval = false)))
    }
    aggregated.toSeq.groupBy(_._1).mapValues(_.map(_._2))
  }

  protected var currentIndices: Map[ActorRef, (Language, AtomicInteger)] = {
    agents.map(pair => pair._2 -> ((pair._1, new AtomicInteger(0))))
  }

  /** Initializes this train scheduler. This method is always called by the translation system, in order to start
    * the training process. */
  override def initialize(): Unit = {
    agents.values.foreach(onTrainResponse)
  }

  /** Responds to a translation agent's train response. This method is called by the translation system, whenever it
    * receives an agent train response message. */
  override def onTrainResponse(agent: ActorRef): Unit = {
    completedSteps += 1L
    val (lang, index) = currentIndices(agent)
    var nextIndex = index.getAndIncrement()
    val dataset = datasets(lang)
    if (nextIndex >= dataset.size) {
      index.set(0)
      nextIndex = 0
    }
    val nextDataset = dataset(nextIndex)
    if (completedSteps < selfTrainSteps + 1L)
      agent ! AgentSelfTrainRequest(nextDataset._2.next()._1, StopCriteria.steps(trainStepsPerRequest))
    else
      agent ! AgentTrainRequest(agents(nextDataset._1), nextDataset._2.next(), StopCriteria.steps(trainStepsPerRequest))
  }
}

object RoundRobinTrainScheduler {
  def apply[T <: ParallelDataset[T]](
      dataset: ParallelDataset[T],
      agents: Map[Language, ActorRef],
      selfTrainSteps: Long = 0L,
      trainStepsPerRequest: Long = 10L
  )(implicit
      sender: ActorRef = Actor.noSender
  ): RoundRobinTrainScheduler[T] = {
    new RoundRobinTrainScheduler[T](dataset, agents, selfTrainSteps, trainStepsPerRequest)(sender)
  }
}
