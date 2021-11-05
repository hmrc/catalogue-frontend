/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend.util

import uk.gov.hmrc.cataloguefrontend.service.ServiceDependency

import scala.annotation.tailrec

object DependencyGraphParser {

  def parse(input: String): DependencyGraph = {
    val graph = lexer(input.split("\n"))
      .foldLeft(DependencyGraph.empty) { (graph, t)  =>
        t match {
          case n: Node     => graph.copy(nodes     = graph.nodes      + n)
          case a: Arrow    => graph.copy(arrows    = graph.arrows     + a)
          case e: Eviction => graph.copy(evictions = graph.evictions  + e)
        }
      }

    // maven generated digraphs don't add nodes, but the main dependency is named the same as the digraph
    if (graph.nodes.isEmpty) {
      val nodesFromRoot = graph.arrows.map(_.to) ++ graph.arrows.map(_.from)
      graph.copy(nodes = nodesFromRoot)
    } else {
      graph
    }
  }

  private val eviction     = """\s*"(.+)" -> "(.+)" \[(.+)\]\s*""".r
  private val arrow        = """\s*"(.+)" -> "(.+)"\s*;?\s*""".r
  private val node         = """\s*"(.+)"\[(.+)\]\s*""".r
  private val styleEvicted = "stroke-dasharray"

  private def lexer(lines: Seq[String]): Seq[Token] =
    lines.flatMap {
      case node(n, style) if !style.contains(styleEvicted) => Some(Node(n)) // drops evicted nodes
      case arrow(a, b)       => Some(Arrow(Node(a), Node(b)))
      case eviction(a, b, r) => Some(Eviction(Node(a), Node(b), r))
      case _                 => None
    }

  private val group           = """([^:]+)"""
  private val artefact        = """([^:]+?)"""           // make + lazy so it does not capture the optional scala version
  private val optScalaVersion = """(?:_(?:\d+\.\d+))?""" // non-capturing groups to get _ and scala version
  private val optType         = """(?::(?:jar|war))?"""  // non-capturing group to get optional type
  private val optClassifier   = """(?::[^:]+)??"""       // a lazy non-capturing group to get optional classifier, lazy so it doesn't eat the version
  private val version         = """([^:]+)"""
  private val optScope        = """(?::(?:compile|runtime|test|system|provided))?""" // optional non-capturing group for scope
  private val nodeRegex = (s"$group:$artefact$optScalaVersion$optType$optClassifier:$version$optScope").r

  sealed trait Token

  case class Node(name: String) extends Token {

    private lazy val dep = name match {
      case nodeRegex(g, a, v) => ServiceDependency(g, a, v)
    }

    def asServiceDependency: ServiceDependency = dep

  }

  case class Arrow(from: Node, to: Node) extends Token

  case class Eviction(old: Node, by: Node, reason: String) extends Token

  case class DependencyGraph(
                              nodes    : Set[Node],
                              arrows   : Set[Arrow],
                              evictions: Set[Eviction]
                            ) {

    def dependencies: Seq[ServiceDependency] =
      nodes.toSeq.map(_.asServiceDependency)

    def dependenciesWithImportPath: Seq[(ServiceDependency, Seq[ServiceDependency])] =
      nodes
        .toSeq
        .map(n => (n.asServiceDependency, pathToRoot(n).map(_.asServiceDependency) ))
        .filter(_._2.length > 1)

    private lazy val arrowsMap: Map[Node, Node] =
      arrows.map(a => a.to -> a.from).toMap

    private def pathToRoot(node: Node): Seq[Node] = {
      @tailrec
      def go(node: Node, acc: Seq[Node]): Seq[Node] = {
        val acc2 = acc :+ node
        arrowsMap.get(node) match {
          case Some(n) => go(n, acc2)
          case None    => acc2
        }
      }
      go(node, Seq.empty)
    }
  }

  object DependencyGraph {
    def empty: DependencyGraph =
      DependencyGraph(
        nodes     = Set.empty,
        arrows    = Set.empty,
        evictions = Set.empty)
  }
}
