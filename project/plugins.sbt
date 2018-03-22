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

logLevel := Level.Warn

// addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "latest.integration")

// Plugins used for generating the library website
addSbtPlugin("com.eed3si9n" % "sbt-unidoc"     % "latest.integration")
addSbtPlugin("com.47deg"    % "sbt-microsites" % "latest.integration")

// Packaging and publishing related plugins
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "latest.integration")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "latest.integration")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "latest.integration")

// Provides fast dependency resolution.
addSbtPlugin("io.get-coursier" %  "sbt-coursier" % "latest.integration")
