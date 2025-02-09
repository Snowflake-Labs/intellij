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
package com.google.idea.blaze.base.qsync;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.BlazeProjectSnapshotBuilder;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encapsulates a loaded querysync project and it's dependencies.
 *
 * <p>This class also maintains a {@link QuerySyncProjectData} instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
public class QuerySyncProject {

  private final Path snapshotFilePath;
  private final Project project;
  private final BlazeProject snapshotHolder;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final ArtifactTracker artifactTracker;
  private final RenderJarArtifactTracker renderJarArtifactTracker;
  private final AppInspectorArtifactTracker appInspectorArtifactTracker;
  private final DependencyTracker dependencyTracker;
  private final RenderJarTracker renderJarTracker;
  private final AppInspectorTracker appInspectorTracker;
  private final ProjectQuerier projectQuerier;
  private final BlazeProjectSnapshotBuilder blazeProjectSnapshotBuilder;
  private final ProjectDefinition projectDefinition;
  private final ProjectViewSet projectViewSet;
  // TODO(mathewi) only one of these two should strictly be necessary:
  private final WorkspacePathResolver workspacePathResolver;
  private final ProjectPath.Resolver projectPathResolver;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final QuerySyncSourceToTargetMap sourceToTargetMap;

  private final ProjectViewManager projectViewManager;
  private final BuildSystem buildSystem;

  private volatile QuerySyncProjectData projectData;

  public QuerySyncProject(
      Project project,
      Path snapshotFilePath,
      BlazeProject snapshotHolder,
      BlazeImportSettings importSettings,
      WorkspaceRoot workspaceRoot,
      ArtifactTracker artifactTracker,
      RenderJarArtifactTracker renderJarArtifactTracker,
      AppInspectorArtifactTracker appInspectorArtifactTracker,
      DependencyTracker dependencyTracker,
      RenderJarTracker renderJarTracker,
      AppInspectorTracker appInspectorTracker,
      ProjectQuerier projectQuerier,
      BlazeProjectSnapshotBuilder blazeProjectSnapshotBuilder,
      ProjectDefinition projectDefinition,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver workspacePathResolver,
      ProjectPath.Resolver projectPathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      QuerySyncSourceToTargetMap sourceToTargetMap,
      ProjectViewManager projectViewManager,
      BuildSystem buildSystem) {
    this.project = project;
    this.snapshotFilePath = snapshotFilePath;
    this.snapshotHolder = snapshotHolder;
    this.importSettings = importSettings;
    this.workspaceRoot = workspaceRoot;
    this.artifactTracker = artifactTracker;
    this.renderJarArtifactTracker = renderJarArtifactTracker;
    this.appInspectorArtifactTracker = appInspectorArtifactTracker;
    this.dependencyTracker = dependencyTracker;
    this.renderJarTracker = renderJarTracker;
    this.appInspectorTracker = appInspectorTracker;
    this.projectQuerier = projectQuerier;
    this.blazeProjectSnapshotBuilder = blazeProjectSnapshotBuilder;
    this.projectDefinition = projectDefinition;
    this.projectViewSet = projectViewSet;
    this.workspacePathResolver = workspacePathResolver;
    this.projectPathResolver = projectPathResolver;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.sourceToTargetMap = sourceToTargetMap;
    this.projectViewManager = projectViewManager;
    this.buildSystem = buildSystem;
    projectData = new QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings);
  }

  public Project getIdeProject() {
    return project;
  }

  public BlazeImportSettings getImportSettings() {
    return importSettings;
  }

  public ProjectViewSet getProjectViewSet() {
    return projectViewSet;
  }

  public WorkspaceRoot getWorkspaceRoot() {
    return workspaceRoot;
  }

  public BlazeProject getSnapshotHolder() {
    return snapshotHolder;
  }

  public QuerySyncProjectData getProjectData() {
    return projectData;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public ProjectPath.Resolver getProjectPathResolver() {
    return projectPathResolver;
  }

  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  public ArtifactTracker getArtifactTracker() {
    return artifactTracker;
  }

  public RenderJarArtifactTracker getRenderJarArtifactTracker() {
    return renderJarArtifactTracker;
  }

  public AppInspectorArtifactTracker getAppInspectorArtifactTracker() {
    return appInspectorArtifactTracker;
  }

  public SourceToTargetMap getSourceToTargetMap() {
    return sourceToTargetMap;
  }

  public ProjectDefinition getProjectDefinition() {
    return projectDefinition;
  }

  public BuildSystem getBuildSystem() {
    return buildSystem;
  }

  public void fullSync(BlazeContext context) throws BuildException {
    sync(context, Optional.empty());
  }

  public void deltaSync(BlazeContext context) throws BuildException {
    syncWithCurrentSnapshot(context);
  }

  private void syncWithCurrentSnapshot(BlazeContext context) throws BuildException {
    sync(context, snapshotHolder.getCurrent().map(BlazeProjectSnapshot::queryData));
  }

  public void sync(BlazeContext parentContext, Optional<PostQuerySyncData> lastQuery)
      throws BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new SyncQueryStatsScope());
      try {
        SaveUtil.saveAllFiles();
        PostQuerySyncData postQuerySyncData =
            lastQuery.isEmpty()
                ? projectQuerier.fullQuery(projectDefinition, context)
                : projectQuerier.update(projectDefinition, lastQuery.get(), context);
        BlazeProjectSnapshot newSnapshot =
            blazeProjectSnapshotBuilder.createBlazeProjectSnapshot(context, postQuerySyncData);
        onNewSnapshot(context, newSnapshot);

        // TODO: Revisit SyncListeners once we switch fully to qsync
        for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
          // A callback shared between the old and query sync implementations.
          syncListener.onSyncComplete(
              project,
              context,
              importSettings,
              projectViewSet,
              ImmutableSet.of(),
              projectData,
              SyncMode.FULL,
              SyncResult.SUCCESS);
        }
      } catch (IOException e) {
        throw new BuildException(e);
      } finally {
        for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
          // A query sync specific callback.
          syncListener.afterQuerySync(project, context);
        }
      }
    }
  }

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a
   *     source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   *     file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   *     the set of all targets defined in all build packages within the directory (recursively).
   */
  public TargetsToBuild getProjectTargets(BlazeContext context, Path workspaceRelativePath) {
    return dependencyTracker.getProjectTargets(context, workspaceRelativePath);
  }

  /** Returns the set of targets with direct dependencies on {@code targets}. */
  public ImmutableSet<Label> getTargetsDependingOn(Set<Label> targets) {
    BlazeProjectSnapshot snapshot = snapshotHolder.getCurrent().orElseThrow();
    return snapshot.graph().getSameLanguageTargetsDependingOn(targets);
  }

  /** Returns workspace-relative paths of modified files, according to the VCS */
  public ImmutableSet<Path> getWorkingSet(BlazeContext context) throws BuildException {
    SaveUtil.saveAllFiles();
    VcsState vcsState;
    Optional<VcsState> computed = projectQuerier.getVcsState(context);
    if (computed.isPresent()) {
      vcsState = computed.get();
    } else {
      context.output(new PrintOutput("Failed to compute working set. Falling back on sync data"));
      BlazeProjectSnapshot snapshot = snapshotHolder.getCurrent().orElseThrow();
      vcsState =
          snapshot
              .queryData()
              .vcsState()
              .orElseThrow(
                  () -> new BuildException("No VCS state, cannot calculate affected targets"));
    }
    return vcsState.modifiedFiles();
  }

  public void build(BlazeContext parentContext, Set<Label> projectTargets)
      throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      if (getDependencyTracker().buildDependenciesForTargets(context, projectTargets)) {
        BlazeProjectSnapshot newSnapshot =
            blazeProjectSnapshotBuilder.createBlazeProjectSnapshot(
                context, snapshotHolder.getCurrent().orElseThrow().queryData());
        onNewSnapshot(context, newSnapshot);
      }
    }
  }

  public void buildRenderJar(BlazeContext parentContext, List<Path> wps)
      throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      renderJarTracker.buildRenderJarForFile(context, wps);
    }
  }

  public ImmutableCollection<Path> buildAppInspector(
      BlazeContext parentContext, List<Label> inspectors) throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      return appInspectorTracker.buildAppInspector(context, inspectors);
    }
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  public void enableAnalysis(BlazeContext context, Set<Label> projectTargets)
      throws BuildException {
    try {
      context.output(
          PrintOutput.output(
              "Building dependencies for:\n  " + Joiner.on("\n  ").join(projectTargets)));
      build(context, projectTargets);
    } catch (IOException e) {
      throw new BuildException("Failed to build dependencies", e);
    }
  }

  public boolean canEnableAnalysisFor(Path workspacePath) {
    return !getProjectTargets(BlazeContext.create(), workspacePath).isEmpty();
  }

  public void enableRenderJar(BlazeContext context, PsiFile psiFile) throws BuildException {
    try {
      Path path = Paths.get(psiFile.getVirtualFile().getPath());
      String rel = workspaceRoot.path().relativize(path).toString();
      buildRenderJar(context, ImmutableList.of(WorkspacePath.createIfValid(rel).asPath()));
    } catch (IOException e) {
      throw new BuildException("Failed to build render jar", e);
    }
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    Path p = virtualFile.getFileSystem().getNioPath(virtualFile);
    if (p == null || !p.startsWith(workspaceRoot.path())) {
      // Not in the workspace.
      // p == null can occur if the file is a zip entry.
      return true;
    }
    Set<Label> pendingTargets =
        dependencyTracker.getPendingTargets(workspaceRoot.relativize(virtualFile));
    return pendingTargets.isEmpty();
  }

  /**
   * Reloads the project view and checks it against the stored {@link ProjectDefinition}.
   *
   * @return true if the stored {@link ProjectDefinition} matches that derived from the {@link
   *     ProjectViewSet}
   */
  public boolean isDefinitionCurrent(BlazeContext context) throws BuildException {
    ProjectViewSet projectViewSet =
        projectViewManager.reloadProjectView(context, workspacePathResolver);
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    ImmutableSet<String> testSourceGlobs =
        projectViewSet.listItems(TestSourceSection.KEY).stream()
            .map(Glob::toString)
            .collect(ImmutableSet.toImmutableSet());
    ProjectDefinition projectDefinition =
        ProjectDefinition.create(
            importRoots.rootPaths(),
            importRoots.excludePaths(),
            LanguageClasses.toQuerySync(workspaceLanguageSettings.getActiveLanguages()),
            testSourceGlobs);

    return this.projectDefinition.equals(projectDefinition);
  }

  public Optional<PostQuerySyncData> readSnapshotFromDisk(BlazeContext context) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.exists()) {
      return Optional.empty();
    }
    try (InputStream in = new GZIPInputStream(new FileInputStream(f))) {
      return new SnapshotDeserializer()
          .readFrom(in, context)
          .map(SnapshotDeserializer::getSyncData);
    }
  }

  /** Returns true if {@code absolutePath} is in a project include */
  public boolean containsPath(Path absolutePath) {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false;
    }
    Path workspaceRelative = workspaceRoot.path().relativize(absolutePath);
    return projectDefinition.isIncluded(workspaceRelative);
  }

  /**
   * Returns true if {@code absolutePath} is specified in a project exclude.
   *
   * <p>A path not added or excluded the project definition will return false for both {@code
   * containsPath} and {@code explicitlyExcludesPath}
   */
  public boolean explicitlyExcludesPath(Path absolutePath) {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false;
    }
    Path workspaceRelative = workspaceRoot.path().relativize(absolutePath);
    return projectDefinition.isExcluded(workspaceRelative);
  }

  /** Returns all external dependencies of a given label */
  public ImmutableSet<Label> externalDependenciesFor(Label label) {
    return snapshotHolder
        .getCurrent()
        .map(BlazeProjectSnapshot::graph)
        .map(graph -> graph.getTransitiveExternalDependencies(label))
        .orElse(ImmutableSet.of());
  }

  private void writeToDisk(BlazeProjectSnapshot snapshot) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.getParentFile().exists()) {
      if (!f.getParentFile().mkdirs()) {
        throw new IOException("Cannot create directory " + f.getParent());
      }
    }
    try (OutputStream o = new GZIPOutputStream(new FileOutputStream(f))) {
      new SnapshotSerializer().visit(snapshot.queryData()).toProto().writeTo(o);
    }
  }

  private void onNewSnapshot(BlazeContext context, BlazeProjectSnapshot newSnapshot)
      throws IOException {
    snapshotHolder.setCurrent(context, newSnapshot);
    projectData = projectData.withSnapshot(newSnapshot);
    writeToDisk(newSnapshot);
  }

  public Iterable<Path> getBugreportFiles() {
    return ImmutableList.<Path>builder()
        .add(snapshotFilePath)
        .addAll(artifactTracker.getBugreportFiles())
        .build();
  }
}
