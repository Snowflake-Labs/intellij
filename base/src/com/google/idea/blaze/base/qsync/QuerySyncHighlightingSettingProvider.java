package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuerySyncHighlightingSettingProvider extends DefaultHighlightingSettingProvider {

  @Override
  public @Nullable FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
    final var psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC) {
      if (!QuerySyncManager.getInstance(project).isReadyForAnalysis(psiFile)) {
        return FileHighlightingSetting.ESSENTIAL;
      } else {
        return FileHighlightingSetting.FORCE_HIGHLIGHTING;
      }
    }

    return null;
  }
}
