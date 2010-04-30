/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.refactoring.transformation

import scala.tools.nsc.io.AbstractFile
import scala.collection.mutable.HashSet
import scala.tools.nsc.util.NoPosition
import scala.tools.nsc.util.RangePosition
import scala.tools.nsc.ast.parser.Tokens
import scala.tools.nsc.symtab.Flags
import scala.tools.refactoring.common.Changes

trait Transform extends Changes {
  outer =>
  
  val global: scala.tools.nsc.interactive.Global
  import global._

  def transform(root: Tree)(trans: PartialFunction[Tree, Tree]): Tree = {
    new Transformer {
      override def transform(tree: Tree): Tree = {
        
        val res = super.transform(tree)
        
        if(trans.isDefinedAt(res)) {
          trans(res)
        } else {
          res
        }
      }
    }.transform(root)
  }
  
  trait ModificationCollector extends TreeModifications {
    
    def substitute(t1: Tree, t2: Tree) = recordWhenChanged(t1, t2)

    private def recordWhenChanged(orig: Tree, changed: Tree) = if(orig != changed && changed.pos.isRange) changes += changed
    
    def transform2(root: Tree)(check: PartialFunction[Tree, Boolean])(trans: PartialFunction[Tree, Tree]): Tree = {
      new Transformer {
        override def transform(tree: Tree): Tree = {
          if(check.isDefinedAt(tree) && check(tree)) {
            val result = trans(super.transform(tree))
            recordWhenChanged(tree, result)
            result
          } else {
            super.transform(tree)
          }
        }
      }.transform(root)
    }
    
    def transform(root: Tree)(trans: PartialFunction[Tree, Tree]): Tree = {
      new Transformer {
        override def transform(tree: Tree): Tree = {
          
          val res = super.transform(tree)
          
          if(trans.isDefinedAt(res)) {
            val result = trans(res)
            recordWhenChanged(tree, result)
            result
          } else {
            res
          }
        }
      }.transform(root)
    }
    
    def toplevelTrees = topChanges distinct
  
    def allChangedTrees = changes toList

    private var changes = new collection.mutable.ListBuffer[Tree]
    
    private def topChanges = {
      
      def properlyIncludes(t1: Tree, t2: Tree) = t1.pos.source == t2.pos.source && t1.pos.properlyIncludes(t2.pos)
      
      def findSuperTrees(trees: List[Tree], superTrees: List[Tree]): List[Tree] = trees match {
        case Nil => superTrees
        case t :: ts =>
        
          def mergeOverlappingTrees(ts: List[Tree]): List[Tree] = ts match {
            case Nil => t :: Nil
            case x :: xs if properlyIncludes(x, t) => x :: xs
            case x :: xs if properlyIncludes(t, x) => t :: xs
            case x :: xs => x :: mergeOverlappingTrees(xs)
          }
        
          findSuperTrees(ts, mergeOverlappingTrees(superTrees))
      }
      
      findSuperTrees(changes toList, Nil)
    }
  }
  
  implicit def blockToTreeList(b: Block) = b.stats ::: b.expr :: Nil
  
  implicit def abstractFileToTree(file: AbstractFile): global.Tree = global.unitOfFile(file).body
    
  def replace[T](from: List[T], what: List[T], replacement: List[T]): List[T] = (from, what) match {
    case (Nil, _) => Nil
    case (xs, Nil) => xs
    case (x :: xs, y :: ys) if x == y => replacement ::: replace(xs, ys, Nil)
    case (x :: xs, _) => x :: replace(xs, what, replacement)
  }
}