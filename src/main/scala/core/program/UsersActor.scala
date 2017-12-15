package slacks.core.program

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.SlackUsersListConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

/**
  * This is the Actor for the Slack Users Algebra
  * 
  * Captures all the errors/data found during the data retrieval
  *
  * @author Raymond Tay
  * @version 1.0
  */
import scala.concurrent.Future
case class UserList(users : List[User])
case object GetUsers
class SlackUsersActor(cfg : SlackUsersListConfig[String],
                      token : SlackAccessToken[String],
                      httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {

  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._
  import io.circe._, io.circe.parser._
  import io.circe.optics.JsonPath._

  import akka.pattern.{pipe}
  import context.dispatcher

  import providers.slack.algebra.Users._

  implicit val http = Http(context.system)
  private val defaultUri = s"${cfg.url}?token=${token.access_token}&limit=20"
  private def continuationUri = (cursor:String) ⇒ defaultUri + s"&limit=20&cursor=${cursor}"
  private var localStorage : UserList = UserList(Nil)

  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[UserList,A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx5[List, Option, Store, ReaderBytes, WriteLog]

  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("[Get-Users-Actor] Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  val parseAllUsers : Reader[io.circe.Json, providers.slack.models.Users] = Reader{ (json: io.circe.Json) ⇒
    import JsonCodec.{slackUserDec, slackUsersDec}
    json.as[providers.slack.models.Users].getOrElse(providers.slack.models.Users(false, List.empty[User], 0L, None, ""))
  }

  // Using lens, we look for the next cursor if we can find it (which would
  // return as a Some(x) else its a None)
  val getNextPage : Kleisli[Option,io.circe.Json,String] = Kleisli{ (json: io.circe.Json) ⇒
    root.response_metadata.next_cursor.string.getOption(json)
  }

  val decodeJsonNUpdateState : Eff[S2, Option[String]] = {
    for {
      datum <- ask[S2, ByteString]
       _    <- tell[S2,String]("[Get-Users-Actor] Collected the json string from ctx.")
      json  <- values[S2, List[io.circe.Json]](parse(datum.utf8String).getOrElse(Json.Null) :: Nil)
       _    <- tell[S2,String]("[Get-Users-Actor] Collected the decoded json string.")
      users <- singleton[S2, providers.slack.models.Users](parseAllUsers(json head))
      cursor<- fromOption[S2, String](getNextPage(json head))
       _    <- tell[S2, String]("[Get-Users-Actor] Processed json data for next-cursor.")
       _    <- modify[S2, UserList]((m:UserList) ⇒ {localStorage = m.copy(users = m.users ++ users.members); localStorage})
       _    <- tell[S2, String]("[Get-Users-Actor] internal state is updated.")
    } yield cursor.some

  }

  override def preStart() = {
    httpService.makeSingleRequest.run(defaultUri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      import io.circe.parser.decode
      import JsonCodec._
      import scala.concurrent._,duration._

      implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
      val possibleDatum : Throwable Either ByteString =
        Either.catchNonFatal{Await.result(extractDataFromHttpStream.runReader(entity).runWriterNoLog.run, 2 second)}

      val cursor : Option[String] =
      possibleDatum.toList.map(datum ⇒
        decodeJsonNUpdateState.runReader(datum).runOption.evalState(localStorage).runList.runWriterNoLog.run head
      ).flatten.head

      cursor.isDefined && !cursor.get.isEmpty match {
        case false ⇒
          log.warning("[Get-Users-Actor] No more further JSON data detected from Http stream.")
        case true ⇒
          log.debug(s"[Get-Users-Actor][local-storage] bot-messages: ${localStorage.users.size}")
          log.info(s"[Get-Users-Actor] following the cursor to retrieve more data...")
          httpService.makeSingleRequest.run(continuationUri(cursor.get)).pipeTo(self)
      }

    case GetUsers ⇒ 
      sender ! localStorage

    case StopAction ⇒ context stop self
  }
}

