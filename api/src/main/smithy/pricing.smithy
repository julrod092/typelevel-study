$version: "2"

namespace pricing

use alloy#simpleRestJson
use smithy4s.meta#packedInputs

@simpleRestJson
@packedInputs
service PriceAPI {
  version: "1.0.0",
  operations: [OrderPricing]
  errors: [ValidationErrors, NotFoundError, InternalServerError]
}

@http(method: "POST", uri: "/orders/price", code: 200)
operation OrderPricing {
  input: OrderPriceDTO,
  output: OrderPricedDTO
}

structure LineItemDTO {
  @required
  sku: String,
  @required
  quantity: Integer,
  unitPrice: BigDecimal,
  lineTotal: BigDecimal
}

list LineItemsDTO {
  member: LineItemDTO
}

structure OrderPriceDTO {
  @required
  customerId: String,
  @required
  items: LineItemsDTO
  couponCode: String
}

structure OrderPricedDTO {
  @required
  orderId: String,
  @required
  customerId: String,
  @required
  status: String,
  @required
  items: LineItemsDTO,
  @required
  subtotal: BigDecimal,
  @required
  discountAmount: BigDecimal,
  @required
  total: BigDecimal,
  couponApplied: String,
  @required
  createdAt: String,
  @required
  updatedAt: String
}

structure Error {
  @required
  field: String,
  @required
  message: String
}

list Errors {
  member: Error
}

@error("server")
@httpError(422)
structure ValidationErrors {
  @required
  code: String,
  @required
  errors: Errors
}

@error("server")
@httpError(404)
structure NotFoundError {
  code: String,
  message: String
}

@error("server")
@httpError(500)
structure InternalServerError {
  code: String,
  message: String
}
