/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.storage.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.ClientConfigurationFactory;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import org.apache.commons.lang3.StringUtils;
import org.apache.druid.common.aws.AWSClientConfig;
import org.apache.druid.common.aws.AWSEndpointConfig;
import org.apache.druid.common.aws.AWSProxyConfig;
import org.apache.druid.data.SearchableVersionedDataFinder;
import org.apache.druid.guice.Binders;
import org.apache.druid.guice.JsonConfigProvider;
import org.apache.druid.guice.LazySingleton;
import org.apache.druid.initialization.DruidModule;
import org.apache.druid.java.util.common.logger.Logger;

import java.util.List;

/**
 *
 */
public class S3StorageDruidModule implements DruidModule
{
  public static final String SCHEME = "s3";
  public static final String SCHEME_S3N = "s3n";
  public static final String SCHEME_S3_ZIP = "s3_zip";

  private static final Logger log = new Logger(S3StorageDruidModule.class);

  @Override
  public List<? extends Module> getJacksonModules()
  {
    return ImmutableList.of(
        new Module()
        {
          @Override
          public String getModuleName()
          {
            return "DruidS3-" + System.identityHashCode(this);
          }

          @Override
          public Version version()
          {
            return Version.unknownVersion();
          }

          @Override
          public void setupModule(SetupContext context)
          {
            context.registerSubtypes(S3LoadSpec.class);
          }
        }
    );
  }

  @Override
  public void configure(Binder binder)
  {
    MapBinder.newMapBinder(binder, String.class, SearchableVersionedDataFinder.class)
             .addBinding(SCHEME)
             .to(S3TimestampVersionedDataFinder.class)
             .in(LazySingleton.class);
    MapBinder.newMapBinder(binder, String.class, SearchableVersionedDataFinder.class)
             .addBinding(SCHEME_S3N)
             .to(S3TimestampVersionedDataFinder.class)
             .in(LazySingleton.class);
    Binders.dataSegmentKillerBinder(binder)
           .addBinding(SCHEME_S3_ZIP)
           .to(S3DataSegmentKiller.class)
           .in(LazySingleton.class);
    Binders.dataSegmentMoverBinder(binder)
           .addBinding(SCHEME_S3_ZIP)
           .to(S3DataSegmentMover.class)
           .in(LazySingleton.class);
    Binders.dataSegmentArchiverBinder(binder)
           .addBinding(SCHEME_S3_ZIP)
           .to(S3DataSegmentArchiver.class)
           .in(LazySingleton.class);
    Binders.dataSegmentPusherBinder(binder).addBinding(SCHEME).to(S3DataSegmentPusher.class).in(LazySingleton.class);
    JsonConfigProvider.bind(binder, "druid.storage", S3InputDataConfig.class);
    JsonConfigProvider.bind(binder, "druid.storage", S3DataSegmentPusherConfig.class);
    JsonConfigProvider.bind(binder, "druid.storage", S3DataSegmentArchiverConfig.class);
    JsonConfigProvider.bind(binder, "druid.storage", S3StorageConfig.class);
    JsonConfigProvider.bind(binder, "druid.storage.sse.kms", S3SSEKmsConfig.class);
    JsonConfigProvider.bind(binder, "druid.storage.sse.custom", S3SSECustomConfig.class);

    Binders.taskLogsBinder(binder).addBinding(SCHEME).to(S3TaskLogs.class);
    JsonConfigProvider.bind(binder, "druid.indexer.logs", S3TaskLogsConfig.class);
    binder.bind(S3TaskLogs.class).in(LazySingleton.class);
  }

  // This provides ServerSideEncryptingAmazonS3.Builder with default configs from Guice injection initially set.
  // However, this builder can then be modified and have configuration(s) inside
  // AmazonS3ClientBuilder and/or S3StorageConfig overridden before being built.
  @Provides
  public ServerSideEncryptingAmazonS3.Builder getServerSideEncryptingAmazonS3Builder(
      AWSCredentialsProvider provider,
      AWSProxyConfig proxyConfig,
      AWSEndpointConfig endpointConfig,
      AWSClientConfig clientConfig,
      S3StorageConfig storageConfig
  )
  {
    final ClientConfiguration configuration = new ClientConfigurationFactory().getConfig();
    final Protocol protocol = S3Utils.determineProtocol(clientConfig, endpointConfig);
    final AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3Client
        .builder()
        .withCredentials(provider)
        .withClientConfiguration(S3Utils.setProxyConfig(configuration, proxyConfig).withProtocol(protocol))
        .withChunkedEncodingDisabled(clientConfig.isDisableChunkedEncoding())
        .withPathStyleAccessEnabled(clientConfig.isEnablePathStyleAccess())
        .withForceGlobalBucketAccessEnabled(clientConfig.isForceGlobalBucketAccessEnabled());

    if (StringUtils.isNotEmpty(endpointConfig.getUrl())) {
      amazonS3ClientBuilder.setEndpointConfiguration(
          new EndpointConfiguration(endpointConfig.getUrl(), endpointConfig.getSigningRegion())
      );
    }

    return ServerSideEncryptingAmazonS3.builder()
                                       .setAmazonS3ClientBuilder(amazonS3ClientBuilder)
                                       .setS3StorageConfig(storageConfig);

  }

  // This provides ServerSideEncryptingAmazonS3 built with all default configs from Guice injection
  /**
   * Creates {@link ServerSideEncryptingAmazonS3} which may perform config validation immediately.
   * You may want to avoid immediate config validation but defer it until you actually use the s3 client.
   * Use {@link #getAmazonS3ClientSupplier} instead in that case.
   */
  @Provides
  @LazySingleton
  public ServerSideEncryptingAmazonS3 getAmazonS3Client(
      ServerSideEncryptingAmazonS3.Builder serverSideEncryptingAmazonS3Builder
  )
  {
    return serverSideEncryptingAmazonS3Builder.build();
  }

  /**
   * Creates a supplier that lazily initialize {@link ServerSideEncryptingAmazonS3}.
   * You may want to use the supplier to defer config validation until you actually use the s3 client.
   */
  @Provides
  @LazySingleton
  public Supplier<ServerSideEncryptingAmazonS3> getAmazonS3ClientSupplier(
      ServerSideEncryptingAmazonS3.Builder serverSideEncryptingAmazonS3Builder
  )
  {
    return Suppliers.memoize(serverSideEncryptingAmazonS3Builder::build);
  }
}
