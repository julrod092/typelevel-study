$version: "2"

namespace pricing

use alloy#simpleRestJson
use smithy4s.meta#packedInputs

@simpleRestJson
@packedInputs
service PriceAPI {
  version: "1.0.0",
  operations: [GetPrice]
}

@http(method: "POST", uri: "/orders/price", code: 200)
operation GetPrice {
  input: OrderPriceDTO,
  output: OrderPricedDTO
}


structure ItemDTO {
  @required
  sku: String,
  @required
  quantity: Integer,
  unitPrice: BigDecimal,
  lineTotal: BigDecimal
}

list ItemsDTO {
  member: ItemDTO
}

structure OrderPriceDTO {
  @required
  customerId: String,
  @required
  member: ItemsDTO
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
  items: ItemsDTO,
  @required
  subtotal: BigDecimal,
  @required
  discountAmount: BigDecimal,
  @required
  total: BigDecimal,
  @required
  couponApplied: String,
  @required
  createdAt: Timestamp
}
