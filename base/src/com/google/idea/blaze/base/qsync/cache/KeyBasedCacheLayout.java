/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.cache;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.nio.file.Path;
import java.util.Collection;

/**
 * A cache layout that places artifacts in a single directory, naming them as per {@link
 * CacheDirectoryManager#cacheKeyForArtifact(OutputArtifactInfo)}.
 */
public class KeyBasedCacheLayout implements CacheLayout {
  private final Path cacheDirectory;

  public KeyBasedCacheLayout(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
  }

  @Override
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    final Path finalDestination = cacheDirectory.resolve(key);
    return PreparedOutputArtifactDestination.create(finalDestination);
  }

  @Override
  public Collection<Path> getCachePaths() {
    return ImmutableList.of(cacheDirectory);
  }
}
