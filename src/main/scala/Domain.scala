//описание моделей данных, ошибок и логики валидации корзины

import zio.json.*
import zio.http.Status
import scala.util.control.NoStackTrace

type OrderId = String

enum OrderStatus:
  case Pending, Paid, Failed

final case class CartItem(
    productId: String,
    name: String,
    priceCents: Long,
    quantity: Int
):
  def lineTotalCents: Long = priceCents * quantity.toLong

final case class Cart(
    userId: String,
    items: List[CartItem]
):
  def totalCents: Long = items.map(_.lineTotalCents).sum
  def totalAmount: BigDecimal = BigDecimal(totalCents) / 100

object Cart:
  given JsonCodec[CartItem] = DeriveJsonCodec.gen[CartItem]
  given JsonCodec[Cart]     = DeriveJsonCodec.gen[Cart]

  def validate(cart: Cart): Either[OrderError.ValidationError, Cart] =
    val errors = List(
      Option.when(cart.userId.trim.isEmpty)("userId не может быть пустым"),
      Option.when(cart.items.isEmpty)("корзина не может быть пустой"),
      Option.when(cart.items.size > 100)("слишком много позиций в корзине"),
      Option.when(cart.items.exists(_.quantity <= 0))("quantity должно быть > 0"),
      Option.when(cart.items.exists(_.priceCents < 0))("price не может быть отрицательным"),
      Option.when(cart.items.exists(_.productId.trim.isEmpty))("productId не может быть пустым")
    ).flatten

    if errors.isEmpty then Right(cart)
    else Left(OrderError.ValidationError(errors.mkString("; ")))

sealed trait OrderError extends Throwable with NoStackTrace

object OrderError:
  final case class ItemOutOfStock(productId: String) extends OrderError:
    override def getMessage: String = s"Товар $productId отсутствует на складе"

  final case class PaymentFailed(reason: String) extends OrderError:
    override def getMessage: String = s"Оплата не прошла: $reason"

  final case class DatabaseError(reason: String) extends OrderError:
    override def getMessage: String = s"Ошибка БД: $reason"

  final case class ValidationError(reason: String) extends OrderError:
    override def getMessage: String = s"Ошибка валидации: $reason"

  final case class InvalidJson(reason: String) extends OrderError:
    override def getMessage: String = s"Неверный JSON: $reason"

  given JsonEncoder[OrderError] = JsonEncoder[String].contramap(_.getMessage)

  extension (e: OrderError)
    def toHttpStatus: Status = e match
      case _: ValidationError | _: InvalidJson => Status.BadRequest
      case _: ItemOutOfStock                   => Status.Conflict
      case _: PaymentFailed                    => Status.PaymentRequired
      case _: DatabaseError                    => Status.InternalServerError