$version: "2"

namespace hello

use alloy#simpleRestJson

@simpleRestJson
service HelloWorldAPI {
  version: "1.0.0",
  operations: [Hello]
}

@readonly
@http(method: "GET", uri: "/hello/{name}", code: 200)
operation Hello {
  input: PersonDTO,
  output: GreetingDTO
}

structure PersonDTO {
  @httpLabel
  @required
  name: String,

  @httpQuery("town")
  town: String
}

structure GreetingDTO {
  @required
  message: String
}
