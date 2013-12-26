package org.ucombinator.webapp

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database

import org.ucombinator.webapp.auth.AuthenticationSupport
import org.ucombinator.webapp.db.Users

class LoginServlet(val db: Database) extends TapasWebAppStack with FlashMapSupport with AuthenticationSupport {
  before() {
    flash("test") = "foo"
  }
  get("/") {
    db withSession {
      if (isAuthenticated) {
        redirect("/")
      } else {
        redirect("/account/login")
      }
    }
  }

  get("/login") {
    contentType = "text/html"
    ssp("/account/login",
      ("flash", flash), ("title", "Login or Sign-up!"),
      ("isSignedIn", false), ("isAdmin", false))
  }

  post("/login") {
    db withSession {
      scentry.authenticate()
      if (isAuthenticated) {
        redirect("/")
      } else {
        flash("loginError") = "incorrect username or password"
        flash("loginOnly") = "true"
        redirect("/account/login")
      }
    }
  }

  post("/signup") {
    // clean up the following with a simpler validation system
    var valid = true
    val name = params.get("name") match {
                 case Some(name) => name
                 case _ => { flash("signup.name") = "please specify a name"; valid = false; "" }
               }
    val username = params.get("username") match {
                     case Some(username) => {
                       db withSession {
                         if (! Users.isUsernameAvailable(username)) {
                           flash("signup.username") = "username is already in use, please seleect another"
                           valid = false
                         }
                       }
                       username
                     }
                     case _ => {
                       flash("signup.username") = "please specify a username"
                       valid = false
                       ""
                     }
                   }
    val email = params.get("email")
    val password = params.get("password") match {
                     case Some(password) => password
                     case _ => {
                       flash("signup.password") = "please specify a password"
                       valid = false
                       ""
                     }
                   }
    val password_confirm = params.get("password_confirm") match {
                             case Some(password_confirm) => {
                               if (password_confirm != password) {
                                 flash("signup.password_confirm") = "password and conrimation do not match"
                                 valid = false
                               }
                               password_confirm
                             }
                             case _ => {
                               flash("signup.password_confirm") = "please specify a password confirmation"
                               valid = false
                             }
                           }
    if (valid) {
      db withSession {
        val id = Users.addUser(name, username, email, password)
        val user = Users.lookupUserById(id)
        session.setAttribute("signup.user", user)
        scentry.authenticate()
      }
      redirect("/")
    } else {
      flash("signup.name.value") = name
      flash("signup.username.value") = username
      flash("signup.email.value") = email match { case Some(s) => s; case _ => "" }
      flash("loginError") = "Unable to signup, please correct the following errors"
      flash("signupOnly") = "true"
      redirect("/account/login")
    }
  }

  get("/logout") {
    db withSession { scentry.logout() }
    redirect("/")
  }
}
