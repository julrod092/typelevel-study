package com.example.events

import com.example.events.EventHandlerError.*
import com.example.generators.{DomainGenerators, EventGenerators}
import com.example.generators.EventGenerators.given
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object EventResultSuite extends SimpleIOSuite with Checkers {

  test("Record errors preserve their identifier while batch errors have no record identifier") {
    forall(DomainGenerators.nonEmptyString) { id =>
      val cases: List[(EventHandlerError, Option[String])] = List(
        LambdaError("batch failed") -> None,
        EmptyEvents("empty batch") -> None,
        IncorrectEventType("unsupported event") -> None,
        AttributeError("missing field", id) -> Some(id),
        EmptyPayload("missing record") -> None,
        EmptyPayload("missing image", Some(id)) -> Some(id),
        PublishError("publish failed", id) -> Some(id)
      )

      expect(cases.forall { case (error, eventId) =>
        EventResult.fromError(error) == FailedEventDetail(eventId, error)
      })
    }
  }

  test("An empty result is the left and right identity when combining batches") {
    forall(EventGenerators.eventResult) { result =>
      expect(EventResult.reduceBatch(EventResult.empty, result) == result) and
        expect(EventResult.reduceBatch(result, EventResult.empty) == result)
    }
  }

  test("Combining batches adds their counts and preserves every success and failure in order") {
    val scenario = for {
      first <- EventGenerators.eventResult
      second <- EventGenerators.eventResult
    } yield (first, second)

    forall(scenario) { case (first, second) =>
      val combined = EventResult.reduceBatch(first, second)

      expect(combined.totalRecords == first.totalRecords + second.totalRecords) and
        expect(combined.successes == first.successes ++ second.successes) and
        expect(combined.failures == first.failures ++ second.failures)
    }
  }

  test("Changing the grouping of consecutive batches does not change their combined result") {
    val scenario = for {
      first <- EventGenerators.eventResult
      second <- EventGenerators.eventResult
      third <- EventGenerators.eventResult
    } yield (first, second, third)

    forall(scenario) { case (first, second, third) =>
      expect(
        EventResult.reduceBatch(EventResult.reduceBatch(first, second), third) ==
          EventResult.reduceBatch(first, EventResult.reduceBatch(second, third))
      )
    }
  }

  test("Combining an error-only result preserves the supplied count rather than counting details") {
    val scenario = for {
      batch <- EventGenerators.eventResult
      error <- EventGenerators.errorWithId
    } yield (batch, error)

    forall(scenario) { case (batch, (error, id)) =>
      val errorsOnly = EventResult.fromOnlyErrors(error)
      val combined = EventResult.reduceBatch(batch, errorsOnly)
      val reversed = EventResult.reduceBatch(errorsOnly, batch)
      val detail = FailedEventDetail(id, error)

      expect(combined.totalRecords == batch.totalRecords) and
        expect(reversed.totalRecords == batch.totalRecords) and
        expect(combined.successes == batch.successes) and
        expect(reversed.successes == batch.successes) and
        expect(combined.failures == batch.failures :+ detail) and
        expect(reversed.failures == detail :: batch.failures)
    }
  }

  test(
    "An error-only result retains all error details without inventing successes or a record count"
  ) {
    val scenario = Gen.choose(0, 8).flatMap(size => Gen.listOfN(size, EventGenerators.errorWithId))

    forall(scenario) { errors =>
      val result = EventResult.fromOnlyErrors(errors.map(_._1)*)
      val expected = errors.map { case (error, id) => FailedEventDetail(id, error) }

      expect(result.failures == expected) and
        expect(result.successes.isEmpty) and
        expect(result.totalRecords == 0)
    }
  }
}
