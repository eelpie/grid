package com.gu.mediaservice.lib.config

import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

case class GridConfigResources(configuration: Configuration, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle)
