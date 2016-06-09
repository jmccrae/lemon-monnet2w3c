# lemon-monnet2w3c

This is a tool that converts from the [Monnet Lemon Model](http://lemon-model.net)
to the new [W3C OntoLex Lemon Model](https://www.w3.org/2016/05/ontolex/).

## Installation

To compile the JAR use the [Scala Build Tool](http://www.scala-sbt.org/) as follows

    sbt assembly

Or you can download the JAR from [my site](http://john.mccr.ae/monnet2w3c-assembly-1.0.jar).

The converter can be run with either of the following commands

    sbt run
    java -jar target/scala-2.10/monnet2w3c-assembly-1.0.jar

## Usage

  Usage: monnet2w3c [options]
  
    -i <inputFile> | --input <inputFile>
          The file in Monnet Lemon
    -o <outputFile> | --output <outputFile>
          The target for the output in W3C OntoLex Lemon
    -f <inputFormat> | --from <inputFormat>
          The RDF serialization of the input
    -t <outputFormat> | --to <outputFormat>
          The RDF serialization of the output
    -b <baseURL> | --base-url <baseURL>
          The Base URL to use when resolving

If the input file or output file are not given then STDIN and STDOUT are used.
The converter by default converts from Turtle into N-Triples.

## Notes

The following properties and classes are not supported or mapped

* `altRef`, `prefRef`, `hiddenRef`: OntoLex Lemon does not support ranking references, consider a usage note on the sense instead
* `constituent`: Phrase types should be indicated with an appropriate vocabulary. Do not confuse lemon:constituent with ontolex:constituent, they are not compatible!
* `extrinsicArg`: Extrinsic arguments are not suppoted by OntoLex Lemon
* `HasLanguage`, `HasPattern`, `LemonElement`, `PhraseElement`, `NodeConstituent`, `SynRoleMarker`,
`LemonCondition`, `LemonContext`, `SenseCondition`, `SenseContext`, `SenseDefinition`, `UsageExample`: 
These classes had a primarily technical role in Monnet Lemon that is not supported
in OntoLex Lemon
* `LexicalTopic`: Lexical topics are not in OntoLex Lemon
* `incompatible`: A more specific relation should be defined
* `property`: OntoLex Lemon does not require that all lexico-semantic properties
    are sub properties of a particular property.
* `separator`: Separator not supported by OntoLex Lemon
* `formVariant`: OntoLex Lemon does not support form variants

The following properties are mapped but care should be taken as they are not 
allowed in the same location (i.e., attached to the lexical sense) in OntoLex
Lemon

* `definition`
* `broader`
* `equivalent`
* `example`
* `narrower`
* `topic`
