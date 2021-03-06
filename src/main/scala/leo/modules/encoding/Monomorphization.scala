package leo.modules.encoding

import leo.datastructures.{Clause, Literal, Signature, Subst, Term, Type, partitionArgs}
import leo.modules.calculus.{TypeUnification, TypeSubst}

/**
  * Translates polymorphic problems into
  * monomorphic problems by heuristic type variable instantiation.
  *
  * @author Alexander Steen <a.steen@fu-berlin.de>
  * @since March 2017
  */
object Monomorphization {
  import scala.collection.mutable

  type MonoResult = (Problem, Signature)
  type Instance = Seq[Type]
  type Instances = Set[Instance]
  type InstanceInfo = mutable.Map[Signature.Key, Instances]
  type PolySymbols = InstanceInfo

  /**
    * Given a (possibly) polymorphic problem `problem`, this method returns
    * an monomorphic problem `p'` which contains all monomorphic formulae
    * of `problem` as well as heuristically instantiated (monomorphic)
    * formulae that occurred polymorphically in `problem`. Also,
    * a fresh signature is returned under which the new problem is
    * stated.
    *
    *
    * The algorithm works as follows:
    * {{{
    *   monoFormulae := monoFormulae(problem) // Set(Clauses)
    *   freshMonoinstances := monoInstances(monoFormulae) // Map(id -> Set(instances))
    *   PA := polyAxioms(problem) // Set(Clauses)
    *   monoinstances := ∅ // Set(Clauses)
    *   WHILE (freshMonoInstances != emptyMap AND iterationLimit not reached) DO
    *     newMonoInstances := ∅ // Set(clauses)
    *     FOR pa ∈ PA DO
    *       PS := collectPolySymbols(pa) // Map(id -> Set(polyparams))
    *       newSubsts := ∅ // Set(substitution)
    *       FOR ps ∈ PS
    *         IF head(ps) ∈ freshMonoInstances AND head(ps) not blacklisted
    *           instances := freshMonoInstances(head(ps)) // Set(Instances)
    *           polyParams := PS(head(ps)) // Set(polyparams)
    *           FOR pP ∈ polyParams DO
    *             FOR i ∈ instances DO
    *               σ := unify(pP, i)
    *               IF σ is defined
    *                 newSubsts := multiply(newSubsts, σ)
    *               ENDIF
    *             DONE
    *           DONE
    *         ENDIF
    *       DONE
    *       newMonoInstances := newMonoInstances ∪ substituteAll(pa, newSubsts).filter(tyGround)
    *     DONE
    *     freshMonoInstances := monoInstances(newMonoInstances) `mapDifference` freshMonoInstances
    *     monoInstances := monoInstances ∪ newMonoInstances
    *   DONE
    *   RETURN monoFormulae ∪ monoInstances
    * }}}
    *
    * @param problem  The polymorphic input problem
    * @param sig The signature of the input problem
    * @return A tuple `(p', s)` where `p'` is a heuristically monomorpized version of `problem`
    *         and `s` a signature under which it is stated.
    */
  final def apply(problem: Problem)(implicit sig: Signature): (Problem, Signature) = {

    val newSig: Signature = Signature.freshWithHOL() // Even if problem is not formulated in HOL
    // we dont care: Since only non-fixed constants will be printed out and
    // ids are re-calculated anyway in apply0(term)
    val instanceInfo: InstanceInfo = mutable.Map()
    val (monomorphicTranslatedFormulas, strippedPolymorphicFormulas) = formulaSplit(problem, newSig, instanceInfo)(sig)
    leo.Out.finest(s"[Monomorphization]: Mono formulas:\n\t${monomorphicTranslatedFormulas.map(_.pretty(newSig)).mkString("\n\t")}")
    leo.Out.finest(s"[Monomorphization]: Poly formulas:\n\t${strippedPolymorphicFormulas.map(_.pretty(sig)).mkString("\n\t")}")
    val monomorphizedFormulas = generateMonoAxioms(strippedPolymorphicFormulas, instanceInfo, newSig)(sig)
    leo.Out.finest(s"monomorphization result: ${monomorphizedFormulas.map(_.pretty(newSig)).mkString("\n\t")}")
    val monoProblem = monomorphicTranslatedFormulas union monomorphizedFormulas
    (monoProblem, newSig)
  }

  private final def formulaSplit(problem: Problem, monoSig: Signature, instanceInfo: InstanceInfo)
                                (polySig: Signature): (Problem, Problem) = {
    val clsIt = problem.iterator
    val tyForall = polySig("!>").key
    var monomorphicTranslatedFormulas: Problem = Set.empty
    var polymorphicFormulas: Problem = Set.empty
    while (clsIt.hasNext) {
      val cl = clsIt.next()
      val (isPoly, strippedOrNull) = polymorphic(cl, tyForall)
      if (isPoly) {
        assert(strippedOrNull != null)
        polymorphicFormulas += strippedOrNull
      }
      else monomorphicTranslatedFormulas += apply0(cl, monoSig, instanceInfo)(polySig)
    }
    (monomorphicTranslatedFormulas, polymorphicFormulas)
  }

  private final def polymorphic(cl: Clause, tyForallAtom: Signature.Key): (Boolean, Clause) = {
    import leo.datastructures.Term.{Symbol, ∙}
    if (cl.typeVars.nonEmpty) {
      (true, cl)
    }
    else {
      val litsIt = cl.lits.iterator
      var collectedLits: Seq[Literal] = Seq.empty
      while (litsIt.hasNext) {
        val lit = litsIt.next()
        if (!lit.equational) { //necessary condition for tyForall at head
          val left = lit.left
          left match {
            case Symbol(`tyForallAtom`) ∙ _ =>
              leo.Out.finest(s"formula starts with !> .... ")
              val strippedLit: Literal = Literal.mkLit(stripTyQuantifiers(tyForallAtom)(lit.left), lit.polarity)
              leo.Out.finest(s"litBefore: ${lit.pretty}")
              leo.Out.finest(s"strippedLit: ${strippedLit.pretty}")
              return (true, Clause((collectedLits :+ strippedLit) ++ litsIt.toSeq)) // we dont need to translate the rest, i guess
            case _ => collectedLits = collectedLits :+ lit
          }
        } else {
          collectedLits = collectedLits :+ lit
        }
      }
      (false, null)
    }
  }
  private final def stripTyQuantifiers(tyForallAtom: Signature.Key)(term: Term): Term = {
    import leo.datastructures.Term.{TypeLambda, Symbol, TermApp}
    term match {
      case TermApp(Symbol(`tyForallAtom`), Seq(TypeLambda(body))) => stripTyQuantifiers(tyForallAtom)(body)
      case _ => term
    }
  }


  private final val iterationLimit: Int = 2
  private final val maxSize: Int = 500
  private final def generateMonoAxioms(polyAxioms: Set[Clause], instanceInfo: InstanceInfo, newSig: Signature)(sig: Signature): Problem = {
    var monoInstances: Set[Clause] = Set.empty
    var freshMonoInstanceInfo: InstanceInfo = instanceInfo
    var curIteration: Int = 0
    while (freshMonoInstanceInfo.nonEmpty && curIteration < iterationLimit && monoInstances.size < maxSize) {
      var newMonoInstances: Set[Clause] = Set.empty
      val polyAxiomsIt = polyAxioms.iterator
      val freshestMonoInstanceInfo: InstanceInfo = mutable.Map.empty
      while (polyAxiomsIt.hasNext) {
        val polyAxiom = polyAxiomsIt.next()
        val polySymbs = polySymbols(polyAxiom, instanceInfo) // side-effect: update instanceInfo
        var newSubsts: Set[TypeSubst] = Set(Subst.id)
        val polySymbsIt = polySymbs.iterator
        while (polySymbsIt.hasNext && monoInstances.size < maxSize) {
          val (polySymbHead, polySymbTypeParams) = polySymbsIt.next()
          if (freshMonoInstanceInfo.contains(polySymbHead) && !blacklisted(polySymbHead, sig)) {
            val instances = freshMonoInstanceInfo(polySymbHead)
            val polySymbTypeParamsIt = polySymbTypeParams.iterator
            while (polySymbTypeParamsIt.hasNext && monoInstances.size < maxSize) {
              val polySymbTypeParam = polySymbTypeParamsIt.next()
              val instancesIt = instances.iterator
              while (instancesIt.hasNext && monoInstances.size < maxSize) {
                val i = instancesIt.next()
                assert(polySymbTypeParam.size == i.size)
                val zippedUniTask = polySymbTypeParam.zip(i)
                val uniResult = TypeUnification(zippedUniTask)
                if (uniResult.isDefined) {
                  newSubsts = multiply(newSubsts.take(maxSize), uniResult.get)
                }
              }
            }
          }
        }
        val preNewMonoInstances = newSubsts.map(polyAxiom.substitute(Subst.id, _)).filter(_.typeVars.isEmpty)
        newMonoInstances = newMonoInstances union preNewMonoInstances.map(apply0(_, newSig, freshestMonoInstanceInfo)(sig))
      }
      freshMonoInstanceInfo = mapDifference(freshestMonoInstanceInfo, freshMonoInstanceInfo)
      monoInstances = monoInstances union newMonoInstances
      curIteration += 1
    }
    monoInstances
  }

  private final val blackListedConsts: Seq[String] = Seq(safeName(TypedFOLEncodingSignature.hApp_name))
  private final def blacklisted(symb: Signature.Key, sig: Signature): Boolean = {
    val meta = sig(symb)
    if (meta.isFixedSymbol) true
    else {
      val name = meta.name
      blackListedConsts.contains(name)
    }
  }

  private final def mapDifference(subtractFrom: InstanceInfo, subtract: InstanceInfo): InstanceInfo = {
    val newMap: InstanceInfo = mutable.Map.empty
    val subtractFromIt = subtractFrom.iterator
    while(subtractFromIt.hasNext) {
      val (key,entry) = subtractFromIt.next()
      if (subtract.contains(key)) {
        val otherEntry = subtract(key)
        val newEntry = entry diff otherEntry
        if (newEntry.nonEmpty) newMap.+=((key, newEntry))
      } else newMap.+=((key, entry))
    }
    newMap
  }

  private final def polySymbols(polyAxiom: Clause, instanceInfo: InstanceInfo): PolySymbols = {
    val polySymbolTable: InstanceInfo = mutable.Map()
    val litsIt = polyAxiom.lits.iterator
    while(litsIt.hasNext) {
      val lit = litsIt.next()
      polySymbols(lit, instanceInfo, polySymbolTable)
    }
    polySymbolTable
  }
  private final def polySymbols(lit: Literal, instanceInfo: InstanceInfo, polySymbolTable: PolySymbols): Unit = {
    if (!lit.equational) polySymbols(lit.left, instanceInfo, polySymbolTable)
    else {
      polySymbols(lit.left, instanceInfo, polySymbolTable)
      polySymbols(lit.right, instanceInfo, polySymbolTable)
    }
  }

  private final def polySymbols(term: Term, instanceInfo: InstanceInfo, polySymbolTable: PolySymbols): Unit = {
    import leo.datastructures.Term.{∙,:::>, Symbol, TypeLambda}
    term match {
      case f ∙ args => f match {
        case Symbol(id) if f.ty.isPolyType =>
          val (typeArgs, termArgs) = partitionArgs(args)
          if (typeArgs.exists(_.typeVars.nonEmpty)) {
            // polySymbol
            updateInstanceInfo(polySymbolTable, id, typeArgs)
          } else {
            // update instance info
            updateInstanceInfo(instanceInfo, id, typeArgs)
          }
          val termArgsIt = termArgs.iterator
          while (termArgsIt.hasNext) {
            val termArg = termArgsIt.next()
            polySymbols(termArg, instanceInfo, polySymbolTable)
          }
        case _ =>
          val (_, termArgs) = partitionArgs(args) // tyargs should be empty
          val termArgsIt = termArgs.iterator
          while (termArgsIt.hasNext) {
            val termArg = termArgsIt.next()
            polySymbols(termArg, instanceInfo, polySymbolTable)
          }
      }
      case _ :::> body => polySymbols(body, instanceInfo, polySymbolTable)
      case TypeLambda(body) => assert(false)
    }
  }

  private final def multiply(set: Set[TypeSubst], subst: TypeSubst): Set[TypeSubst] = {
    var result: Set[TypeSubst] = Set.empty
    val setIt = set.iterator
    while (setIt.hasNext) {
      val subst1 = setIt.next()
      result = result + subst1.comp(subst).normalize + subst.comp(subst1).normalize
    }
    result
  }

  private final def apply0(cl: Clause, newSig: Signature, instanceInfo: InstanceInfo)(sig: Signature): Clause = {
    leo.Out.finest(s"[Monomorphization] Translate clause (tyvars: ${cl.typeVars.toString()}) ${cl.pretty(sig)}")
    Clause(cl.lits.map(apply0(_, newSig, instanceInfo)(sig)))
  }

  private final def apply0(lit: Literal, newSig: Signature, instanceInfo: InstanceInfo)(sig: Signature): Literal = {
    leo.Out.finest(s"[Monomorphization] Translate literal ${lit.pretty(sig)}")
    if (lit.equational) {
      val newLeft = apply0(lit.left, newSig, instanceInfo)(sig)
      val newRight = apply0(lit.right, newSig, instanceInfo)(sig)
      Literal.mkLit(newLeft, newRight, lit.polarity)
    } else {
      Literal.mkLit(apply0(lit.left, newSig, instanceInfo)(sig), lit.polarity)
    }
  }

  private final def apply0(t: Term, newSig: Signature, instanceInfo: InstanceInfo)(implicit sig: Signature): Term = {
    import Term.local._
    import Term.{Symbol, ∙, Bound, :::>, Integer, Rational, Real}
    leo.Out.finest(s"[Monomorphization] Translate term ${t.pretty(sig)}")
    t match {
      case f ∙ args => f match {
        case Symbol(id) => if (f.ty.isPolyType) {
          val (tyArgs, termArgs) = partitionArgs(args)
          if (sig(id).isFixedSymbol) {
            val name = sig(id).name
            val newF = mkAtom(newSig(name).key)(newSig)
            val newArgs = termArgs.map(arg => apply0(arg, newSig, instanceInfo))
            val newFTyApplied = mkTypeApp(newF, tyArgs.map(ty => convertType(ty, sig, newSig)))
            mkTermApp(newFTyApplied, newArgs)
          } else {
            val monoType = f.ty.instantiate(tyArgs)
            val name = monoInstanceName(id, tyArgs)(sig)
            updateInstanceInfo(instanceInfo, id, tyArgs)
            val newF = if (newSig.exists(name)) mkAtom(newSig(name).key)(newSig)
            else mkAtom(newSig.addUninterpreted(name, convertType(monoType, sig, newSig)))(newSig)
            val newArgs = termArgs.map(arg => apply0(arg, newSig, instanceInfo))
            mkTermApp(newF, newArgs)
          }

        } else {
          assert(args.forall(_.isLeft), s"not all arguments of ${f.pretty(sig)} (type: ${f.ty.pretty(sig)}) terms in: ${t.pretty(sig)}")
          val name = escape(sig(id).name)
          val newF = if (newSig.exists(name)) mkAtom(newSig(name).key)(newSig)
          else mkAtom(newSig.addUninterpreted(name, convertType(sig(id)._ty, sig, newSig)))(newSig)
          val newArgs = args.map(arg => apply0(arg.left.get, newSig, instanceInfo))
          mkTermApp(newF, newArgs)
        }
        case Bound(ty,idx) => // bound head cannot have poly type
          assert(args.forall(_.isLeft), s"not all arguments of ${f.pretty(sig)} (type: ${f.ty.pretty(sig)}) terms in: ${t.pretty(sig)}")
          val newF = mkBound(convertType(ty, sig, newSig), idx)
          val newArgs = args.map(arg => apply0(arg.left.get, newSig, instanceInfo))
          mkTermApp(newF, newArgs)
        case Integer(_) | Rational(_, _) | Real(_, _, _) => f
        case _ => throw new IllegalArgumentException("apply0 something")
      }
      case ty :::> body =>
        val convertedType = convertType(ty, sig, newSig)
        λ(convertedType)(apply0(body, newSig, instanceInfo)(sig))
      case _ => throw new IllegalArgumentException(s"${t.pretty(sig)} was given")
    }
  }

  private final def convertType(ty: Type, oldSig: Signature, newSig: Signature): Type = {
    import Type._
    ty match {
      case BaseType(id) =>
        val name = escape(oldSig(id).name)
        if (newSig.exists(name)) mkType(newSig(name).key)
        else mkType(newSig.addBaseType(name))
      case ComposedType(id, args) =>
        val name = monoInstanceName(id, args)(oldSig)
        if (newSig.exists(name)) mkType(newSig(name).key)
        else mkType(newSig.addBaseType(name))
      case in -> out =>
        val convertedIn = convertType(in, oldSig,newSig)
        val convertedOut = convertType(out, oldSig, newSig)
        mkFunType(convertedIn, convertedOut)
      case _ => throw new IllegalArgumentException("convertType(.)")
    }
  }

  private final def monoInstanceName(id: Signature.Key, tyArgs: Seq[Type])(sig: Signature): String = {
    val sb: StringBuffer = new StringBuffer
    sb.append(safeName(sig(id).name))
    sb.append("_")
    val tyArgsIt = tyArgs.iterator
    while (tyArgsIt.hasNext) {
      val tyArg = tyArgsIt.next()
      sb.append(canonicalTyName(tyArg)(sig))
      if (tyArgsIt.hasNext) sb.append("_")
    }
    sb.toString
  }
  private final def canonicalTyName(ty: Type)(sig: Signature): String = {
    import Type._
    ty match {
      case BaseType(id) => sig(id).name.replaceAll("\\$", "D")
      case ComposedType(id, args) => s"${sig(id).name}_${args.map(canonicalTyName(_)(sig)).mkString("_")}"
      case in -> out => s"func_${canonicalTyName(in)(sig)}_${canonicalTyName(out)(sig)}"
      case _ => throw new IllegalArgumentException(s"Spooky type: ${ty.pretty(sig)}") // bound, poly cannot happen, -> types should at this level be encoded to fun
    }
  }

  private final def updateInstanceInfo(instanceInfo: InstanceInfo, symbol: Signature.Key, instance: Instance): Unit = {
    if (instanceInfo.contains(symbol)) {
      val entry = instanceInfo(symbol)
      instanceInfo.+=(symbol -> (entry+instance))
    } else {
      instanceInfo.+=(symbol -> Set(instance))
    }
  }
}
