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

package org.platanios.symphony.mt.experiments

import org.platanios.symphony.mt.Language.{English, Vietnamese}
import org.platanios.symphony.mt.{Environment, Language, LogConfig}
import org.platanios.symphony.mt.data._
import org.platanios.symphony.mt.data.datasets.IWSLT15Dataset
import org.platanios.symphony.mt.models.attention.LuongAttention
import org.platanios.symphony.mt.models.rnn._
import org.platanios.symphony.mt.models.{Model, StateBasedModel}
import org.platanios.symphony.mt.translators.SymphonyTranslator
import org.platanios.symphony.mt.translators.actors.SystemConfig
import org.platanios.symphony.mt.vocabulary.{SimpleVocabularyGenerator, Vocabulary}
import org.platanios.tensorflow.api.learn.StopCriteria
import org.platanios.tensorflow.api.ops.training.optimizers.GradientDescent

import java.nio.file.{Path, Paths}

/**
  * @author Emmanouil Antonios Platanios
  */
object SymphonyIWSLT15 extends App {
  val workingDir: Path = Paths.get("temp").resolve("symphony")

  val srcLang        : Language = English
  val tgtLang        : Language = Vietnamese
  val commonVocabSize: Int      = 10000

  val dataConfig = DataConfig(
    workingDir = workingDir.resolve("data"),
    numBuckets = 5,
    srcMaxLength = 50,
    tgtMaxLength = 50,
    loaderVocab = GeneratedVocabulary(SimpleVocabularyGenerator(commonVocabSize, -1, 8192)))

  val dataset     : LoadedDataset              = IWSLT15Dataset(srcLang, tgtLang, dataConfig).load()
  val datasetFiles: LoadedDataset.GroupedFiles = dataset.files(srcLang, tgtLang)

  val env = Environment(
    workingDir = workingDir,
    numGPUs = 0,
    parallelIterations = 32,
    swapMemory = true,
    randomSeed = Some(10))

  val logConfig = LogConfig(
    logLossSteps = 100,
    logTrainEvalSteps = -1)

  val systemConfig = SystemConfig(
    env = env, 
    interlinguaVocabSize = commonVocabSize, 
    selfTrainSteps = 1000L, 
    trainStepsPerRequest = 10L)

  val trainDataset = () => datasetFiles.createTrainDataset(TRAIN_DATASET, repeat = true)

  //  val trainEvalDataset = () => datasetFiles.createTrainDataset(TRAIN_DATASET, repeat = false, dataConfig.copy(numBuckets = 1), isEval = true)
  //  val devEvalDataset   = () => datasetFiles.createTrainDataset(DEV_DATASET, repeat = false, dataConfig.copy(numBuckets = 1), isEval = true)
  //  val testEvalDataset  = () => datasetFiles.createTrainDataset(TEST_DATASET, repeat = false, dataConfig.copy(numBuckets = 1), isEval = true)

  val trainEvalDataset: () => MTTrainDataset = null
  val devEvalDataset  : () => MTTrainDataset = null
  val testEvalDataset : () => MTTrainDataset = null

  def model(
      srcLang: Language,
      srcVocab: Vocabulary,
      tgtLang: Language,
      tgtVocab: Vocabulary,
      env: Environment
  ): Model = {
    StateBasedModel(
      name = "Model",
      srcLang = srcLang, srcVocab = srcVocab,
      tgtLang = tgtLang, tgtVocab = tgtVocab,
      StateBasedModel.Config(
        env,
        UnidirectionalRNNEncoder(
          cell = BasicLSTM(forgetBias = 1.0f),
          numUnits = 32,
          numLayers = 2,
          residual = false,
          dropout = Some(0.2f),
          timeMajor = true),
        UnidirectionalRNNDecoder(
          cell = BasicLSTM(forgetBias = 1.0f),
          numUnits = 32,
          numLayers = 2,
          residual = false,
          dropout = Some(0.2f),
          attention = Some(LuongAttention(scaled = true)),
          outputAttention = true,
          timeMajor = true,
          beamWidth = 1),
        timeMajor = true,
        maxGradNorm = 5.0f,
        optimizer = GradientDescent(_, _, learningRateSummaryTag = "LearningRate"),
        learningRateInitial = 1.0f,
        learningRateDecayRate = 0.5f,
        learningRateDecaySteps = 12000 * 1 / (3 * 4),
        learningRateDecayStartStep = 12000 * 2 / 3,
        colocateGradientsWithOps = true),
      trainEvalDataset, devEvalDataset, testEvalDataset,
      dataConfig, logConfig)
  }

  val translator = SymphonyTranslator(systemConfig, model, "IWSLT-15")
  translator.train(dataset, StopCriteria.steps(12000))
}
