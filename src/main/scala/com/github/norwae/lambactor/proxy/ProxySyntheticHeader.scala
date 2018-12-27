package com.github.norwae.lambactor.proxy

import akka.http.scaladsl.model.HttpHeader

abstract class ProxySyntheticHeader extends HttpHeader {
  override val name = getClass.getName
  override val lowercaseName: String = name.toLowerCase
  override def value = s"$key: $keyValue"
  def key: String
  def keyValue: String

  override def renderInRequests(): Boolean = false
  override def renderInResponses(): Boolean = false

  def render[A <: AnyRef](r: A): r.type =
    throw new UnsupportedOperationException("Not rendered in either request or response")

  override def toString(): String = s"$name($key, $keyValue)"
}
