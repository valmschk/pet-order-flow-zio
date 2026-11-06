import zio.*
import zio.http.*
import zio.json.*

object Main extends ZIOAppDefault:

  final case class CreateOrderResponse(orderId: String, status: String = "CREATED")
  object CreateOrderResponse:
    given JsonCodec[CreateOrderResponse] = DeriveJsonCodec.gen

  final case class ErrorResponse(error: String)
  object ErrorResponse:
    given JsonCodec[ErrorResponse] = DeriveJsonCodec.gen

  private def errorResponse(err: OrderError): Response =
    Response
      .json(ErrorResponse(err.getMessage).toJson)
      .status(err.toHttpStatus)

  val routes: Routes[OrderService, Response] = Routes(
    Method.POST / "orders" -> handler { (req: Request) =>
      (for
        bodyStr <- req.body
                     .asString
                     .mapError(e => OrderError.InvalidJson(e.getMessage))
        cart    <- ZIO
                     .fromEither(bodyStr.fromJson[Cart])
                     .mapError(err => OrderError.InvalidJson(err))
        svc     <- ZIO.service[OrderService]
        orderId <- svc.createOrder(cart)
      yield Response.json(CreateOrderResponse(orderId).toJson))
        .catchAll(err => ZIO.succeed(errorResponse(err)))
    },
    Method.GET / "health" -> handler(Response.ok)
  )

  val appLayer: ZLayer[Any, Nothing, OrderService] = 
    (Layers.catalogClient ++ Layers.paymentClient ++ Layers.databaseRepo ++ Layers.kafkaProducer) >>> 
    OrderService.live

  def run: ZIO[Any, Throwable, Unit] =
    Server.serve(routes).provide(
      appLayer,                     
      Server.defaultWithPort(8080)               
    )