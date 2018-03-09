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

package org.platanios.symphony.mt.vocabulary

import org.platanios.symphony.mt.data.DataConfig
import org.platanios.tensorflow.api._

import better.files._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets

import scala.collection.mutable

/** Represents a vocabulary of words.
  *
  * @param  file File containing the vocabulary, with one word per line.
  * @param  size Size of this vocabulary (i.e., number of words).
  *
  * @author Emmanouil Antonios Platanios
  */
case class Vocabulary private[Vocabulary] (file: File, size: Int) {
  /** Creates a vocabulary lookup table (from word string to word ID), from the provided vocabulary file.
    *
    * @return Vocabulary lookup table.
    */
  def lookupTable(name: String = "IndexTableFromFile"): tf.HashTable = {
    Vocabulary.indexTableFromFile(
      file.path.toAbsolutePath.toString, defaultValue = Vocabulary.UNKNOWN_TOKEN_ID, name = name)
  }
}

/** Contains utilities for dealing with vocabularies. */
object Vocabulary {
  private[this] val logger: Logger = Logger(LoggerFactory.getLogger("Vocabulary"))

  /** Creates a new vocabulary from the provided vocabulary file.
    *
    * The method first checks if the specified vocabulary file exists and if it does, it checks that special tokens are
    * being used correctly. If not, this method can optionally create a new file by prepending the appropriate tokens to
    * the existing one.
    *
    * The special tokens check simply involves checking whether the first three tokens in the vocabulary file match the
    * specified `unknownToken`, `beginSequenceToken`, and `endSequenceToken` values.
    *
    * @param  file               Vocabulary file to check.
    * @param  checkSpecialTokens Boolean value indicating whether or not to check for the use of special tokens, and
    *                            prepend them while creating a new vocabulary file, if the check fails.
    * @param  directory          Directory to use when creating the new vocabulary file, in case the special tokens
    *                            check fails. Defaults to the current directory in which `file` is located, meaning
    *                            that if the special tokens check fails, `file` will be replaced with the appended
    *                            vocabulary file.
    * @param  dataConfig         Data configuration that includes information about the special tokens.
    * @return Constructed vocabulary.
    * @throws IllegalArgumentException If the provided vocabulary file could not be loaded.
    */
  @throws[IllegalArgumentException]
  def apply(
      file: File,
      checkSpecialTokens: Boolean = true,
      directory: File = null,
      dataConfig: DataConfig = DataConfig()
  ): Vocabulary = {
    val check = Vocabulary.check(
      file, checkSpecialTokens, directory,
      dataConfig.unknownToken, dataConfig.beginOfSequenceToken, dataConfig.endOfSequenceToken)
    check match {
      case None => throw new IllegalArgumentException(s"Could not load the vocabulary file located at '$file'.")
      case Some((size, path)) => Vocabulary(path, size)
    }
  }

  val UNKNOWN_TOKEN          : String = "<unk>"
  val BEGIN_OF_SEQUENCE_TOKEN: String = "<s>"
  val END_OF_SEQUENCE_TOKEN  : String = "</s>"
  val UNKNOWN_TOKEN_ID       : Int    = 0

  /** Checks if the specified vocabulary file exists and if it does, checks that special tokens are being used
    * correctly. If not, this method can optionally create a new file by prepending the appropriate tokens to the
    * existing one.
    *
    * The special tokens check simply involves checking whether the first three tokens in the vocabulary file match the
    * specified `unknownToken`, `beginSequenceToken`, and `endSequenceToken` values.
    *
    * @param  file                 Vocabulary file to check.
    * @param  checkSpecialTokens   Boolean value indicating whether or not to check for the use of special tokens, and
    *                              prepend them while creating a new vocabulary file, if the check fails.
    * @param  directory            Directory to use when creating the new vocabulary file, in case the special tokens
    *                              check fails. Defaults to the current directory in which `file` is located, meaning
    *                              that if the special tokens check fails, `file` will be replaced with the appended
    *                              vocabulary file.
    * @param  unknownToken         Special token for unknown tokens. Defaults to `<unk>`.
    * @param  beginOfSequenceToken Special token for the beginning of a sequence. Defaults to `<s>`.
    * @param  endOfSequenceToken   Special token for the end of a sequence. Defaults to `</s>`.
    * @return Option containing the number of tokens and the checked vocabulary file, which could be a new file.
    */
  private[Vocabulary] def check(
      file: File,
      checkSpecialTokens: Boolean = true,
      directory: File = null,
      unknownToken: String = UNKNOWN_TOKEN,
      beginOfSequenceToken: String = BEGIN_OF_SEQUENCE_TOKEN,
      endOfSequenceToken: String = END_OF_SEQUENCE_TOKEN
  ): Option[(Int, File)] = {
    if (file.notExists) {
      None
    } else {
      logger.info(s"Vocabulary file '$file' exists.")
      val reader = file.newBufferedReader(StandardCharsets.UTF_8)
      val tokens = mutable.ListBuffer.empty[String]
      var line = reader.readLine()
      while (line != null) {
        tokens ++= line.split("\\s+").toSeq
        line = reader.readLine()
      }
      reader.close()
      if (!checkSpecialTokens) {
        Some((tokens.size, file))
      } else {
        // Verify that the loaded vocabulary using the right special tokens.
        // If it does not, use those tokens and generate a new vocabulary file.
        assert(tokens.lengthCompare(3) >= 0, "The loaded vocabulary must contain at least three tokens.")
        if (tokens(0) != unknownToken ||
            tokens(1) != beginOfSequenceToken ||
            tokens(2) != endOfSequenceToken) {
          logger.info(
            s"The first 4 vocabulary tokens [${tokens(0)}, ${tokens(1)}, ${tokens(2)}] " +
                s"are not equal to [$unknownToken, $beginOfSequenceToken, $endOfSequenceToken].")
          tokens.prepend(unknownToken, beginOfSequenceToken, endOfSequenceToken)
          val newFile = if (directory != null) directory.sibling(file.name) else file
          val writer = newFile.newBufferedWriter(StandardCharsets.UTF_8)
          tokens.foreach(token => writer.write(s"$token\n"))
          writer.close()
          logger.info(s"Created fixed vocabulary file at '$newFile'.")
          Some((tokens.size, newFile))
        } else {
          Some((tokens.size, file))
        }
      }
    }
  }

  /** Creates vocabulary lookup tables (from word string to word ID), from the provided vocabulary files.
    *
    * @param  srcFile Source vocabulary file.
    * @param  tgtFile Target vocabulary file.
    * @return Tuple contain the source vocabulary lookup table and the target one.
    */
  private[Vocabulary] def createTables(srcFile: File, tgtFile: File): (tf.HashTable, tf.HashTable) = {
    val srcPath = srcFile.path.toAbsolutePath.toString
    val tgtPath = tgtFile.path.toAbsolutePath.toString
    val sourceTable = indexTableFromFile(srcPath, defaultValue = UNKNOWN_TOKEN_ID)
    val targetTable = {
      if (srcFile == tgtFile)
        sourceTable
      else
        indexTableFromFile(tgtPath, defaultValue = UNKNOWN_TOKEN_ID)
    }
    sourceTable.initialize()
    if (srcFile != tgtFile)
      targetTable.initialize()
    (sourceTable, targetTable)
  }

  /** Creates a lookup table that converts string tensors into integer IDs.
    *
    * This operation constructs a lookup table to convert tensors of strings into tensors of `INT64` IDs. The mapping
    * is initialized from a vocabulary file specified in `filename`, where the whole line is the key and the zero-based
    * line number is the ID.
    *
    * The underlying table must be initialized by executing the `tf.tablesInitializer()` op or the op returned by
    * `table.initialize()`.
    *
    * Example usage:
    *
    * If we have a vocabulary file `"test.txt"` with the following content:
    * {{{
    *   emerson
    *   lake
    *   palmer
    * }}}
    * Then, we can use the following code to create a table mapping `"emerson" -> 0`, `"lake" -> 1`, and
    * `"palmer" -> 2`:
    * {{{
    *   val table = tf.indexTableFromFile("test.txt"))
    * }}}
    *
    * @param  filename          Filename of the text file to be used for initialization. The path must be accessible
    *                           from wherever the graph is initialized (e.g., trainer or evaluation workers).
    * @param  delimiter         Delimiter to use in case a `TextFileColumn` extractor is being used.
    * @param  vocabularySize    Number of elements in the file, if known. If not known, set to `-1` (the default value).
    * @param  defaultValue      Default value to use if a key is missing from the table.
    * @param  keysDataType      Data type of the table keys.
    * @param  name              Name for the created table.
    * @return Created table.
    */
  private[Vocabulary] def indexTableFromFile(
      filename: String, delimiter: String = "\t", vocabularySize: Int = -1, defaultValue: Long = -1L,
      keysDataType: DataType = STRING, name: String = "IndexTableFromFile"
  ): tf.HashTable = {
    tf.createWithNameScope(name) {
      tf.createWithNameScope("HashTable") {
        val sharedName = {
          if (vocabularySize != -1)
            s"hash_table_${filename}_${vocabularySize}_${tf.TextFileWholeLine}_${tf.TextFileLineNumber}"
          else
            s"hash_table_${filename}_${tf.TextFileWholeLine}_${tf.TextFileLineNumber}"
        }
        val initializer = tf.LookupTableTextFileInitializer(
          filename, if (keysDataType.isInteger) INT64 else keysDataType, INT64,
          tf.TextFileWholeLine, tf.TextFileLineNumber, delimiter, vocabularySize)
        tf.HashTable(initializer, defaultValue, sharedName = sharedName, name = "Table")
      }
    }
  }
}
