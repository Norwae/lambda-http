package com.github.norwae.lambactor.proxy

import akka.http.scaladsl.model.HttpHeader

case class StageVariable(key: String, keyValue: String) extends ProxySyntheticHeader