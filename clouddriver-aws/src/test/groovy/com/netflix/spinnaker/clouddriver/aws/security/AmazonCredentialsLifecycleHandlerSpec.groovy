/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.aws.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutorService

class AmazonCredentialsLifecycleHandlerSpec extends Specification {
  @Shared
  def awsCleanupProvider = new AwsCleanupProvider()
  @Shared
  def awsInfrastructureProvider = new AwsInfrastructureProvider()
  @Shared
  def awsProvider = new AwsProvider(credentialsRepository)
  @Shared
  def objectMapper = new ObjectMapper()
  @Shared
  def credOne = TestCredential.named('one')
  @Shared
  def credTwo = TestCredential.named('two')
  @Shared
  def credentialsRepository = Mock(CredentialsRepository) {
    getAll() >> [credOne, credTwo]
  }


  def 'it should replace current public image caching agent'() {
    def imageCachingAgentOne = new ImageCachingAgent(null, credOne, "us-east-1", objectMapper, null, true, null)
    def imageCachingAgentTwo = new ImageCachingAgent(null, credTwo, "us-east-1", objectMapper, null, false, null)
    def awsProvider = new AwsProvider(credentialsRepository)
    awsProvider.addAgents([imageCachingAgentOne, imageCachingAgentTwo])
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      null, null, null, null, null, null, null, null, null, null, null, null, null, null,
      credentialsRepository)

    when:
    handler.credentialsDeleted(credOne)

    then:
    imageCachingAgentTwo.includePublicImages
  }

  def 'it should remove region not used by public image caching agent'() {
    def imageCachingAgentOne = new ImageCachingAgent(null, credOne, "us-west-2", objectMapper, null, true, null)
    def imageCachingAgentTwo = new ImageCachingAgent(null, credTwo, "us-east-1", objectMapper, null, false, null)
    def awsProvider = new AwsProvider(credentialsRepository)
    awsProvider.addAgents([imageCachingAgentOne, imageCachingAgentTwo])
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      null, null, null, null, null, null, null, null, null, null, null, null, null, null,
      credentialsRepository)
    handler.publicRegions.add("us-west-2")

    when:
    handler.credentialsDeleted(credOne)

    then:
    !handler.publicRegions.contains("us-west-2")
  }

  def 'it should add agents'() {
    def awsProvider = new AwsProvider(credentialsRepository)
    def awsInfrastructureProvider = new AwsInfrastructureProvider()
    def awsCleanupProvider = new AwsCleanupProvider()
    def amazonCloudProvider = new AmazonCloudProvider()
    def registry = new DefaultRegistry()
    def eddaApiFactory = new EddaApiFactory()
    def dynamicConfigService = Mock(DynamicConfigService) {
      isEnabled("aws.features.cloud-formation", false) >> false
      isEnabled("aws.features.launch-templates", false) >> false
    }
    Optional<ExecutorService> reservationReportPool = Optional.empty()
    Optional<Collection<AgentProvider>> agentProviders = Optional.empty()
    def deployDefaults = new  AwsConfiguration.DeployDefaults()
    def handler = new AmazonCredentialsLifecycleHandler(awsCleanupProvider, awsInfrastructureProvider, awsProvider,
      amazonCloudProvider, null, null, null, objectMapper, null, eddaApiFactory, null, registry, reservationReportPool, agentProviders, null, dynamicConfigService, deployDefaults,
      credentialsRepository)
    def credThree = TestCredential.named('three')

    when:
    handler.credentialsAdded(credThree)

    then:
    awsInfrastructureProvider.getAgents().size() == 12
    awsProvider.getAgents().size() == 18
    handler.publicRegions.size() == 2
    handler.awsInfraRegions.size() == 2
  }
}