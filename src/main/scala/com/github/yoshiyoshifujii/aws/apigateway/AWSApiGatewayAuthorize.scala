package com.github.yoshiyoshifujii.aws.apigateway

import com.amazonaws.services.apigateway.model._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.Try

trait AWSApiGatewayAuthorizeWrapper extends AWSApiGatewayRestApiWrapper {
  val restApiId: RestApiId

  def createAuthorizer(name: String,
                       authorizerUri: Uri,
                       identitySourceHeaderName: String,
                       identityValidationExpression: Option[String],
                       authorizerResultTtlInSeconds: Option[Int] = Some(300)) = Try {
    val request = new CreateAuthorizerRequest()
      .withRestApiId(restApiId)
      .withName(name)
      .withType(AuthorizerType.TOKEN)
      .withAuthType("custom")
      .withAuthorizerUri(authorizerUri.value)
      .withIdentitySource(IdentitySource(identitySourceHeaderName).mkValue)
    identityValidationExpression.foreach(request.setIdentityValidationExpression)
    authorizerResultTtlInSeconds.foreach(request.setAuthorizerResultTtlInSeconds(_))

    client.createAuthorizer(request)
  }

  def getAuthorizer(name: String) =
    for {
      as <- getAuthorizers(restApiId)
    } yield as.getItems.find(a => a.getName == name)

  def updateAuthorizer(authorizerId: AuthorizerId,
                       name: String,
                       authorizerUri: Uri,
                       identitySourceHeaderName: String,
                       identityValidationExpression: Option[String],
                       authorizerResultTtlInSeconds: Option[Int] = Some(300)) = Try {
    lazy val generatePatch =
      (p: String) =>
        (v: String) =>
          Option {
            new PatchOperation()
              .withOp(Op.Replace)
              .withPath(p)
              .withValue(v)
      }

    lazy val patchOperations = Seq(
      generatePatch("/name")(name),
      generatePatch("/authorizerUri")(authorizerUri.value),
      generatePatch("/identitySource")(IdentitySource(identitySourceHeaderName).mkValue),
      identityValidationExpression.flatMap(generatePatch("/identityValidationExpression")(_)),
      authorizerResultTtlInSeconds.flatMap(i =>
        generatePatch("/authorizerResultTtlInSeconds")(i.toString))
    ).flatten

    val request = new UpdateAuthorizerRequest()
      .withRestApiId(restApiId)
      .withAuthorizerId(authorizerId)
      .withPatchOperations(patchOperations.asJava)

    client.updateAuthorizer(request)
  }

  def deployAuthorizer(name: String,
                       awsAccountId: String,
                       lambdaName: String,
                       lambdaAlias: Option[String],
                       identitySourceHeaderName: String,
                       identityValidationExpression: Option[String],
                       authorizerResultTtlInSeconds: Option[Int] = Some(300)) = {
    val _authorizerUri = Uri(
      regionName = regionName,
      awsAccountId = awsAccountId,
      lambdaName = lambdaName,
      lambdaAlias = lambdaAlias
    )
    for {
      aOp <- getAuthorizer(name)
      id <- aOp map { a =>
        updateAuthorizer(
          authorizerId = a.getId,
          name = name,
          authorizerUri = _authorizerUri,
          identitySourceHeaderName = identitySourceHeaderName,
          identityValidationExpression = identityValidationExpression,
          authorizerResultTtlInSeconds = authorizerResultTtlInSeconds
        ).map(_.getId)
      } getOrElse {
        createAuthorizer(
          name = name,
          authorizerUri = _authorizerUri,
          identitySourceHeaderName = identitySourceHeaderName,
          identityValidationExpression = identityValidationExpression,
          authorizerResultTtlInSeconds = authorizerResultTtlInSeconds
        ).map(_.getId)
      }
    } yield id
  }

  def deleteAuthorizer(name: String) = {
    lazy val deleteAuthorizer = (authorizerId: AuthorizerId) => {
      val request = new DeleteAuthorizerRequest()
        .withRestApiId(restApiId)
        .withAuthorizerId(authorizerId)

      client.deleteAuthorizer(request)
    }

    for {
      aOp <- getAuthorizer(name)
      d <- Try {
        aOp map { a =>
          deleteAuthorizer(a.getId)
        } getOrElse (throw new NotFoundException(s"authorizer not found: $name"))
      }
    } yield d
  }

}
case class AWSApiGatewayAuthorize(regionName: String, restApiId: RestApiId)
    extends AWSApiGatewayAuthorizeWrapper
