package connectors

import baseSpec.BaseSpecWithApplication
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import models.{APIError, FileInfo, GithubRepo, RepoItem, User, UserModel}
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

import java.time.Instant

class GithubConnectorSpec extends BaseSpecWithApplication with BeforeAndAfterAll {
  val TestGithubConnector = new GithubConnector(ws)

  val Port = 8080
  val Host = "localhost"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(Port))

  override def beforeAll: Unit = {
    wireMockServer.start()
    configureFor(Host, Port)
  }

  override def afterAll: Unit = {
    wireMockServer.stop()
    ws.close()
  }

  "GithubConnectorSpec .get()" should {
    "return a Right(User)" in {
      stubFor(get(urlEqualTo("/users/matthew-goh"))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{
            |  "login": "matthew-goh",
            |  "id": 186605436,
            |  "node_id": "U_kgDOCx9ffA",
            |  "avatar_url": "https://avatars.githubusercontent.com/u/186605436?v=4",
            |  "gravatar_id": "",
            |  "url": "https://api.github.com/users/matthew-goh",
            |  "html_url": "https://github.com/matthew-goh",
            |  "type": "User",
            |  "user_view_type": "public",
            |  "site_admin": false,
            |  "name": "Matthew Goh",
            |  "company": null,
            |  "blog": "",
            |  "location": null,
            |  "email": null,
            |  "hireable": null,
            |  "bio": null,
            |  "twitter_username": null,
            |  "public_repos": 5,
            |  "public_gists": 0,
            |  "followers": 0,
            |  "following": 0,
            |  "created_at": "2024-10-28T15:22:40Z",
            |  "updated_at": "2024-11-05T11:54:37Z"
            |}
          """.stripMargin)))

      whenReady(TestGithubConnector.get[User]("http://localhost:8080/users/matthew-goh").value) { result =>
        result shouldBe Right(User("matthew-goh", None, Instant.parse("2024-10-28T15:22:40Z"), 0, 0))
      }
    }

    "return a Not found error" in {
      stubFor(get(urlEqualTo("/users/abc"))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{
            |  "message": "Not Found",
            |  "documentation_url": "https://docs.github.com/rest",
            |  "status": "404"
            |}""".stripMargin)))

      whenReady(TestGithubConnector.get[User]("http://localhost:8080/users/abc").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Not found"))
      }
    }
  }

  "return a Could not connect error if the response cannot be mapped to a User" in {
    stubFor(get(urlEqualTo("/users/abc"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""{
          |  "login": "matthew-goh",
          |  "id": 186605436,
          |  "followers": 0,
          |  "following": 0
          |}""".stripMargin)))

    whenReady(TestGithubConnector.get[User]("http://localhost:8080/users/abc").value) { result =>
      result shouldBe Left(APIError.BadAPIResponse(500, "Could not connect"))
    }
  }

  "GithubConnectorSpec .getList()" should {
    "return a Right(Seq[RepoItem])" in {
      stubFor(get(urlEqualTo("/repos/matthew-goh/scala101/contents"))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""[
            |  {
            |    "name": ".gitignore",
            |    "path": ".gitignore",
            |    "sha": "ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
            |    "size": 158,
            |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/.gitignore?ref=main",
            |    "html_url": "https://github.com/matthew-goh/scala101/blob/main/.gitignore",
            |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
            |    "download_url": "https://raw.githubusercontent.com/matthew-goh/scala101/main/.gitignore",
            |    "type": "file",
            |    "_links": {
            |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/.gitignore?ref=main",
            |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/ae66c9c4436c5dce22a6d1855552f6651ea11ef4",
            |      "html": "https://github.com/matthew-goh/scala101/blob/main/.gitignore"
            |    }
            |  },
            |  {
            |    "name": "build.sbt",
            |    "path": "build.sbt",
            |    "sha": "477a19d9089787571e77878c7fe0fc5b05541753",
            |    "size": 387,
            |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
            |    "html_url": "https://github.com/matthew-goh/scala101/blob/main/build.sbt",
            |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/477a19d9089787571e77878c7fe0fc5b05541753",
            |    "download_url": "https://raw.githubusercontent.com/matthew-goh/scala101/main/build.sbt",
            |    "type": "file",
            |    "_links": {
            |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
            |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/477a19d9089787571e77878c7fe0fc5b05541753",
            |      "html": "https://github.com/matthew-goh/scala101/blob/main/build.sbt"
            |    }
            |  },
            |  {
            |    "name": "project",
            |    "path": "project",
            |    "sha": "318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
            |    "size": 0,
            |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/project?ref=main",
            |    "html_url": "https://github.com/matthew-goh/scala101/tree/main/project",
            |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/trees/318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
            |    "download_url": null,
            |    "type": "dir",
            |    "_links": {
            |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/project?ref=main",
            |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/trees/318820de6fa5c540fcdc7dcdb15e492ca36cd2fc",
            |      "html": "https://github.com/matthew-goh/scala101/tree/main/project"
            |    }
            |  },
            |  {
            |    "name": "src",
            |    "path": "src",
            |    "sha": "a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
            |    "size": 0,
            |    "url": "https://api.github.com/repos/matthew-goh/scala101/contents/src?ref=main",
            |    "html_url": "https://github.com/matthew-goh/scala101/tree/main/src",
            |    "git_url": "https://api.github.com/repos/matthew-goh/scala101/git/trees/a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
            |    "download_url": null,
            |    "type": "dir",
            |    "_links": {
            |      "self": "https://api.github.com/repos/matthew-goh/scala101/contents/src?ref=main",
            |      "git": "https://api.github.com/repos/matthew-goh/scala101/git/trees/a19a9c57da0f6bfdca670328671c1bff0c4cb67f",
            |      "html": "https://github.com/matthew-goh/scala101/tree/main/src"
            |    }
            |  }
            |]
          """.stripMargin)))

      whenReady(TestGithubConnector.getList[RepoItem]("http://localhost:8080/repos/matthew-goh/scala101/contents").value) { result =>
        result shouldBe Right(Seq(RepoItem(".gitignore", ".gitignore", "file"), RepoItem("build.sbt", "build.sbt", "file"),
          RepoItem("project", "project", "dir"), RepoItem("src", "src", "dir")))
      }
    }

    "return a Not found error" in {
      stubFor(get(urlEqualTo("/repos/matthew-goh/abc/contents"))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{
                      |  "message": "Not Found",
                      |  "documentation_url": "https://docs.github.com/rest/repos/contents#get-repository-content",
                      |  "status": "404"
                      |}""".stripMargin)))

      whenReady(TestGithubConnector.getList[RepoItem]("http://localhost:8080/repos/matthew-goh/abc/contents").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "Not found"))
      }
    }

    "return a Could not connect error if the response cannot be mapped to a Seq[RepoItem]" in {
      stubFor(get(urlEqualTo("/repos/matthew-goh/scala101/contents/build.sbt"))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{
                      |  "name": "build.sbt",
                      |  "path": "build.sbt",
                      |  "sha": "477a19d9089787571e77878c7fe0fc5b05541753",
                      |  "size": 387,
                      |  "url": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
                      |  "type": "file",
                      |  "content": "VGhpc0J1aWxkIC8gdmVyc2lvbiA6PSAiMC4xLjAtU05BUFNIT1QiCgpUaGlz\nQnVpbGQgLyBzY2FsYVZlcnNpb24gOj0gIjIuMTMuMTQiCgpsYXp5IHZhbCBy\nb290ID0gKHByb2plY3QgaW4gZmlsZSgiLiIpKQogIC5zZXR0aW5ncygKICAg\nIG5hbWUgOj0gInNjYWxhMTAxIgogICkKCi8vbGlicmFyeURlcGVuZGVuY2ll\ncyArPSAib3JnLnNjYWxhLWxhbmcubW9kdWxlcyIgJSUgInNjYWxhLXBhcnNl\nci1jb21iaW5hdG9ycyIgJSAiMS4xLjIiCmxpYnJhcnlEZXBlbmRlbmNpZXMg\nKz0gIm9yZy5zY2FsYWN0aWMiICUlICJzY2FsYWN0aWMiICUgIjMuMi4xOSIK\nbGlicmFyeURlcGVuZGVuY2llcyArPSAib3JnLnNjYWxhdGVzdCIgJSUgInNj\nYWxhdGVzdCIgJSAiMy4yLjE5IiAlIFRlc3QK\n",
                      |  "encoding": "base64",
                      |  "_links": {
                      |    "self": "https://api.github.com/repos/matthew-goh/scala101/contents/build.sbt?ref=main",
                      |    "git": "https://api.github.com/repos/matthew-goh/scala101/git/blobs/477a19d9089787571e77878c7fe0fc5b05541753",
                      |    "html": "https://github.com/matthew-goh/scala101/blob/main/build.sbt"
                      |  }
                      |}""".stripMargin)))

      whenReady(TestGithubConnector.getList[RepoItem]("http://localhost:8080/repos/matthew-goh/scala101/contents/build.sbt").value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(500, "Could not connect"))
      }
    }
  }

  "GithubConnectorSpec .create()" should {
    "return a Right(response)" in {
      val responseBody = """{"content":{"name":"testfile.txt","path":"testfile.txt","sha":"4753fddcf141a3798b6aed0e81f56c7f14535ed7","size":18,
"url":"https://api.github.com/repos/matthew-goh/test-repo/contents/testfile.txt?ref=main",
"html_url":"https://github.com/matthew-goh/test-repo/blob/main/testfile.txt",
"git_url":"https://api.github.com/repos/matthew-goh/test-repo/git/blobs/4753fddcf141a3798b6aed0e81f56c7f14535ed7",
"download_url":"https://raw.githubusercontent.com/matthew-goh/test-repo/main/testfile.txt",
"type":"file","_links":{"self":"https://api.github.com/repos/matthew-goh/test-repo/contents/testfile.txt?ref=main",
"git":"https://api.github.com/repos/matthew-goh/test-repo/git/blobs/4753fddcf141a3798b6aed0e81f56c7f14535ed7",
"html":"https://github.com/matthew-goh/test-repo/blob/main/testfile.txt"}},
"commit":{"sha":"30fc1672e979da86d88f45e7ce46dbef3bd59d31","node_id":"C_kwDONRSKnNoAKDMwZmMxNjcyZTk3OWRhODZkODhmNDVlN2NlNDZkYmVmM2JkNTlkMzE","url":"https://api.github.com/repos/matthew-goh/test-repo/git/commits/30fc1672e979da86d88f45e7ce46dbef3bd59d31","html_url":"https://github.com/matthew-goh/test-repo/commit/30fc1672e979da86d88f45e7ce46dbef3bd59d31",
"author":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T10:28:44Z"},"committer":{"name":"Matthew Goh","email":"matthew.goh@mercator.group","date":"2024-11-19T10:28:44Z"},
"tree":{"sha":"3eccd62615f2ad1ccb13b5d3cf77f67dd71ee9a9","url":"https://api.github.com/repos/matthew-goh/test-repo/git/trees/3eccd62615f2ad1ccb13b5d3cf77f67dd71ee9a9"},"message":"Test commit","parents":[],
"verification":{"verified":false,"reason":"unsigned","signature":null,"payload":null,"verified_at":null}}}
"""

      stubFor(put(urlEqualTo("/users/matthew-goh/repos/test-repo/testfile.txt"))
        .withRequestBody(equalToJson("""
            {
              "message": "Another test commit",
              "content": "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
            }""".stripMargin))
        .willReturn(aResponse()
          .withStatus(201)
          .withHeader("Content-Type", "application/json")
          .withBody(responseBody)))

      whenReady(TestGithubConnector.create("http://localhost:8080/users/matthew-goh/repos/test-repo/testfile.txt",
        Json.obj(
        "message" -> "Another test commit",
        "content" -> "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
      )).value) { result =>
        result shouldBe Right(Json.parse(responseBody))
      }
    }

    "return a Not found error" in {
      stubFor(put(urlEqualTo("/users/abc/repos/test-repo/testfile.txt"))
        .withRequestBody(equalToJson(
          """
            {
              "message": "Another test commit",
              "content": "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
            }""".stripMargin))
        .willReturn(aResponse()
          .withStatus(404)
          .withHeader("Content-Type", "application/json")
          .withBody("""{"message":"Not Found","documentation_url":"https://docs.github.com/rest/repos/contents#create-or-update-file-contents","status":"404"}""")))

      whenReady(TestGithubConnector.create("http://localhost:8080/users/abc/repos/test-repo/testfile.txt",
        Json.obj(
          "message" -> "Another test commit",
          "content" -> "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
        )).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(404, "User or repository not found"))
      }
    }

    "return an invalid path error" in {
      stubFor(put(urlEqualTo("/users/matthew-goh/repos/test-repo/invalid//testfile.txt"))
        .withRequestBody(equalToJson(
          """
            {
              "message": "Another test commit",
              "content": "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
            }""".stripMargin))
        .willReturn(aResponse()
          .withStatus(422)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{"message":"path contains a malformed path component","errors":[{"resource":"Commit","field":"path","code":"invalid"}],
              |"documentation_url":"https://docs.github.com/rest/repos/contents#create-or-update-file-contents","status":"422"}""".stripMargin)))

      whenReady(TestGithubConnector.create("http://localhost:8080/users/matthew-goh/repos/test-repo/invalid//testfile.txt",
        Json.obj(
          "message" -> "Another test commit",
          "content" -> "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
        )).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(422, "Invalid path"))
      }
    }

    "return a file already exists error" in {
      stubFor(put(urlEqualTo("/users/matthew-goh/repos/test-repo/testfile.txt"))
        .withRequestBody(equalToJson(
          """
            {
              "message": "Another test commit",
              "content": "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
            }""".stripMargin))
        .willReturn(aResponse()
          .withStatus(422)
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{"message":"Invalid request.\n\n\"sha\" wasn't supplied.",
              |"documentation_url":"https://docs.github.com/rest/repos/contents#create-or-update-file-contents","status":"422"}""".stripMargin)))

      whenReady(TestGithubConnector.create("http://localhost:8080/users/matthew-goh/repos/test-repo/testfile.txt",
        Json.obj(
          "message" -> "Another test commit",
          "content" -> "Q3JlYXRpbmcgYW5vdGhlciB0ZXN0IGZpbGU="
        )).value) { result =>
        result shouldBe Left(APIError.BadAPIResponse(422, "File already exists"))
      }
    }
  }
}
