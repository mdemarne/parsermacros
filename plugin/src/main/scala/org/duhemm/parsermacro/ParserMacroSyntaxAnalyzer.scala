package org.duhemm.parsermacro

import scala.tools.nsc.ast.parser.{ SyntaxAnalyzer => NscSyntaxAnalyzer, TreeBuilder }
import scala.reflect.internal.Phase

import scala.tools.nsc.ast.parser.BracePatch
import scala.tools.nsc.ast.parser.Tokens._

import scala.reflect.internal.Flags
import scala.collection.mutable.ListBuffer

// Partially taken from scalameta/scalahost
/**
 * New syntax analyzer that behaves just like scalac's default syntax analyzer, but is able
 * to parse parser macro applications.
 */
abstract class ParserMacroSyntaxAnalyzer extends NscSyntaxAnalyzer { self =>
  import global._

  override val runsAfter: List[String] = Nil
  override val runsRightAfter: Option[String] = None
  override val initial = true

  private def initialUnitBody(unit: CompilationUnit): Tree = {
    if (unit.isJava) new JavaUnitParser(unit).parse()
    else if (currentRun.parsing.incompleteHandled) new ParserMacroUnitParser(unit).parse()
    else new ParserMacroUnitParser(unit).smartParse()
  }

  def newUnitParser(unit: CompilationUnit): UnitParser = new ParserMacroUnitParser(unit)
  private class ParserMacroUnitParser(unit: global.CompilationUnit, patches: List[BracePatch]) extends UnitParser(unit, patches) {
    def this(unit: global.CompilationUnit) = this(unit, Nil)
    override def withPatches(patches: List[BracePatch]): UnitParser = new UnitParser(unit, patches)
    override def newScanner() = new UnitScanner(unit, patches)

    override def topStat: PartialFunction[Token, List[Tree]] = {
      case _ if isIdent =>
        parserMacro :: Nil
      case t if super.topStat isDefinedAt t =>
        super.topStat(t)
      case _ =>
        syntaxErrorOrIncompleteAnd("expected class or object definition", skipIt = true)(Nil)
    }

    private def parserMacro: Tree = {
      val base = qualId()
      if (in.token == DOT) parserMacroApplication(selectors(base, false, in.offset))
      else parserMacroApplication(base)
    }

    private def parserMacroApplication(t: Tree): Tree = {
      in.token match {
        case HASH =>
          val macroParserArgs = new ListBuffer[String]
          while(in.token == HASH) {
            in.nextToken()
            val endToken = in.token match {
              case LPAREN => RPAREN
              case LBRACE => RBRACE
              case other  => syntaxErrorOrIncompleteAnd(s"'(' or '{' expected but ${token2string(other)} found.", skipIt = true)(ERROR)
            }
            in.nextToken()
            val start = in.offset
            while(in.token != endToken && in.token != ERROR && in.token != EOF) {
              in.nextToken()
            }
            val end = in.offset
            macroParserArgs += in.buf.slice(start, end).mkString("")
            accept(endToken)
          }
          t updateAttachment ParserMacroArgumentsAttachment(macroParserArgs.toList)

        case other =>
          syntaxErrorOrIncompleteAnd(s"'#' expected but ${token2string(other)} found.", skipIt = true)(EmptyTree)

      }
    }

    override def simpleExprRest(t: Tree, canApply: Boolean): Tree = {
      if (canApply) newLineOptWhenFollowedBy(LBRACE)
      in.token match {
        case HASH if (canApply) =>
          parserMacroApplication(t)
        case _ =>
          super.simpleExprRest(t, canApply)
      }
    }
  }

  override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
    override val checkable = false
    override val keepsTypeParams = false

    def apply(unit: CompilationUnit) {

      informProgress("parsing " + unit)
      // if the body is already filled in, don't overwrite it
      // otherwise compileLate is going to overwrite bodies of synthetic source files
      if (unit.body == EmptyTree)
        unit.body = initialUnitBody(unit)

      if (settings.Yrangepos && !reporter.hasErrors)
        validatePositions(unit.body)

      if (settings.Ymemberpos.isSetByUser)
        new MemberPosReporter(unit) show (style = settings.Ymemberpos.value)
    }
  }
}
