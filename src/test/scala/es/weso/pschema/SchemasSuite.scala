package es.weso.pschema 

import org.apache.spark.sql.types._
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._
import com.github.mrpowers.spark.fast.tests._
import munit._
import es.weso.simpleshex._
import es.weso.wbmodel.Value._
import es.weso.wbmodel._
import es.weso.graphxhelpers.GraphBuilder._
import org.apache.spark.graphx.VertexRDD
import es.weso.rbe.interval._
import scala.collection.immutable.SortedSet
import es.weso.rdf.nodes._

class SchemasSuite extends FunSuite 
  with SparkSessionTestWrapper with DatasetComparer with RDDComparer {

  private def sort(
    ps: List[(String, List[String], List[String])]
    ): List[(String, List[String], List[String])] = 
    ps.map{ 
      case (p, vs, es) => (p, vs.sorted, es.sorted) 
    }.sortWith(_._1 < _._1) 

  import spark.implicits._

 def testCase(
  name: String,
  gb: GraphBuilder[Entity,Statement], 
  schemaStr: String,
  expected: List[(String, List[String], List[String])],
  maxIterations: Int = Int.MaxValue,
  verbose: Boolean = false
)(implicit loc: munit.Location): Unit = {
 test(name) { 
  val graph = buildGraph(gb, spark.sparkContext)
  Schema.unsafeFromString(schemaStr, CompactFormat).fold(
    err => fail(s"Error parsing schema: $err"),
    schema => {
     val validatedGraph = 
   PSchema[Entity,Statement,ShapeLabel,Reason, PropertyId](
     graph, Start, maxIterations, verbose)(
     schema.checkLocal,schema.checkNeighs,schema.getTripleConstraints,_.id
   )

  val vertices: List[(Long,Shaped[Entity,ShapeLabel,Reason,PropertyId])] = 
        validatedGraph.vertices.collect().toList

  val result: List[(String, List[String], List[String])] = 
    sort(
     vertices
     .map{ case (_, sv) => 
      ( sv.value, 
        sv.okShapes.map(_.name).toList, 
        sv.noShapes.map(_.name).toList
      )}
      .collect { 
       case (e: Entity, okShapes, noShapes) => 
        (e.entityId.id, okShapes, noShapes) 
       }
      )
    assertEquals(result,expected)
  })
 }
}

 {
  val gb = for {
       instanceOf <- P(31, "instance of")
       timbl <- Q(80, "Tim")
       human <- Q(5, "Human")
       researcher <- Q(6, "Researcher")
       other <- Q(7,"Other")
     } yield {
       vertexEdges(List(
         triple(timbl, instanceOf, researcher),
         triple(timbl, instanceOf, human),
         triple(other, instanceOf, researcher)
       ))
     }
  val schema = 
    """|prefix wde: <http://www.wikidata.org/entity/>
       |
       |# Defines humans in Wikidata
       |Start = @<Human>
       |
       |<Human> EXTRA wde:P31 {
       | wde:P31 @<HumanCode> 
       |}
       |<HumanCode> [ wde:Q5 ]
       |<Any> {} 
       |""".stripMargin

     val expected = sort(List(
        ("Q5", List("HumanCode"), List("Start")),
        ("Q6", List(), List("HumanCode", "Start")),
        ("Q7", List(), List("Start")),
        ("Q80", List("Start"), List())
    ))
   testCase(
     "Example with 2 instances...", 
     gb,schema,
     expected,
     5, true)
  }


}