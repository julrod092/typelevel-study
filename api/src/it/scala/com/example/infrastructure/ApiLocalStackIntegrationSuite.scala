package com.example.infrastructure

import cats.effect.{Clock, IO}
import com.amazonaws.dynamodb.{AttributeValue, StringAttributeValue}
import com.example.domain.repositories.CustomerRecord
import com.example.infrastructure.configuration.{Configuration, PricingEnvironment}
import com.example.support.{DynamoIntegrationSuite, DynamoTestSupport}
import io.circe.Json
import org.http4s.Method.POST
import org.http4s.Status.Ok
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.http4s.Request

object ApiLocalStackIntegrationSuite extends DynamoIntegrationSuite {

  test(
    "A valid public pricing request reads its customer and stores the returned order through real DynamoDB boundaries"
  ) { fixture =>
    val customer = CustomerRecord(
      customerId = "integration-customer",
      tier = "GOLD",
      name = Some("Integration Customer"),
      createdAt = "2026-01-01T00:00:00Z"
    )
    val environment = PricingEnvironment(
      customers = fixture.customers,
      coupons = fixture.coupons,
      orders = fixture.orders,
      clock = fixture.clock,
      idGenerator = fixture.idGenerator
    )
    val requestBody = Json.obj(
      "customerId" -> Json.fromString(customer.customerId),
      "items" -> Json.arr(
        Json.obj(
          "sku" -> Json.fromString("SKU-045"),
          "quantity" -> Json.fromInt(2)
        )
      )
    )

    for {
      _ <- fixture.client.putItem(
        fixture.tables.customersTableName,
        fixture.customers.encodeRecord(customer)
      )
      result <- Configuration.httpApp[IO](environment).use { app =>
        for {
          response <- app.run(
            Request[IO](POST, uri"/orders/price").withEntity(requestBody)
          )
          body <- response.as[Json]
          orderId <- IO.fromOption(body.hcursor.get[String]("orderId").toOption)(
            new IllegalStateException("Successful pricing response did not contain orderId")
          )
          rawOrder <- DynamoTestSupport.getRawItem(
            fixture.client,
            fixture.tables.ordersTableName,
            Map(
              fixture.orders.keyAttribute -> AttributeValue.s(StringAttributeValue(orderId))
            )
          )
        } yield {
          val storedOrder = rawOrder.flatMap(fixture.orders.decodeRecord)

          expect(response.status == Ok) and
            expect(
              body.hcursor.get[String]("customerId").toOption.contains(customer.customerId)
            ) and
            expect(body.hcursor.get[String]("status").toOption.contains("Priced")) and
            expect(decimal(body, "subtotal").contains(BigDecimal("99.98"))) and
            expect(storedOrder.exists(_.orderId == orderId)) and
            expect(storedOrder.exists(_.customerId == customer.customerId)) and
            expect(storedOrder.exists(_.subtotal == BigDecimal("99.98")))
        }
      }
    } yield result
  }

  private def decimal(body: Json, field: String): Option[BigDecimal] =
    body.hcursor.downField(field).focus.flatMap(_.asNumber).flatMap(_.toBigDecimal)
}
