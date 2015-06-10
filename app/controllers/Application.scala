package controllers

import org.apache.commons.codec.binary.{Base64, StringUtils}
import play.api.libs.json.Json
import play.api.libs.ws.{WSAuthScheme, WS}
import play.api.mvc._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application extends Controller {


  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  /**
   * SEND USER AGENT TO KENOBI
   */
  def authorize = Action {
    Redirect("http://localhost:9000/oauth/authorize" +
        "?scope=scim.write,openid,scim.read" +
        "&response_type=code" +
        "&client_id=test" +
        "&redirect_uri=http%3A%2F%2Flocalhost%3A9001%2Fcallback" +
        "&state=123" +
        "&nonce=123")
  }

  /**
   * CALLBACK FROM KENOBI - CATCH THE AUTHORIZATION CODE
   */
  def callback = Action.async { request =>
    for {
      code <- Future(request.getQueryString("code").get)
    } yield Ok(views.html.callback(code, request.queryString.mapValues(_.mkString(", "))))
  }

  /**
   * GET THE TOKEN FROM THE TOKEN ENDPOINT OF KENOBI
   */
  def token(code: String) = Action.async { request =>
    for {
      resp <- WS
        .url("http://localhost:9000/oauth/token").withAuth("test", "testSecret", WSAuthScheme.BASIC)
        .post(
          Map("code" -> Seq(code),
            "grant_type" -> Seq("authorization_code"),
            "redirect_uri" -> Seq(s"http://${request.host}${controllers.routes.Application.callback}")
          ))
    } yield Ok(views.html.token(accessTokenFrom(resp.body), translatedAccessToken(resp.body)))
  }

  /**
   * GET THE USER INFORMATION FROM THE OPENID CONNECT ENDPOINT
   */
  def openid(token: String) = Action.async { request =>
    for {
      user <- WS.url("http://localhost:9000/userinfo").withHeaders("Authorization" -> s"Bearer $token").get()
    } yield Ok(user.body)
  }

  /**
   * HELPER TO PARSE ACCESS TOKEN
   */
  def accessTokenFrom(responseBody: String): String = {
    (Json.parse(responseBody) \ "access_token").get.toString().replaceAll("^\"|\"$", "")
  }

  /**
   * DECODES ACCESS TOKEN FROM BASE64
   */
  def translatedAccessToken(responseBody: String): List[String] = {
    accessTokenFrom(responseBody).split("\\.").take(2).map(str => StringUtils.newStringUtf8(Base64.decodeBase64(str))).toList
  }
}
