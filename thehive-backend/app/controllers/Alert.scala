package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.runtime.universe
import scala.util.{ Failure, Success }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsArray, JsObject, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Controller

import org.elastic4play.{ BadRequestError, CreateError, Timed }
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.{ baseModelEntityWrites, multiFormat }
import org.elastic4play.services.{ Agg, AuxSrv }
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }

import models.{ Alert, AlertStatus }
import services.{ AlertSrv, TaskSrv }
import scala.util.Try

@Singleton
class AlertCtrl @Inject() (
    alertSrv: AlertSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends Controller with Status {

  val log = Logger(getClass)

  @Timed
  def create() = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    alertSrv.create(request.body)
      .map(alert ⇒ renderer.toOutput(CREATED, alert))
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request ⇒
    val withStats = for {
      statsValues ← request.queryString.get("nstats")
      firstValue ← statsValues.headOption
    } yield Try(firstValue.toBoolean).getOrElse(firstValue == "1")

    for {
      alert ← alertSrv.get(id)
      alertsWithStats ← auxSrv.apply(alert, 0, withStats.getOrElse(false), false)
    } yield renderer.toOutput(OK, alertsWithStats)
  }

  @Timed
  def update(id: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    alertSrv.update(id, request.body)
      .map { alert ⇒ renderer.toOutput(OK, alert) }
  }

  @Timed
  def bulkUpdate() = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    request.body.getStrings("ids").fold(Future.successful(Ok(JsArray()))) { ids ⇒
      alertSrv.bulkUpdate(ids, request.body.unset("ids")).map(multiResult ⇒ renderer.toMultiOutput(OK, multiResult))
    }
  }

  @Timed
  def delete(id: String) = authenticated(Role.write).async { implicit request ⇒
    alertSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find() = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (alerts, total) = alertSrv.find(query, range, sort)
    val alertsWithStats = auxSrv.apply(alerts, nparent, withStats, false)
    renderer.toOutput(OK, alertsWithStats, total)
  }

  @Timed
  def stats() = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    alertSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }
}