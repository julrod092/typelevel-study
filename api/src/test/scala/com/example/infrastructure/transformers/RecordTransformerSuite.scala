package com.example.infrastructure.transformers

import com.example.domain.models.{Coupon, Customer, LineItem, Order}
import com.example.domain.repositories.{CouponRecord, CustomerRecord, LineItemRecord, OrderRecord}
import com.example.generators.{DomainGenerators, RecordGenerators}
import com.example.generators.DomainGenerators.given
import com.example.generators.RecordGenerators.given
import com.example.infrastructure.transformers.RecordTransformer.given
import io.scalaland.chimney.dsl.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object RecordTransformerSuite extends SimpleIOSuite with Checkers {

  test("line-item records survive a domain round trip") {
    forall(RecordGenerators.lineItemRecord) { record =>
      expect(record.transformInto[LineItem].transformInto[LineItemRecord] == record)
    }
  }

  test("domain line items survive a record round trip") {
    forall(DomainGenerators.lineItem) { lineItem =>
      expect(lineItem.transformInto[LineItemRecord].transformInto[LineItem] == lineItem)
    }
  }

  test("customer records survive a domain round trip") {
    forall(RecordGenerators.customerRecord) { record =>
      expect(record.transformInto[Customer].transformInto[CustomerRecord] == record)
    }
  }

  test("domain customers survive a record round trip") {
    forall(DomainGenerators.customer) { customer =>
      expect(customer.transformInto[CustomerRecord].transformInto[Customer] == customer)
    }
  }

  test("coupon records survive a domain round trip") {
    forall(RecordGenerators.couponRecord) { record =>
      expect(record.transformInto[Coupon].transformInto[CouponRecord] == record)
    }
  }

  test("domain coupons survive a record round trip") {
    forall(DomainGenerators.coupon) { coupon =>
      expect(coupon.transformInto[CouponRecord].transformInto[Coupon] == coupon)
    }
  }

  test("order records survive a domain round trip") {
    forall(RecordGenerators.orderRecord) { record =>
      expect(record.transformInto[Order].transformInto[OrderRecord] == record)
    }
  }

  test("domain orders survive a record round trip") {
    forall(DomainGenerators.order) { order =>
      expect(order.transformInto[OrderRecord].transformInto[Order] == order)
    }
  }
}
