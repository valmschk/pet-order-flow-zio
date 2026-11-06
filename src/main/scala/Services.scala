//реализация интерфейсов из Ports и сборка ZLayer-слоев для зависимостей

import zio.*

final case class LiveCatalogClient(stock: Map[String, Long]) extends CatalogClient:
  override def checkStock(items: List[CartItem]): IO[OrderError, Map[String, Long]] =
    ZIO.logInfo(s"Проверка наличия ${items.size} позиций...") *>
      ZIO.foldLeft(items)(Map.empty[String, Long]) { (acc, item) =>
        stock.get(item.productId) match
          case Some(price) if item.quantity > 0 =>
            ZIO.succeed(acc.updated(item.productId, price))
          case Some(_) =>
            ZIO.fail(OrderError.ValidationError(s"Некорректное количество для ${item.productId}"))
          case None =>
            ZIO.fail(OrderError.ItemOutOfStock(item.productId))
      }

final case class LivePaymentClient(maxAmountCents: Long = 1_000_000L) extends PaymentClient:
  override def processPayment(userId: String, amountCents: Long): IO[OrderError, String] =
    val logic: IO[OrderError, String] = 
      if amountCents <= 0 then
        ZIO.fail(OrderError.PaymentFailed("сумма должна быть положительной"))
      else if amountCents > maxAmountCents then
        ZIO.fail(OrderError.PaymentFailed(s"превышен лимит ${maxAmountCents / 100.0} руб."))
      else
        ZIO.succeed(s"pay-${java.util.UUID.randomUUID()}")

    ZIO.logInfo(s"Списание ${amountCents / 100.0} руб. для $userId...").flatMap(_ => logic)

  override def refund(paymentId: String): UIO[Unit] =
    ZIO.logInfo(s"Возврат по платежу $paymentId") // Убран .orDie

final case class LiveDatabaseRepo(ref: Ref[Map[OrderId, (Cart, OrderStatus)]]) extends DatabaseRepo:
  override def saveOrder(orderId: OrderId, cart: Cart, status: OrderStatus): IO[OrderError, Unit] =
    ZIO.logInfo(s"Сохранение заказа $orderId [$status]").flatMap { _ =>
      ref.update(_.updated(orderId, (cart, status))).unit
    }

  override def updateStatus(orderId: OrderId, status: OrderStatus): IO[OrderError, Unit] =
    ref.update(m => m.get(orderId).fold(m)(c => m.updated(orderId, (c._1, status)))).unit

final case class LiveKafkaProducer() extends KafkaProducer:
  override def publishOrderCreated(orderId: OrderId): IO[OrderError, Unit] =
    ZIO.logInfo(s"Публикация OrderCreated $orderId в Kafka").unit

object Layers:
  val catalogClient: ULayer[CatalogClient] = ZLayer.succeed(
    LiveCatalogClient(Map("sku-1" -> 19900L, "sku-2" -> 15050L, "sku-3" -> 9999L))
  )
  
  val paymentClient: ULayer[PaymentClient] = ZLayer.succeed(LivePaymentClient())
  
  val databaseRepo: ULayer[DatabaseRepo] = ZLayer.fromZIO {
    Ref.make(Map.empty[OrderId, (Cart, OrderStatus)]).map { (ref: Ref[Map[OrderId, (Cart, OrderStatus)]]) => 
      LiveDatabaseRepo(ref)
    }
  }
  
  val kafkaProducer: ULayer[KafkaProducer] = ZLayer.succeed(LiveKafkaProducer())