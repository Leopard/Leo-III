package leo.modules.encoding

import leo.datastructures.{Signature, Term, Type}


/**
  * A LambdaElimStrategy is an identifier for a concrete technique for eliminating
  * lambda abstraction inside higher-order problems.
  * Invoking [[leo.modules.encoding.LambdaElimStrategy#apply]] returns a
  * new instance of that (stateful) elimination procedure wrt. to a certain
  * [[leo.modules.encoding.TypedFOLEncodingSignature]].
  *
  * @see [[leo.modules.encoding.LambdaElimination]]
  * @author Alexander Steen <a.steen@fu-berlin.de>
  */
sealed trait LambdaElimStrategy extends Function1[TypedFOLEncodingSignature, LambdaElimination]
object LambdaElimStrategy_SKI extends LambdaElimStrategy {
  final def apply(sig: TypedFOLEncodingSignature): LambdaElimination = new LambdaElim_SKI(sig)
}
object LambdaElimStrategy_Turner extends LambdaElimStrategy {
  final def apply(sig: TypedFOLEncodingSignature): LambdaElimination = new LambdaElim_Turner(sig)
}
object LambdaElimStrategy_LambdaLifting extends LambdaElimStrategy {
  final def apply(sig: TypedFOLEncodingSignature): LambdaElimination = new LambdaElim_LambdaLifting(sig)
}

/**
  * A LambdaElimination escapsulates a concrete implementation of a
  * procedure for elimination lambda abstraction inside higher-order terms.
  * Popular examples are (i) replacing lambda abstraction by SKI combinators
  * (as done by [[leo.modules.encoding.LambdaElim_SKI]]) or by introducing super-combinators
  * (as done  by [[leo.modules.encoding.LambdaElim_LambdaLifting]]).
  *
  * @note Since, during the transformation, auxiliary constants may be employed,
  *       the transformation is stateful in the sense that the LambdaElimination instance
  *       will keep track of used auxiliary symbols.
  *
  * @author Alexander Steen <a.steen@fu-berlin.de>
  * @since February 2017
  */
protected[encoding] abstract class LambdaElimination(sig: TypedFOLEncodingSignature) {
  /** Given a term `t`, this method returns a term `t'` that does not contain
    * any lambda abstraction^*^ such that `t'` is an first-order encoding
    * of `t`.
    *
    * @note Side-effects:
    *       - This method inserts new symbols into `sig`
    *       - This method manipulates the internal state of
    *       the concrete [[leo.modules.encoding.LambdaElimination]] instance.
    */
  def eliminateLambda(t: Term)(holSignature: Signature): Term

  /** Returns a set of terms which represent auxiliary axioms of symbols used for the translation. */
  def getAuxiliaryDefinitions: Set[Term]
}

protected[encoding] class LambdaElim_SKI(sig: TypedFOLEncodingSignature) extends LambdaElimination(sig) {
  import leo.datastructures.Term.{mkAtom, mkTypeApp}
  import leo.datastructures.Type.{∀, mkVarType}
  import sig.{hApp,funTy}

  private var usedSymbols: Set[Signature#Key] = Set.empty

  /* Combinator definitions from here */
  // Shorthands used for type definitions later
  final private val Xty: Type = mkVarType(1)
  final private val Yty: Type = mkVarType(2)
  final private val Zty: Type = mkVarType(3)

  ////////////
  // I combinator
  ////////////
  private final lazy val combinator_I_key: Signature#Key = {sig.addUninterpreted("$$comb_I", ∀(funTy(Xty,Xty)))}
  /** Curry I combinator given by `I x = x`. */
  final lazy val combinator_I: Term = {
    val id = combinator_I_key
    usedSymbols += id
    mkAtom(id)(sig)
  }
  final def I(ty: Type): Term = mkTypeApp(combinator_I, ty)

  ////////////
  // K combinator
  ////////////
  private final lazy val combinator_K_key: Signature#Key = {sig.addUninterpreted("$$comb_K", ∀(∀(funTy(Xty,funTy(Yty, Xty)))))}
  /** Curry K combinator given by `K x y = x`.
    * `K :: ∀x.∀y. y -> $$fun(x,y)` (`->` only used for readibility. Encoded using $$fun(.)).*/
  final lazy val combinator_K: Term = {
    val id = combinator_K_key
    usedSymbols += id
    mkAtom(id)(sig)
  }
  final def K(ty1: Type, ty2: Type, arg: Term): Term = hApp(mkTypeApp(combinator_K, Seq(ty1, ty2)),arg)

  ////////////
  // S combinator
  ////////////
  private final lazy val combinator_S_key: Signature#Key = {sig.addUninterpreted("$$comb_S",
    ∀(∀(∀(funTy(funTy(Zty,funTy(Xty,Yty)), funTy(funTy(Zty, Xty),funTy(Zty,Yty)))))))}
  /** Curry S combinator given by `S x y z = x z (y z)`. */
  final lazy val combinator_S: Term = {
    val id = combinator_S_key
    usedSymbols += id
    mkAtom(id)(sig)
  }
  final def S(ty1: Type, ty2: Type, ty3: Type, arg1: Term, arg2: Term): Term = {
    hApp(mkTypeApp(combinator_S, Seq(ty1, ty2, ty3)), Seq(arg1, arg2))
  }

  ////////////
  // B combinator
  ////////////
  private final lazy val combinator_B_key: Signature#Key = {sig.addUninterpreted("$$comb_B",
    ∀(∀(∀(funTy(funTy(Xty, Yty), funTy(funTy(Zty, Xty),funTy(Zty, Yty)))))))}
  /** Curry B combinator given by `B x y z = x (y z)`.
    * `B :: ∀z.∀y.∀x. $$fun(x,y) -> $$fun(z,x) -> $$fun(z,y)` (`->` only used for readibility. Encoded using $$fun(.)). */
  final lazy val combinator_B: Term = {
    val id = combinator_B_key
    usedSymbols += id
    mkAtom(id)(sig)
  }
  final def B(ty1: Type, ty2: Type, ty3: Type, arg1: Term, arg2: Term): Term = {
    hApp(mkTypeApp(combinator_B, Seq(ty1, ty2, ty3)), Seq(arg1,arg2))
  }

  ////////////
  // C combinator
  ////////////
  private final lazy val combinator_C_key: Signature#Key = {sig.addUninterpreted("$$comb_C",
    ∀(∀(∀(funTy(funTy(Zty,funTy(Xty,Yty)), funTy(Zty,funTy(Xty,Yty)))))))}
  /** Curry C combinator given by `C x y z = x z y`. */
  final lazy val combinator_C: Term = {
    val id = combinator_C_key
    usedSymbols += id
    mkAtom(id)(sig)
  }
  final def C(ty1: Type, ty2: Type, ty3: Type, arg1: Term, arg2: Term): Term = {
    hApp(mkTypeApp(combinator_C, Seq(ty1, ty2, ty3)),Seq(arg1,arg2))
  }

  /* Lambda elimination procedure from here */
  /** entry point */
  def eliminateLambdaNew(t: Term)(holSignature: Signature): Term = {
    import leo.datastructures.Term._
    import TypedFOLEncoding.translateTerm

    t match {
      case ty :::> body =>
        if (body.isTermAbs) {
          println(s"recurse on: ${body.pretty(holSignature)}")
          val innerElim = eliminateLambdaNew(body)(holSignature)
          println(s"end-recurse on: ${innerElim.pretty(sig)}")
          eliminateLambdaNewShallow(ty, innerElim)(holSignature)
        } else eliminateLambdaNew0(ty, body)(holSignature)
      case _ => /* nolambda */ translateTerm(t, this)(holSignature, sig)
    }
  }

  /** eliminate a top-level lambda abstraction λ(absType)(absBody) where
    * it is assumed that absBody was already recursively transformed to FO. Hence,
    * terms need not to be converted again. */
  def eliminateLambdaNewShallow(absType: Type, absBody: Term)(holSignature: Signature): Term = {
    import leo.datastructures.Term._
    import leo.modules.encoding.TypedFOLEncoding.foTransformType0
    absBody match {
      case Bound(`absType`, 1) => println("[S] I")
        I(foTransformType0(absType, true)(holSignature, sig))
      case _ if !free(absBody, 1) =>
        println("[S] K")
        val translatedTy = absType //foTransformType0(absType, true)(holSignature, sig)
        val translatedBody = absBody //eliminateLambdaNew(absBody.lift(-1))(holSignature)
        K(translatedTy, translatedBody.ty, translatedBody)
      case f ∙ args =>
        val lastArg = args.last
        if (lastArg.isLeft) {
          val termLastArg = lastArg.left.get
          val allButLastArg = Term.mkApp(f, args.init)
          println(s"[S] head: " + f.pretty(sig))
          println(s"head is App: ${f == sig.hApp}")
          println("[S] allButLastArg: " + allButLastArg.pretty(sig))
          println("[S] lastArg: " + termLastArg.pretty(sig))
          if (!free(allButLastArg, 1)) {
            // eta or B
            if (termLastArg.isVariable && Bound.unapply(termLastArg).get._2 == 1) {
              println("[S] eta")
              if (f == sig.hApp) {
                assert(args.size == 4)
                args(2).left.get
              } else {
               assert(false)
                allButLastArg
              } // eliminateLambdaNew(allButLastArg)(holSignature)
            } else {
              println("[S] B")
              assert(termLastArg.ty == allButLastArg.ty._funDomainType)
              val translatedTy = absType //foTransformType0(absType, true)(holSignature, sig)
              val translatedAllButLastArgCoDomainType = allButLastArg.ty.codomainType // foTransformType0(allButLastArg.ty.codomainType, true)(holSignature, sig)
              val translatedAllButLastArgDomainType = allButLastArg.ty._funDomainType // foTransformType0(allButLastArg.ty._funDomainType, true)(holSignature, sig)
              val translatedAllButLastArg = if (f == sig.hApp) {
                assert(args.size == 4)
                args(2).left.get.lift(-1)
              } else {
                assert(false)
                allButLastArg.lift(-1)
              } // eliminateLambdaNew(allButLastArg.lift(-1))(holSignature)
              val translatedTermLastArg = eliminateLambdaNewShallow(translatedTy, termLastArg)(holSignature) // eliminateLambdaNew(λ(translatedTy)(termLastArg))(holSignature)
              B(translatedTy, translatedAllButLastArgCoDomainType, translatedAllButLastArgDomainType, translatedAllButLastArg, translatedTermLastArg)
            }
          } else if (!free(termLastArg,1)) {
            // C
            println("[S] C")
            assert(termLastArg.ty == allButLastArg.ty._funDomainType)
            val translatedTy = absType //foTransformType0(absType, true)(holSignature, sig)
            val translatedAllButLastArgCoDomainType = allButLastArg.ty.codomainType // foTransformType0(allButLastArg.ty.codomainType, true)(holSignature, sig)
            val translatedAllButLastArgDomainType = allButLastArg.ty._funDomainType //foTransformType0(allButLastArg.ty._funDomainType, true)(holSignature, sig)
            val translatedAllButLastArg = eliminateLambdaNewShallow(translatedTy,(
              if (f == sig.hApp) {
                assert(args.size == 4)
                args(2).left.get
              } else {
                assert(false)
                allButLastArg
              }))(holSignature)

            //eliminateLambdaNew(λ(translatedTy)(allButLastArg))(holSignature)
            val translatedTermLastArg = termLastArg.lift(-1) // eliminateLambdaNew(termLastArg.lift(-1))(holSignature)
            C(translatedTy, translatedAllButLastArgCoDomainType, translatedAllButLastArgDomainType, translatedAllButLastArg, translatedTermLastArg)
          } else { // occurs in both free
            // S
            ???
          }
        } else {
          ???
        }
      case _ => // this would be: (1) TypeLambda, (2) Lambda.
        // Cannot happen since (1) is not in the valid term fragment
        // (2) was checked by eliminateLambda before
        throw new IllegalArgumentException
    }
  }

  def eliminateLambdaNew0(absType: Type, absBody: Term)(holSignature: Signature): Term = {
    import leo.datastructures.Term._
    import leo.modules.encoding.TypedFOLEncoding.foTransformType0
    absBody match {
      case Bound(`absType`, 1) => println("I")
        I(foTransformType0(absType, true)(holSignature, sig))
      case _ if !free(absBody, 1) =>
        println("K")
        val translatedTy = foTransformType0(absType, true)(holSignature, sig)
        val translatedBody = eliminateLambdaNew(absBody.lift(-1))(holSignature)
        K(translatedTy, translatedBody.ty, translatedBody)
      case f ∙ args =>
        val lastArg = args.last
        if (lastArg.isLeft) {
          val termLastArg = lastArg.left.get
          val allButLastArg = Term.mkApp(f, args.init)
          println(s"head: " + f.pretty(holSignature))
          println("allButLastArg: " + allButLastArg.pretty(holSignature))
          println("lastArg: " + termLastArg.pretty(holSignature))
          if (!free(allButLastArg, 1)) {
            // eta or B
            if (termLastArg.isVariable && Bound.unapply(termLastArg).get._2 == 1) {
              println("eta")
              eliminateLambdaNew(allButLastArg)(holSignature)
            } else {
              println("B")
              assert(termLastArg.ty == allButLastArg.ty._funDomainType)
              val translatedTy = foTransformType0(absType, true)(holSignature, sig)
              val translatedAllButLastArgCoDomainType = foTransformType0(allButLastArg.ty.codomainType, true)(holSignature, sig)
              val translatedAllButLastArgDomainType = foTransformType0(allButLastArg.ty._funDomainType, true)(holSignature, sig)
              val translatedAllButLastArg = eliminateLambdaNew(allButLastArg.lift(-1))(holSignature)
              val translatedTermLastArg = eliminateLambdaNew(λ(translatedTy)(termLastArg))(holSignature)
              B(translatedTy, translatedAllButLastArgCoDomainType, translatedAllButLastArgDomainType, translatedAllButLastArg, translatedTermLastArg)
            }
          } else if (!free(termLastArg,1)) {
            // C
            println(s"C")
            assert(termLastArg.ty == allButLastArg.ty._funDomainType)
            val translatedTy = foTransformType0(absType, true)(holSignature, sig)
            val translatedAllButLastArgCoDomainType = foTransformType0(allButLastArg.ty.codomainType, true)(holSignature, sig)
            val translatedAllButLastArgDomainType = foTransformType0(allButLastArg.ty._funDomainType, true)(holSignature, sig)
            val translatedAllButLastArg = eliminateLambdaNew(λ(translatedTy)(allButLastArg))(holSignature)
            val translatedTermLastArg = eliminateLambdaNew(termLastArg.lift(-1))(holSignature)
            C(translatedTy, translatedAllButLastArgCoDomainType, translatedAllButLastArgDomainType, translatedAllButLastArg, translatedTermLastArg)
          } else { // occurs in both free
            // S
            println(s"S")
            assert(termLastArg.ty == allButLastArg.ty._funDomainType)
            val translatedTy = foTransformType0(absType, true)(holSignature, sig)
            val translatedAllButLastArgCoDomainType = foTransformType0(allButLastArg.ty.codomainType, true)(holSignature, sig)
            val translatedAllButLastArgDomainType = foTransformType0(allButLastArg.ty._funDomainType, true)(holSignature, sig)
            val translatedAllButLastArg = eliminateLambdaNew(λ(translatedTy)(allButLastArg))(holSignature)
            val translatedTermLastArg = eliminateLambdaNew(λ(translatedTy)(termLastArg))(holSignature)
            S(translatedTy, translatedAllButLastArgCoDomainType, translatedAllButLastArgDomainType, translatedAllButLastArg, translatedTermLastArg)
          }
        } else {
          ???
        }
      case _ => // this would be: (1) TypeLambda, (2) Lambda.
        // Cannot happen since (1) is not in the valid term fragment
        // (2) was checked by eliminateLambda before
        throw new IllegalArgumentException
    }
  }

  @inline private final def free(t: Term, idx: Int): Boolean = t.looseBounds.contains(idx)

  override def eliminateLambda(t: Term)(holSignature: Signature): Term = eliminateLambdaNew(t)(holSignature)

  override def getAuxiliaryDefinitions: Set[Term] = {
    var result: Set[Term] = Set.empty
    for (usedSymbol <- usedSymbols) {
      result += axioms(usedSymbol)
    }
    result
  }

  private final lazy val axioms: Map[Signature#Key, Term] = {
    val X: Term = Term.mkBound(Xty,1)
    val Y: Term = Term.mkBound(Yty,2)
    val Z: Term = Term.mkBound(Zty,3)
    import TypedFOLEncodingSignature._
    Map(
      combinator_I_key -> Eq(hApp(I(Xty), X), X),
      combinator_K_key -> Eq(hApp(K(Xty, Yty, Y), X),Y),
      combinator_S_key -> ???,
      combinator_B_key -> ???,
      combinator_C_key -> ???
    )
  }
}

protected[encoding] class LambdaElim_Turner(sig: TypedFOLEncodingSignature) extends LambdaElimination(sig) {

  override def eliminateLambda(t: Term)(holSignature: Signature): Term = ???

  override def getAuxiliaryDefinitions: Set[Term] = ???
}

protected[encoding] class LambdaElim_LambdaLifting(sig: TypedFOLEncodingSignature) extends LambdaElimination(sig) {

  override def eliminateLambda(t: Term)(holSignature: Signature): Term = ???

  override def getAuxiliaryDefinitions: Set[Term] = ???
}
