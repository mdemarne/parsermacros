package macros
import scala.meta._
import scala.meta.dialects.Scala211

object LegacyMacros {
  def countTokensImpl(tokens: Seq[Token]) = {
    val count = tokens.length
    val res = internal.ast.Lit.Int(count)
    res
  }
  def countTokens: Int = macro countTokensImpl

  def alwaysReturnOneImpl(p1: Seq[Token], p2: Seq[Token]) = {
    internal.ast.Lit.Int(1)
  }
  def alwaysReturnOne: Int = macro alwaysReturnOneImpl

  def compatibleParameterTypeImpl(tokens: Iterable[Token]): Tree = internal.ast.Lit.Int(1)
  def compatibleParameterType = macro compatibleParameterTypeImpl

  def compatibleReturnTypeImpl(tokens: Seq[Token]): internal.ast.Lit = internal.ast.Lit.Int(1)
  def compatibleReturnType = macro compatibleReturnTypeImpl

  def hasTypeParametersImpl[T](tokens: Seq[Token]): internal.ast.Lit = internal.ast.Lit.Int(1)
  def hasTypeParameters[T] = macro hasTypeParametersImpl[T]

  def hasTypeParametersTooImpl[T >: Token](token: Seq[T]): Tree = internal.ast.Lit.Int(1)
  def hasTypeParametersToo[T >: Token] = macro hasTypeParametersTooImpl[T]

  // Wrong implementations of parser macros
  def tooManyParamLists(tokens: Seq[Token])(otherTokens: Seq[Token]): Tree = ???
  def incompatibleParameterTypes(something: Int): Tree = ???
  def incompatibleParameterSeqType(tokens: List[Token]): Tree = ???
  def incompatibleReturnType(tokens: Seq[Token]): Any = ???

}

// The test `Accept a macro whose implementation is defined in an abstract parent` breaks
// if this is an abstract class, but passes if this is a trait. I have absolutely no idea
// regarding what happens here. It was working before I started implementing support for
// lightweight syntax.
trait AbstractProvider {
  def abstractImpl(tokens: Seq[Token]): Tree

  def concreteImpl(tokens: Any): Tree = {
    internal.ast.Lit.Int(1)
  }
}

object ConcreteProvider extends AbstractProvider {

  override def abstractImpl(tokens: Seq[Token]): Tree =
    internal.ast.Lit.Int(1)
  def overrideAbstractImplFromParent: Int = macro abstractImpl

  def concreteImplInAbstractParent: Int = macro concreteImpl
}
