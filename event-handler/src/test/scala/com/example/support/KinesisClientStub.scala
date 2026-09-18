package com.example.support

import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.{PutRecordRequest, PutRecordResponse}

import java.util.concurrent.CompletableFuture

object KinesisClientStub {

  def apply(
      putRecordResult: PutRecordRequest => CompletableFuture[PutRecordResponse]
  ): KinesisAsyncClient = new KinesisAsyncClient {
    override def serviceName(): String = "kinesis"

    override def putRecord(request: PutRecordRequest): CompletableFuture[PutRecordResponse] =
      putRecordResult(request)

    override def close(): Unit = ()
  }
}
