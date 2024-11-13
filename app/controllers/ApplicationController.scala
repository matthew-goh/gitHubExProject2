package controllers

import models.UserModel
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf.CSRF
import services.{GithubService, RepositoryService}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(repoService: RepositoryService, service: GithubService, val controllerComponents: ControllerComponents)
                                     (implicit ec: ExecutionContext) extends BaseController with play.api.i18n.I18nSupport {
  ///// METHODS CALLED BY FRONTEND /////
  def accessToken(implicit request: Request[_]) = {
    CSRF.getToken
  }

  ///// API METHODS WITHOUT FRONTEND /////

}
