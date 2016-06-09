import java.io.{File, FileInputStream, PrintStream, InputStream}
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.riot.Lang._
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.sparql.core.Quad
import scala.language.dynamics

/**
 * Converter from Monnet Lemon to W3C Ontolex Lemon
 */
object monnet2w3c {
  case class Args(
    inputFile : File = null,
    outputFile : File = null,
    inputFormat : String = null,
    outputFormat : String = null,
    baseURL : Option[String] = None)

  val RDF_LANGS = Seq(CSV, JSONLD, N3, NQ, NQUADS, NT, NTRIPLES,
    RDFJSON, RDFTHRIFT, RDFXML, TRIG, TRIX, TTL, TURTLE).map(
      x => x.getLabel().toUpperCase -> x).toMap


  def main(args : Array[String]) {
    val parser = new scopt.OptionParser[Args]("monnet2w3c") {
      head("Monnet Lemon to W3C OntoLex Lemon Converter", "1.0")

      opt[File]('i', "input") valueName("<inputFile>") action {
        (x, c) => c.copy(inputFile = x)
      } validate { 
        x => if(x.exists) { success } 
             else { failure("Input file does not exist") }
      } text("The file in Monnet Lemon")

      opt[File]('o', "output") valueName("<outputFile>") action {
        (x, c) => c.copy(outputFile = x)
      } validate { 
        x => if(!x.exists || !x.isDirectory) { success } 
             else { failure("Output file is a folder") }
      } text("The target for the output in W3C OntoLex Lemon")

      opt[String]('f', "from") valueName("<inputFormat>") action {
        (x, c) => c.copy(inputFormat = x)
      } validate {
        x => if(RDF_LANGS contains x.toUpperCase) { success } 
             else { failure("Invalid input language (%s are supported)" 
                            format RDF_LANGS.keys.mkString(", ")) }
      } text("The RDF serialization of the input")

      opt[String]('t', "to") valueName("<outputFormat>") action {
        (x, c) => c.copy(outputFormat = x)
      } validate {
        x => if(RDF_LANGS contains x.toUpperCase) { success } 
             else { failure("Invalid output language (%s are supported)" 
                            format RDF_LANGS.keys.mkString(", ")) }
      } text("The RDF serialization of the output")

      opt[String]('b', "base-url") valueName("<baseURL>") action {
        (x, c) => c.copy(baseURL = Some(x))
      } text("The Base URL to use when resolving")
    }
    parser.parse(args, Args()) match {
      case Some(config) =>
        convert(
          Option(config.inputFile).map(new FileInputStream(_)).getOrElse(System.in),
          Option(config.outputFile).map(new PrintStream(_)).getOrElse(System.out),
          Option(config.inputFormat).map(RDF_LANGS).getOrElse(TURTLE),
          Option(config.outputFormat).map(RDF_LANGS).getOrElse(NTRIPLES),
          config.baseURL
        )
      case None =>
        System.exit(-1)
    }
  }

  class Namespace(val prefix : String) extends Dynamic {
    def selectDynamic(name : String) = this + name
    def +(name : String) = 
      NodeFactory.createURI(prefix + name)
  }

  val lemon1 = new Namespace("http://www.monnet-project.eu/lemon#")
  val lemon2 = new Namespace("http://lemon-model.net/lemon#")
  val ontolex = new Namespace("http://www.w3.org/ns/lemon/ontolex#")
  val synsem = new Namespace("http://www.w3.org/ns/lemon/synsem#")
  val decomp = new Namespace("http://www.w3.org/ns/lemon/decomp#")
  val vartrans = new Namespace("http://www.w3.org/ns/lemon/vartrans#")
  val lime = new Namespace("http://www.w3.org/ns/lemon/lime#")
  val skos = new Namespace("http://www.w3.org/2004/02/skos/core#")
  val liam = new Namespace("http://lemon-model.net/liam#")
  val dct = new Namespace("http://purl.org/dc/terms/")
  val rdf = new Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#")

  class Converter(out : PrintStream, lang : Lang) extends StreamRDF {
    val model = if(lang != NTRIPLES) {
      org.apache.jena.sparql.core.DatasetGraphFactory.create()
    } else {
      null
    }

    def toN3(node : Node) = {
      if(node.isURI()) {
        "<%s>" format node.getURI()
      } else if(node.isBlank()) {
        "_:%s" format node.getBlankNodeLabel()
      } else if(node.getLiteralLanguage() != null) {
        "\"%s\"@%s" format (node.getLiteralLexicalForm().
                              replaceAll("\"","\\\\\"").
                              replaceAll("\n","\\\\n").
                              replaceAll("\r","\\\\r"),
                            node.getLiteralLanguage())
      } else if(node.getLiteralDatatypeURI() != null) {
        "\"%s\"^^%s" format (node.getLiteralLexicalForm().
                              replaceAll("\"","\\\\\"").
                              replaceAll("\n","\\\\n").
                              replaceAll("\r","\\\\r"),
                             node.getLiteralDatatypeURI())
      } else {
        "\"%s\"" format (node.getLiteralLexicalForm().
                              replaceAll("\"","\\\\\"").
                              replaceAll("\n","\\\\n").
                              replaceAll("\r","\\\\r"))
      }
    }

    def emit(graph : Node, triple : Triple) { 
      if(model == null) {
        out.println("%s %s %s ." format (
          toN3(triple.getSubject()),
          toN3(triple.getPredicate()),
          toN3(triple.getObject())))
      } else {
        model.add(new Quad(graph, triple))
      }
    }

    def base(base : String) {} 
    def start() {}
    def finish() {
      if(model != null) {
        RDFDataMgr.write(out, model, lang)
      }
      try {
        out.flush
        out.close
      } catch {
        case x : Exception =>
      }
    }

    def prefix(prefix : String, iri : String) {}
    def triple(triple : Triple) {
      process(null, triple)
    }
    def quad(quad : Quad) {
      process(quad.getGraph(), quad.asTriple)
    }

    def toLemon(node : Node) : Option[String] = {
      if(node.isURI()) {
        if(node.getURI().startsWith(lemon1.prefix)) {
          Some(node.getURI().drop(lemon1.prefix.length))
        } else if(node.getURI().startsWith(lemon2.prefix)) {
          Some(node.getURI().drop(lemon2.prefix.length))
        } else {
          None
        }
      } else {
        None
      }
    }


    def remapProperties(triple : Triple, maps : Map[String, Node],
      warnmsg : Option[String] = None) = {
      toLemon(triple.getPredicate()) match {
        case Some(p) =>
          if(maps contains p) {
            warnmsg match {
              case Some(m) =>
                warn(triple, m)
              case _ =>
            }
            new Triple(triple.getSubject(), maps(p), triple.getObject())
          } else {
            triple
          }
        case None =>
          triple
      }
    }
    
    def remapObjects(triple : Triple, maps : Map[String, Node]) = {
      toLemon(triple.getObject()) match {
        case Some(o) =>
          if(maps contains o) {
            new Triple(triple.getSubject(), triple.getPredicate(), maps(o))
          } else {
            triple
          }
        case None =>
          triple
      }
    }

    val propMaps = Map(
      //<http://lemon-model.net/lemon#entry>
      "entry" -> lime.entry,
      //<http://lemon-model.net/lemon#lexicalForm>
      "lexicalForm" -> ontolex.lexicalForm,
      //<http://lemon-model.net/lemon#canonicalForm>
      "canonicalForm" -> ontolex.canonicalForm,
      //<http://lemon-model.net/lemon#otherForm>
      "otherForm" -> ontolex.otherForm,
      //<http://lemon-model.net/lemon#language>
      "language" -> lime.language,
      //<http://lemon-model.net/lemon#writtenRep>
      "writtenRep" -> ontolex.writtenRep,
      //<http://lemon-model.net/lemon#synBehavior>
      "synBehavior" -> synsem.synBehavior,
      //<http://lemon-model.net/lemon#synArg>
      "synArg" -> synsem.synArg,
      //<http://lemon-model.net/lemon#subsense>
      "subsense" -> synsem.submap,
      //<http://lemon-model.net/lemon#subjOfProp>
      "subjOfProp" -> synsem.subjOfProp,
      //<http://lemon-model.net/lemon#senseRelation>
      "senseRelation" -> vartrans.senseRel,
      //<http://lemon-model.net/lemon#sense>
      "sense" -> ontolex.sense,
      //<http://lemon-model.net/lemon#semArg>
      "semArg" -> ontolex.ontoCorrespondence,
      //<http://lemon-model.net/lemon#representation>
      "representation" -> ontolex.representation,
      //<http://lemon-model.net/lemon#reference>
      "reference" -> ontolex.reference,
      //<http://lemon-model.net/lemon#propertyRange>
      "propertyRange" -> synsem.propertyRange,
      //<http://lemon-model.net/lemon#propertyDomain>
      "propertyDomain" -> synsem.propertyDomain,
      //<http://lemon-model.net/lemon#optional>
      "optional" -> synsem.optional,
      //<http://lemon-model.net/lemon#objOfProp>
      "objOfProp" -> synsem.objOfProp,
      //<http://lemon-model.net/lemon#marker>
      "marker" -> synsem.marker,
      //<http://lemon-model.net/lemon#isSenseOf>
      "isSenseOf" -> ontolex.isSenseOf,
      //<http://lemon-model.net/lemon#isReferenceOf>
      "isReferenceOf" -> ontolex.isReferenceOf,  
      //<http://lemon-model.net/lemon#isA>
      "isA" -> synsem.isA,
      //<http://lemon-model.net/lemon#condition>
      "condition" -> synsem.condition,
      //<http://lemon-model.net/lemon#context>
      "context" -> ontolex.usage,
      //<http://lemon-model.net/lemon#edge>
      "edge" -> decomp.constituent,
      //<http://lemon-model.net/lemon#leaf>
      "leaf" -> decomp.constituent,
      //<http://lemon-model.net/lemon#element>
      "element" -> decomp.correspondsTo,
      //<http://lemon-model.net/lemon#generates>
      "generates" -> liam.generates,
      //<http://lemon-model.net/lemon#lexicalVariant>
      "lexicalVariant" -> vartrans.lexicalRel,
      //<http://lemon-model.net/lemon#nextTransform>
      "nextTransform" -> liam.nextTransform,
      //<http://lemon-model.net/lemon#pattern>
      "pattern" -> liam.pattern,
      //<http://lemon-model.net/lemon#rule>
      "rule" -> liam.rule,
      //<http://lemon-model.net/lemon#transform>
      "transform" -> liam.transform,
      //<http://lemon-model.net/lemon#value>
      "value" -> rdf.value
    )

    val skosProps = Map(
      //<http://lemon-model.net/lemon#definition>
      "definition" -> skos.definition,
      //<http://lemon-model.net/lemon#broader>
      "broader" -> skos.broader,
      //<http://lemon-model.net/lemon#equivalent>
      "equivalent" -> skos.exactMatch,
      //<http://lemon-model.net/lemon#example>
      "example" -> skos.example,
      //<http://lemon-model.net/lemon#narrower>
      "narrower" -> skos.narrower,
      //<http://lemon-model.net/lemon#topic>
      "topic" -> dct.topic
    )

    val objMaps = propMaps ++ skosProps ++ Map(
      //<http://lemon-model.net/lemon#Lexicon>
      "Lexicon" -> lime.Lexicon,
      //<http://lemon-model.net/lemon#LexicalEntry>
      "LexicalEntry" -> ontolex.LexicalEntry,
      //<http://lemon-model.net/lemon#Word>
      "Word"-> ontolex.Word,
      //<http://lemon-model.net/lemon#Part>
      "Part" -> ontolex.Affix,
      //<http://lemon-model.net/lemon#Phrase>
      "Phrase" -> ontolex.MultiwordExpression,
      //<http://lemon-model.net/lemon#Form>
      "Form" -> ontolex.Form,
      //<http://lemon-model.net/lemon#LexicalSense>
      "LexicalSense" -> ontolex.LexicalSense,
      //<http://lemon-model.net/lemon#Argument>
      "Argument" -> synsem.SyntacticArgument,
      //<http://lemon-model.net/lemon#Component>
      "Component" -> decomp.Component,
      //<http://lemon-model.net/lemon#Node>
      "Node" -> decomp.Component,
      //<http://lemon-model.net/lemon#Frame>
      "Frame" -> synsem.SyntacticFrame,
      //<http://lemon-model.net/lemon#MorphPattern>
      "MorphPattern" -> liam.MorphPattern,
      //<http://lemon-model.net/lemon#MorphTransform>
      "MorphTransform" -> liam.MorphTransform,
      //<http://lemon-model.net/lemon#Prototype>
      "Prototype" -> liam.Prototype
      )

    val unmapped = Map(
      //<http://lemon-model.net/lemon#altRef>
      "altRef" -> "OntoLex Lemon does not support ranking references, consider a usage note on the sense instead",
      //<http://lemon-model.net/lemon#prefRef>
      "prefRef" -> "OntoLex Lemon does not support ranking references, consider a usage note on the sense instead",
      //<http://lemon-model.net/lemon#hiddenRef>
      "hiddenRef" -> "OntoLex Lemon does not support ranking references, consider a usage note on the sense instead",
      //<http://lemon-model.net/lemon#constituent>
      "constituent" -> "Phrase types should be indicated with an appropriate vocabulary. Do not confuse lemon:constituent with ontolex:constituent, they are not compatible!",
      //<http://lemon-model.net/lemon#extrinsicArg>
      "extrinsicArg" -> "Extrinsic arguments are not suppoted by OntoLex Lemon",
      //<http://lemon-model.net/lemon#HasLanguage>
      "HasLanguage" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#HasPattern>
      "HasPattern" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#LemonElement>
      "LemonElement" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#PhraseElement>
      "PhraseElement" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#NodeConstituent>
      "NodeConstituent" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#SynRoleMarker>
      "SynRoleMarker" -> "Technical type ignored",
      //<http://lemon-model.net/lemon#LexicalCondition>
      "LemonCondition" -> "No class for conditions in OntoLex Lemon",
      //<http://lemon-model.net/lemon#LexicalContext>
      "LemonContext" -> "No class for usages in OntoLex Lemon",
      //<http://lemon-model.net/lemon#SenseCondition>
      "SenseCondition" -> "No class for conditions in OntoLex Lemon",
      //<http://lemon-model.net/lemon#SenseContext>
      "SenseContext" -> "No class for contexts in OntoLex Lemon",
      //<http://lemon-model.net/lemon#SenseDefinition>
      "SenseDefinition" -> "No class for definitions in OntoLex Lemon",
      //<http://lemon-model.net/lemon#UsageExample>
      "UsageExample" -> "No class for examples in OntoLex Lemon",
      //<http://lemon-model.net/lemon#LexicalTopic>
      "LexicalTopic" -> "Ignoring lexical topic",
      //<http://lemon-model.net/lemon#incompatible>
      "incompatible" -> "Senses cannot be considered incompatible in OntoLex Lemon",
      //<http://lemon-model.net/lemon#property>
      "property" -> "OntoLex Lemon has no class for lexico-syntactic property values",
      //<http://lemon-model.net/lemon#separator>
      "separator" -> "Separator not supported by OntoLex Lemon",
      //<http://lemon-model.net/lemon#formVariant>
      "formVariant" -> "OntoLex Lemon does not support form variants"
    )

    def warn(triple : Triple, message : String) = {
      System.err.println(triple)
      System.err.println(message)
    }

    def checkAbstractForm(triple : Triple) =
      if(toLemon(triple.getPredicate()) == Some("abstractForm")) {
        warn(triple, "Raising abstract from to other form")
        new Triple(triple.getSubject(), ontolex.otherForm, triple.getObject())
      } else {
        triple
      }

    def checkUnmapped(triple : Triple) = {
      toLemon(triple.getPredicate()) match {
        case Some(l) if unmapped contains l =>
          warn(triple, unmapped(l))
        case _ =>
      }
      toLemon(triple.getObject()) match {
        case Some(l) if unmapped contains l =>
          warn(triple, unmapped(l))
        case _ =>
      }
    }

    def toCorrespondsTo(triple : Triple) = {
      toLemon(triple.getPredicate()) match {
        case Some("phraseRoot") =>
          new Triple(triple.getObject(), synsem.correspondsTo, triple.getSubject())
        case Some("tree") => 
          new Triple(triple.getObject(), synsem.correspondsTo, triple.getSubject())
        case _ =>
          triple
      }
    }

    case class ComponentList(
      graph : Node,
      source : Node,
      head : Node)

    case class ComponentElement(
      node : Node,
      next : Node,
      value : Node)

    val componentLists = collection.mutable.Map[Node, ComponentList]()
    val elements = collection.mutable.Map[Node, ComponentElement]()

    private def verifyCL(cl : ComponentList) = {
      def verifyChain(ce : ComponentElement, depth : Int) : Boolean = {
        // There could be loops in the input list so let's make sure we can stop
        if(depth < 0 || ce.next == null || ce.value == null) {
          false
        } else if(ce.next == rdf.nil) {
          true
        } else {
          elements.get(ce.next) match {
            case Some(ce) =>
              verifyChain(ce, depth - 1)
            case None =>
              false
          }
        }
      }
      elements.get(cl.head) match {
        case Some(ce) =>
          verifyChain(ce, 1000)
        case None =>
          false
      }
    }

    def emitCL(cl : ComponentList) = {
      componentLists.remove(cl.source)
      def emitChain(ce : ComponentElement, idx : Int) {
        emit(cl.graph, new Triple(cl.source, rdf + ("_" + idx), ce.value))
        emit(cl.graph, new Triple(cl.source, ontolex.constituent, ce.value))
        elements.remove(ce.node)
        if(ce.next != rdf.nil) {
          emitChain(elements(ce.next), idx + 1)
        }
      }
      emitChain(elements(cl.head), 1)
    }

    def findCL(cl : ComponentElement) : Option[ComponentList] = {
      componentLists.values.find(_.head == cl.node) match {
        case Some(cl) =>
          Some(cl)
        case None =>
          elements.values.find(_.next == cl.node) match {
            case Some(ce) =>
              findCL(ce)
            case None =>
              None
          }
      }
    }


    def checkInDecomp(graph : Node, triple : Triple) = {
      if(toLemon(triple.getPredicate()) == Some("decomposition")) {
        val cl = ComponentList(graph, triple.getSubject(), triple.getObject())
        if(verifyCL(cl)) {
          emitCL(cl)
        } else {
          componentLists(triple.getObject()) = cl
        }
        false
      } else if(triple.getPredicate() == rdf.first) {
        elements.get(triple.getSubject()) match {
          case None =>
            elements(triple.getSubject()) = ComponentElement(triple.getSubject(),
              null, triple.getObject())
          case Some(ComponentElement(n, next, _)) =>
            val ce = ComponentElement(n, next, triple.getObject())
            findCL(ce) match {
              case Some(cl) =>
                if(verifyCL(cl)) {
                  emitCL(cl)
                } else {
                  elements(n) = ce
                }
              case None =>
                elements(n) = ce
            }
        }
        false
      } else if(triple.getPredicate() == rdf.rest) {
        elements.get(triple.getSubject()) match {
          case None =>
            elements(triple.getSubject()) = ComponentElement(triple.getSubject(),
              triple.getObject(), null)
          case Some(ComponentElement(n, _, value)) =>
            val ce = ComponentElement(n, triple.getObject(), value)
            findCL(ce) match {
              case Some(cl) =>
                if(verifyCL(cl)) {
                  emitCL(cl)
                } else {
                  elements(n) = ce
                }
              case None =>
                elements(n) = ce
            }
        }
        false
      } else {
        true
      }
    }

    def process(graph : Node, triple : Triple) {
      // First the properties that are just remapped
      val t1 = remapObjects(
        remapProperties(
          remapProperties(triple, propMaps), 
          skosProps, Some("Semantic relations such as broader have been mapped to " +
          "SKOS. You should considering using a LexicalConcept rather than placing " +
          "semantic relations on a sense")),
      objMaps)

      // The abstract form is now included in other form
      //<http://lemon-model.net/lemon#abstractForm>
      val t2 = checkAbstractForm(t1)

      // Many values are not possible to map and these are just left in the old 
      // namespace
      checkUnmapped(t2)
      
      // The phraseRoot and tree property now have the reverse polarity
      //<http://lemon-model.net/lemon#phraseRoot>
      //<http://lemon-model.net/lemon#tree>
      val t3 = toCorrespondsTo(t2)

      // Component lists work very differently
      //<http://lemon-model.net/lemon#ComponentList>
      //<http://lemon-model.net/lemon#decomposition>
      if(checkInDecomp(graph, triple)) {
        // We emit the value unless it is part of a decomposition
        emit(graph, t1)
      }
    }

  }
  
  def convert(
    input : InputStream,
    output : PrintStream,
    inputLang : Lang, outputLang : Lang,
    baseURL : Option[String]) {
      baseURL match {
        case Some(b) =>
          RDFDataMgr.parse(new Converter(output, outputLang), input,
            b, inputLang)
        case None =>
          RDFDataMgr.parse(new Converter(output, outputLang), input,
            inputLang)
      }

  }
}
