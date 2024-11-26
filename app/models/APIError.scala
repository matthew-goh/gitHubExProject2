package models

import play.api.http.Status

// abstract class can pass constructor parameters
sealed abstract class APIError(
                                val httpResponseStatus: Int,
                                val reason: String
                              )

object APIError {

  final case class BadAPIResponse(upstreamStatus: Int, upstreamMessage: String)
    extends APIError(
      upstreamStatus, // Status.INTERNAL_SERVER_ERROR,
      s"Bad response from upstream; got status: $upstreamStatus, and got reason: $upstreamMessage"
    )

}
