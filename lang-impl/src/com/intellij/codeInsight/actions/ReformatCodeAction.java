package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class ReformatCodeAction extends AnAction {
  private static final @NonNls String HELP_ID = "editing.codeReformatting";

  public void actionPerformed(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final VirtualFile[] files = (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);

    PsiFile file = null;
    final PsiDirectory dir;
    boolean hasSelection = false;

    if (editor != null){
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      dir = file.getContainingDirectory();
      hasSelection = editor.getSelectionModel().hasSelection();
    }
    else if (areFiles(files)) {
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
      if (!operationStatus.hasReadonlyFiles()) {
        final ReformatFilesDialog reformatFilesDialog = new ReformatFilesDialog(project);
        reformatFilesDialog.show();
        if (!reformatFilesDialog.isOK()) return;
        if (reformatFilesDialog.optimizeImports()) {
          new ReformatAndOptimizeImportsProcessor(project, convertToPsiFiles(files, project)).run();
        }
        else {
          new ReformatCodeProcessor(project, convertToPsiFiles(files, project), null).run();
        }
      }

      return;
    }
    else{
      Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
      Module moduleContext = (Module)dataContext.getData(DataConstants.MODULE_CONTEXT);

      if (projectContext != null || moduleContext != null) {
        final String text;
        if (moduleContext != null) {
          text = CodeInsightBundle.message("process.scope.module", moduleContext.getModuleFilePath());
        }
        else {
          text = CodeInsightBundle.message("process.scope.project", projectContext.getPresentableUrl());
        }

        LayoutProjectCodeDialog dialog = new LayoutProjectCodeDialog(project, CodeInsightBundle.message("process.reformat.code"), text, true);
        dialog.show();
        if (!dialog.isOK()) return;
        if (dialog.isOptimizeImports()) {
          if (moduleContext != null) {
            new ReformatAndOptimizeImportsProcessor(project, moduleContext).run();
          }
          else {
            new ReformatAndOptimizeImportsProcessor(projectContext).run();
          }
        }
        else {
          if (moduleContext != null) {
            new ReformatCodeProcessor(project, moduleContext).run();
          }
          else {
            new ReformatCodeProcessor(projectContext).run();
          }
        }
        return;
      }

      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null) return;
      if (element instanceof PsiDirectoryContainer) {
        dir = ((PsiDirectoryContainer)element).getDirectories()[0];
      }
      else if (element instanceof PsiDirectory) {
        dir = (PsiDirectory)element;
      }
      else {
        file = element.getContainingFile();
        if (file == null) return;
        dir = file.getContainingDirectory();
      }
    }

    final LayoutCodeDialog dialog = new LayoutCodeDialog(project, CodeInsightBundle.message("process.reformat.code"), file, dir,
                                                         hasSelection ? Boolean.TRUE : Boolean.FALSE, HELP_ID);
    dialog.show();
    if (!dialog.isOK()) return;

    final boolean optimizeImports = dialog.isOptimizeImports();
    if (dialog.isProcessDirectory()){
      if (optimizeImports) {
        new ReformatAndOptimizeImportsProcessor(project, dir, dialog.isIncludeSubdirectories()).run();
      }
      else {
        new ReformatCodeProcessor(project, dir, dialog.isIncludeSubdirectories()).run();
      }
    }
    else{
      final TextRange range;
      if (editor != null && dialog.isProcessSelectedText()){
        range = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
      }
      else{
        range = null;
      }

      if (optimizeImports && range == null) {
        new ReformatAndOptimizeImportsProcessor(project, file).run();
      }
      else {
        new ReformatCodeProcessor(project, file, range).run();
      }
    }
  }

  public static PsiFile[] convertToPsiFiles(final VirtualFile[] files,Project project) {
    final PsiManager manager = PsiManager.getInstance(project);
    final ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    for (VirtualFile virtualFile : files) {
      final PsiFile psiFile = manager.findFile(virtualFile);
      if (psiFile != null) result.add(psiFile);
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    final VirtualFile[] files = (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);

    if (editor != null){
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || file.getVirtualFile() == null) {
        presentation.setEnabled(false);
        return;
      }

      if (LanguageFormatting.INSTANCE.forContext(file)  != null) {
        presentation.setEnabled(true);
        return;
      }
    }
    else if (files!= null && areFiles(files)) {
      boolean anyFormatters = false;
      for (VirtualFile virtualFile : files) {
        if (virtualFile.isDirectory()) {
          presentation.setEnabled(false);
          return;
        }
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile == null) {
          presentation.setEnabled(false);
          return;
        }
        final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(psiFile);
        if (builder != null) {
          anyFormatters = true;
        }
      }
      if (!anyFormatters) {
        presentation.setEnabled(false);
        return;
      }
    }
    else if (files != null && files.length == 1) {
      // skip. Both directories and single files are supported.
    }
    else if (dataContext.getData(DataConstants.MODULE_CONTEXT) == null &&
             dataContext.getData(DataConstants.PROJECT_CONTEXT) == null) {
      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null) {
        presentation.setEnabled(false);
        return;
      }
      if (!(element instanceof PsiDirectory)) {
        PsiFile file = element.getContainingFile();
        if (file == null || LanguageFormatting.INSTANCE.forContext(file) == null) {
          presentation.setEnabled(false);
          return;
        }
      }
    }
    presentation.setEnabled(true);
  }

  public static boolean areFiles(final VirtualFile[] files) {
    if (files == null) return false;
    if (files.length < 2) return false;
    for (VirtualFile virtualFile : files) {
      if (virtualFile.isDirectory()) return false;
    }
    return true;
  }
}
