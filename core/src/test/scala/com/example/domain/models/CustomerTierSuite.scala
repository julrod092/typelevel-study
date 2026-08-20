package com.example.domain.models

import cats.effect.IO
import weaver.SimpleIOSuite

object CustomerTierSuite extends SimpleIOSuite {

  test("Coupon eligibility excludes BASIC customers while permitting SILVER and GOLD customers") {
    IO.pure(
      expect(!CustomerTier.BASIC.isApplicable) and
        expect(CustomerTier.SILVER.isApplicable) and
        expect(CustomerTier.GOLD.isApplicable)
    )
  }
}
