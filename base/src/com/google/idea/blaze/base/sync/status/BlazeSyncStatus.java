/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.status;

import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.project.Project;

/** Interface to tell blaze it might need to resync. */
public interface BlazeSyncStatus {

  /** The current sync status */
  enum SyncStatus {
    FAILED,
    DIRTY,
    CLEAN,
  }

  SyncStatus getStatus();

  static BlazeSyncStatus getInstance(Project project) {
    return project.getService(BlazeSyncStatus.class);
  }

  void syncStarted();

  void syncEnded(SyncMode syncMode, SyncResult syncResult);

  /**
   * @deprecated For query sync, use {@link
   *     com.google.idea.blaze.base.qsync.QuerySyncManager#syncInProgress}
   */
  @Deprecated
  boolean syncInProgress();

  void setDirty();

  boolean isDirty();
}
