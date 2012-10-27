package at.logic.skeptik.parser

import scala.util.parsing.combinator._
import collection.mutable.{HashMap => MMap}
import java.io.FileReader
import at.logic.skeptik.proof._
import at.logic.skeptik.proof.sequent._
import at.logic.skeptik.proof.sequent.lk._
import at.logic.skeptik.expression.formula._
import at.logic.skeptik.expression._
import at.logic.skeptik.judgment.immutable.{SeqSequent => Sequent}

class SMT2Parser(filename: String)
extends JavaTokenParsers with RegexParsers {
  
  private val proofMap = new MMap[String,SequentProofNode]
  private val exprMap  = new MMap[String,E]

  def proof: Parser[List[SequentProofNode]] = rep(line)
  def line: Parser[SequentProofNode] = "(set"  ~> name ~ "(" ~ inference <~ "))" ^^ {
    case ~(~(n,_),p) => proofMap += (n -> p); p
    case x => throw new Exception("Wrong line " + x)
  }

  def inference: Parser[SequentProofNode] = (resolution | input)
  def resolution: Parser[SequentProofNode] = "resolution" ~> clauses <~ conclusion ^^ {
    list => list.tail.foldLeft(list.head) { (left,right) => CutIC(left,right) }
  }
  def input: Parser[SequentProofNode] = name ~> opt(clauses) ~> conclusion ^^ {
    list => new Axiom(Sequent(list))
  }

  def clauses: Parser[List[SequentProofNode]] = ":clauses (" ~> rep(name) <~ ")" ^^ {
    list => list.map(proofMap)
  }
  def conclusion: Parser[List[E]] = ":conclusion (" ~> rep(expression) <~ ")"

  def expression: Parser[E] = (assignmentE | simpleE)
  def assignmentE: Parser[E] = name ~ ":" ~ simpleE ^^ {
    case ~(~(n,_),e) => exprMap.update(n,e) ; e
  }
  def simpleE: Parser[E] = (posE | negE | andE | orE | letE | otherE)
  def posE: Parser[E] = name ^^ {
    n => exprMap.getOrElse(n, Var(n,o))
  }
  def negE: Parser[E] = "(not" ~> expression <~ ")" ^^ {
    e => Neg(e)
  }
  def andE: Parser[E] = "(and" ~> rep(expression) <~ ")" ^^ {
    list => list.tail.foldLeft(list.head) { (left,right) => And(left,right) }
  }
  def orE: Parser[E] = "(or" ~> rep(expression) <~ ")" ^^ {
    list => list.tail.foldLeft(list.head) { (left,right) => Or(left,right) }
  }
  def otherE: Parser[E] = "(" ~> otherOther ~ rep(otherOther) <~ ")" ^^ {
    case ~(op,l) => Var(l.foldLeft(op) { (left,right) => left + right }, o)
  }
  def otherOther: Parser[String] = ( expression ^^ (_.toString) | name )

  def letE: Parser[E] = "(let (" ~> rep(letAssignment) ~> ")" ~> expression <~ ")"
  def letAssignment: Parser[Unit] = "(" ~> name ~ expression <~ ")" ^^ {
    case ~(n,e) => exprMap.update(n,e)
  }

  def name: Parser[String] = """[^ ():]+""".r

  def getProofNode = {
    parse(proof, new FileReader(filename)) match {
      case Success(Nil,in) => throw new Exception(exprMap.keys.toString + " at " + in.pos + " " + in.pos.longString)
      case Success(list,_) => Proof(list.last) // returns proof whose root is in the last line of the proof file
      case Failure(message,_) => throw new Exception(message)
      case Error(message,_) => throw new Exception(message)
    }
  }
}
