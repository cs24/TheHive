package models

import javax.inject.{ Inject, Singleton }

import models.JsonFormat.alertStatusFormat
import org.elastic4play.models.{ Attribute, AttributeDef, BaseEntity, EntityDef, HiveEnumeration, ModelDef }
import org.elastic4play.models.{ AttributeFormat ⇒ F, AttributeOption ⇒ O }
import play.api.Logger
import play.api.libs.json.{ JsObject, JsString, Json }
import services.AuditedModel

import scala.concurrent.Future

object AlertStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val New, Update, Ignore, Imported = Value
}

trait AlertAttributes {
  _: AttributeDef ⇒
  def artifactAttributes: Seq[Attribute[_]]

  val alertId = attribute("_id", F.stringFmt, "Alert id", O.readonly)
  val tpe = attribute("type", F.stringFmt, "Type of the alert", O.readonly)
  val source = attribute("source", F.stringFmt, "Source of the alert", O.readonly)
  val sourceRef = attribute("sourceRef", F.stringFmt, "Source reference of the alert", O.readonly)
  val date = attribute("date", F.dateFmt, "Date of the alert", O.readonly)
  val lastSyncDate = attribute("lastSyncDate", F.dateFmt, "Date of the last synchronization")
  val caze = optionalAttribute("case", F.stringFmt, "Id of the case, if created")
  val title = attribute("title", F.textFmt, "Title of the alert")
  val description = attribute("description", F.textFmt, "Description of the alert")
  val severity = attribute("severity", F.numberFmt, "Severity if the alert (0-5)", 3L)
  val tags = multiAttribute("tags", F.stringFmt, "Alert tags")
  val tlp = attribute("tlp", F.numberFmt, "TLP level", 2L)
  val artifacts = multiAttribute("artifacts", F.objectFmt(artifactAttributes), "Artifact of the alert")
  val caseTemplate = optionalAttribute("caseTemplate", F.stringFmt, "Case template to use")
  val status = attribute("status", F.enumFmt(AlertStatus), "Status of the alert", AlertStatus.New)
  val follow = attribute("follow", F.booleanFmt, "", true)
}

@Singleton
class AlertModel @Inject() (artifactModel: ArtifactModel)
    extends ModelDef[AlertModel, Alert]("alert")
    with AlertAttributes
    with AuditedModel {

  private[AlertModel] lazy val logger = Logger(getClass)
  override val defaultSortBy: Seq[String] = Seq("-date")
  override val removeAttribute: JsObject = Json.obj("status" → AlertStatus.Ignore)

  override def artifactAttributes: Seq[Attribute[_]] = artifactModel.attributes

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    Future.successful {
      if (attrs.keys.contains("_id"))
        attrs
      else {
        val tpe = (attrs \ "tpe").asOpt[String].getOrElse("<null>")
        val source = (attrs \ "source").asOpt[String].getOrElse("<null>")
        val sourceRef = (attrs \ "sourceRef").asOpt[String].getOrElse("<null>")
        attrs + ("_id" → JsString(s"$tpe|$source|$sourceRef"))
      }
    }
  }
}

class Alert(model: AlertModel, attributes: JsObject)
    extends EntityDef[AlertModel, Alert](model, attributes)
    with AlertAttributes {

  override def artifactAttributes: Seq[Attribute[_]] = Nil

  def toCaseJson: JsObject = Json.obj(
    //"caseId" -> caseId,
    "title" → title(),
    "description" → description(),
    "severity" → severity(),
    //"owner" -> owner,
    "startDate" → date(),
    "tags" → tags(),
    "tlp" → tlp(),
    "status" → CaseStatus.Open)
}
