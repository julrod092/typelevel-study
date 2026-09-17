package com.example.events

enum EventHandlerError {
  case LambdaError(message: String)
  case EmptyEvents(message: String)
  case IncorrectEventType(message: String)
  case AttributeError(message: String, eventId: String)
  case EmptyPayload(message: String, eventId: Option[String] = None)
  case PublishError(message: String, eventId: String)
}
