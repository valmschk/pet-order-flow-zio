//traits для внешних систем и перечисление статусов заказа

import zio.*

trait CatalogClient:
  def checkStock(items: List[CartItem]): IO[OrderError, Map[String, Long]]

trait PaymentClient:
  def processPayment(userId: String, amountCents: Long): IO[OrderError, String]
  def refund(paymentId: String): UIO[Unit]

trait DatabaseRepo:
  def saveOrder(orderId: OrderId, cart: Cart, status: OrderStatus): IO[OrderError, Unit]
  def updateStatus(orderId: OrderId, status: OrderStatus): IO[OrderError, Unit]

trait KafkaProducer:
  def publishOrderCreated(orderId: OrderId): IO[OrderError, Unit]