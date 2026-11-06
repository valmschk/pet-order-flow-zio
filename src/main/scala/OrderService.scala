//логика, безпасно обрабатывает ошибки и пересчитывает цену по каталогу

import zio.*

final class OrderService(
    catalog: CatalogClient,
    payment: PaymentClient,
    database: DatabaseRepo,
    kafka: KafkaProducer
):
  def createOrder(cart: Cart): IO[OrderError, OrderId] =
    for
      validated <- ZIO.fromEither(Cart.validate(cart))
      prices    <- catalog.checkStock(validated.items)
      
      totalCents = validated.items.map(i => prices.getOrElse(i.productId, 0L) * i.quantity).sum
      orderId    = java.util.UUID.randomUUID().toString
      
      _         <- database.saveOrder(orderId, validated, OrderStatus.Pending)

      paymentId <- payment
                     .processPayment(validated.userId, totalCents)
                     .tapError { (err: OrderError) =>
                       ZIO.logError(s"Оплата не прошла для $orderId: ${err.getMessage}") *>
                         database.updateStatus(orderId, OrderStatus.Failed).ignore
                     }

      _ <- database
             .updateStatus(orderId, OrderStatus.Paid)
             .tapError { (err: OrderError) =>
               ZIO.logError(s"Не удалось сохранить статус $orderId: ${err.getMessage}") *>
                 payment.refund(paymentId)
             }

      _ <- kafka
             .publishOrderCreated(orderId)
             .tapError { (err: OrderError) => 
               ZIO.logError(s"Kafka publish failed для $orderId: ${err.getMessage}") 
             }
             .ignore
    yield orderId

object OrderService:
  val live
      : URLayer[CatalogClient & PaymentClient & DatabaseRepo & KafkaProducer, OrderService] =
    ZLayer.fromFunction(new OrderService(_, _, _, _))