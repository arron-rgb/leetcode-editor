package com.shuzijun.leetcode.plugin.actions.tree;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.shuzijun.leetcode.plugin.manager.CodeManager;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.Question;
import com.shuzijun.leetcode.plugin.window.NavigatorTable;

/**
 * @author shuzijun
 */
public class OpenContentAction extends AbstractTreeAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent, Config config, NavigatorTable navigatorTable, Question question) {
        Project project = anActionEvent.getProject();
        CodeManager.openContent(question, project, true);
    }
}
