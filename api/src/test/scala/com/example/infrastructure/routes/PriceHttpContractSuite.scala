package com.example.infrastructure.routes

import cats.effect.IO
import com.example.domain.repositories.{CouponRecord, CustomerRecord, LineItemRecord, OrderRecord}
import com.example.infrastructure.configuration.Configuration
import com.example.support.{PricingEnvironmentStub, RepositoryCalls}
import fs2.text
import io.circe.Json
import org.http4s.Method.POST
import org.http4s.Status.{BadRequest, InternalServerError, NotFound, Ok, UnprocessableEntity}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.http4s.{Request, Response}
import weaver.SimpleIOSuite

object PriceHttpContractSuite extends SimpleIOSuite {

  private val customer = CustomerRecord(
    customerId = "customer-123",
    tier = "GOLD",
    name = Some("Test Customer"),
    createdAt = "2026-01-01T00:00:00Z"
  )

  test(
    "When a known customer submits a valid catalog order, the public endpoint returns the complete response and persists the same order"
  ) {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = Some(customer))
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(priceRequest(customer.customerId))
          body <- response.as[Json]
          calls <- stub.calls.get
        } yield {
          val orderId = body.hcursor.get[String]("orderId").toOption
          val createdAt = body.hcursor.get[String]("createdAt").toOption
          val updatedAt = body.hcursor.get[String]("updatedAt").toOption
          val responseItems = body.hcursor.get[List[Json]]("items").toOption.getOrElse(Nil)
          val expectedSavedOrder = for {
            id <- orderId
            created <- createdAt
            updated <- updatedAt
          } yield OrderRecord(
            orderId = id,
            customerId = customer.customerId,
            status = "Priced",
            items = List(LineItemRecord("SKU-001", 2, BigDecimal("19.99"), BigDecimal("39.98"))),
            subtotal = BigDecimal("39.98"),
            discountAmount = BigDecimal(0),
            total = BigDecimal("39.98"),
            couponCode = None,
            createdAt = created,
            updatedAt = updated
          )

          expect(response.status == Ok) and
            expect(
              body.hcursor.get[String]("customerId").toOption.contains(customer.customerId)
            ) and
            expect(body.hcursor.get[String]("status").toOption.contains("Priced")) and
            expect(decimal(body, "subtotal").contains(BigDecimal("39.98"))) and
            expect(decimal(body, "discountAmount").contains(BigDecimal(0))) and
            expect(decimal(body, "total").contains(BigDecimal("39.98"))) and
            expect(responseItems.size == 1) and
            expect(
              responseItems.headOption
                .flatMap(_.hcursor.get[String]("sku").toOption)
                .contains("SKU-001")
            ) and
            expect(
              responseItems.headOption.flatMap(_.hcursor.get[Int]("quantity").toOption).contains(2)
            ) and
            expect(
              responseItems.headOption
                .flatMap(item => decimal(item, "unitPrice"))
                .contains(BigDecimal("19.99"))
            ) and
            expect(
              responseItems.headOption
                .flatMap(item => decimal(item, "lineTotal"))
                .contains(BigDecimal("39.98"))
            ) and
            expect(body.hcursor.downField("couponApplied").focus.isEmpty) and
            expect(orderId.exists(_.nonEmpty)) and
            expect(createdAt.exists(_.nonEmpty)) and
            expect(updatedAt == createdAt) and
            expect(calls.customerIds.map(_.value) == List(customer.customerId)) and
            expect(calls.couponCodes.isEmpty) and
            expect(calls.savedOrders == expectedSavedOrder.toList)
        }
      }
    } yield result
  }

  test(
    "When a qualified customer supplies an existing coupon, the public endpoint applies and persists the complete discount contract"
  ) {
    val coupon = CouponRecord(
      code = "SAVE10",
      discountPercent = 10,
      minOrderAmount = BigDecimal(0),
      usageLimit = 10,
      usageCount = 0,
      expiresAt = "2100-01-01T00:00:00Z",
      stackableWithTiers = true
    )

    for {
      stub <- PricingEnvironmentStub.create(
        customerResult = Some(customer),
        couponResult = Some(coupon)
      )
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(priceRequest(customer.customerId, Some(coupon.code)))
          body <- response.as[Json]
          calls <- stub.calls.get
        } yield {
          val savedOrder = calls.savedOrders.headOption

          expect(response.status == Ok) and
            expect(decimal(body, "subtotal").contains(BigDecimal("39.98"))) and
            expect(decimal(body, "discountAmount").contains(BigDecimal("3.998"))) and
            expect(decimal(body, "total").contains(BigDecimal("35.982"))) and
            expect(body.hcursor.get[String]("couponApplied").toOption.contains(coupon.code)) and
            expect(calls.customerIds.map(_.value) == List(customer.customerId)) and
            expect(calls.couponCodes.map(_.value) == List(coupon.code)) and
            expect(calls.savedOrders.size == 1) and
            expect(savedOrder.flatMap(_.couponCode).contains(coupon.code)) and
            expect(savedOrder.exists(_.discountAmount == BigDecimal("3.998"))) and
            expect(savedOrder.exists(_.total == BigDecimal("35.982")))
        }
      }
    } yield result
  }

  test(
    "When request validation fails, the public endpoint returns the ordered 422 contract without invoking repositories"
  ) {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = Some(customer))
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        val body = Json.obj(
          "customerId" -> Json.fromString(""),
          "items" -> Json.arr(
            Json.obj(
              "sku" -> Json.fromString(""),
              "quantity" -> Json.fromInt(0)
            )
          ),
          "couponCode" -> Json.fromString("")
        )

        for {
          response <- app.run(Request[IO](POST, uri"/orders/price").withEntity(body))
          responseBody <- response.as[Json]
          calls <- stub.calls.get
        } yield expect(response.status == UnprocessableEntity) and
          expect(
            validationCodes(responseBody) == List(
              "EMPTY_CUSTOMER_ID",
              "EMPTY_ITEM_SKU",
              "INVALID_ITEM_QUANITY",
              "EMPTY_COUPON_CODE"
            )
          ) and
          expect(calls == RepositoryCalls(Nil, Nil, Nil))
      }
    } yield result
  }

  test(
    "When the requested customer does not exist, the public endpoint returns 404 without looking up coupons or saving an order"
  ) {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = None)
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(priceRequest(customer.customerId))
          body <- response.as[Json]
          calls <- stub.calls.get
        } yield expect(response.status == NotFound) and
          expect(body.hcursor.get[String]("code").toOption.contains("NOT_FOUND")) and
          expect(calls.customerIds.map(_.value) == List(customer.customerId)) and
          expect(calls.couponCodes.isEmpty) and
          expect(calls.savedOrders.isEmpty)
      }
    } yield result
  }

  test(
    "When a requested coupon does not exist, the public endpoint returns 404 after lookup without saving an order"
  ) {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = Some(customer), couponResult = None)
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(priceRequest(customer.customerId, Some("SAVE10")))
          body <- response.as[Json]
          calls <- stub.calls.get
        } yield expect(response.status == NotFound) and
          expect(body.hcursor.get[String]("code").toOption.contains("NOT_FOUND")) and
          expect(calls.customerIds.map(_.value) == List(customer.customerId)) and
          expect(calls.couponCodes.map(_.value) == List("SAVE10")) and
          expect(calls.savedOrders.isEmpty)
      }
    } yield result
  }

  test("When saving a priced order fails, the public endpoint returns the modeled 500 error") {
    for {
      stub <- PricingEnvironmentStub.create(
        customerResult = Some(customer),
        saveError = Some(new RuntimeException("DynamoDB unavailable"))
      )
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(priceRequest(customer.customerId))
          body <- response.as[Json]
          calls <- stub.calls.get
        } yield expect(response.status == InternalServerError) and
          expect(body.hcursor.get[String]("code").toOption.contains("500")) and
          expect(
            body.hcursor
              .get[String]("message")
              .toOption
              .contains("Error storing order, please try again later.")
          ) and
          expect(calls.savedOrders.size == 1)
      }
    } yield result
  }

  test(
    "When the request violates the Smithy JSON contract, protocol decoding rejects it without invoking application repositories"
  ) {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = Some(customer))
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        val malformedBody = Json.obj("customerId" -> Json.fromString(customer.customerId))

        for {
          response <- app.run(Request[IO](POST, uri"/orders/price").withEntity(malformedBody))
          _ <- response.body.compile.drain
          calls <- stub.calls.get
        } yield expect(response.status == BadRequest) and
          expect(calls == RepositoryCalls(Nil, Nil, Nil))
      }
    } yield result
  }

  test("The generated API documentation exposes the public order-pricing operation") {
    for {
      stub <- PricingEnvironmentStub.create(customerResult = Some(customer))
      result <- Configuration.httpApp[IO](stub.environment).use { app =>
        for {
          response <- app.run(Request[IO](uri = uri"/docs/specs/pricing.PriceAPI.json"))
          body <- response.body.through(text.utf8.decode).compile.string
        } yield expect(response.status == Ok) and
          expect(body.contains("/orders/price")) and
          expect(body.contains("post"))
      }
    } yield result
  }

  private def priceRequest(customerId: String, couponCode: Option[String] = None): Request[IO] = {
    val base = Json.obj(
      "customerId" -> Json.fromString(customerId),
      "items" -> Json.arr(
        Json.obj(
          "sku" -> Json.fromString("SKU-001"),
          "quantity" -> Json.fromInt(2)
        )
      )
    )
    val body =
      couponCode.fold(base)(code => base.deepMerge(Json.obj("couponCode" -> Json.fromString(code))))

    Request[IO](POST, uri"/orders/price").withEntity(body)
  }

  private def decimal(body: Json, field: String): Option[BigDecimal] =
    body.hcursor.downField(field).focus.flatMap(_.asNumber).flatMap(_.toBigDecimal)

  private def validationCodes(body: Json): List[String] =
    body.hcursor
      .get[List[Json]]("errors")
      .toOption
      .getOrElse(Nil)
      .flatMap(_.asObject.toList.flatMap(_.values))
      .flatMap(_.hcursor.get[String]("errorCode").toOption)
}
