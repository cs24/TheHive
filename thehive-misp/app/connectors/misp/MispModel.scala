package connectors.misp
//
import java.util.Date

//import connectors.misp.JsonFormat.eventWrites
import org.elastic4play.controllers.Fields
import play.api.libs.json._
//
//import javax.inject.{ Inject, Singleton }
//
//import play.api.libs.json.JsObject
//
//import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, EntityDef, HiveEnumeration, ModelDef }
//
//import JsonFormat.eventStatusFormat
//import services.AuditedModel
//
//object EventStatus extends Enumeration with HiveEnumeration {
//  type Type = Value
//  val New, Update, Ignore, Imported = Value
//}
//
//trait MispAttributes { _: AttributeDef ⇒
//  val eventUuid = attribute("_id", F.stringFmt, "event uuid")
//  val serverId = attribute("serverId", F.stringFmt, "Id of the server")
//  val eventId = attribute("eventId", F.numberFmt, "Id of MIPS event")
//  val org = attribute("org", F.stringFmt, "Org")
//  val info = attribute("info", F.textFmt, "Info")
//  val tags = multiAttribute("tags", F.stringFmt, "tags")
//  val date = attribute("date", F.dateFmt, "date")
//  val threatLevel = attribute("threatLevel", F.numberFmt, "Threate level")
//  val analysis = attribute("analysis", F.numberFmt, "Analysis")
//  val attributeCount = attribute("attributeCount", F.numberFmt, "Number of attributes")
//  val eventStatus = attribute("eventStatus", F.enumFmt(EventStatus), "event status", EventStatus.New)
//  val caze = optionalAttribute("case", F.stringFmt, "Id of the case, if created")
//  val follow = attribute("follow", F.booleanFmt, "", true)
//  val publishDate = attribute("publishDate", F.dateFmt, "")
//}
//
//@Singleton
//class MispModel @Inject() () extends ModelDef[MispModel, Misp]("misp") with MispAttributes with AuditedModel
//
//class Misp(model: MispModel, attributes: JsObject) extends EntityDef[MispModel, Misp](model, attributes) with MispAttributes
//
case class MispEvent(_id: String, serverId: String, eventId: Long, org: String, info: String, tags: Seq[String], date: Date, publishDate: Date, attributeCount: Int, threatLevel: Int, analysis: Int) {
  val eventUuid = _id
  def toAlertJson = {
    val alertTags = JsString(s"src:$org") +: tags.collect {
      case t if !t.toLowerCase.startsWith("tlp:") ⇒ JsString(t)
    }
    val tlp = tags
      .map(_.toLowerCase)
      .collectFirst {
        case "tlp:white" ⇒ 0L
        case "tlp:green" ⇒ 1L
        case "tlp:amber" ⇒ 2L
        case "tlp:red"   ⇒ 3L
      }
      .getOrElse(2L)
    Json.obj(
      "type" → "misp",
      "source" → serverId,
      "sourceRef" → eventId.toString,
      "date" → date,
      "lastSyncDate" → publishDate,
      "title" → s"#$eventId ${info.trim}",
      "description" → s"Imported from MISP Event #$eventId, created at $date",
      "severity" → threatLevel,
      "tags" → alertTags,
      "tlp" → tlp)
  }
}

case class MispAlert(
  eventUuid: String,
  source: String,
  sourceRef: String,
  date: Date,
  lastSyncDate: Date,
  title: String,
  description: String,
  severity: Long,
  tags: Seq[String],
  tlp: Long,
  artifacts: Seq[JsObject], // FIXME
  caseTemplate: String)

case class MispAttribute(id: String, tpe: String, category: String, uuid: String, eventId: Long, date: Date, comment: String, value: String)