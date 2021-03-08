/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2021 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.containers.toArray
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.group.NotificationService
import com.maddyhome.idea.vim.helper.MessageHelper
import com.maddyhome.idea.vim.option.IdeaStatusIcon
import com.maddyhome.idea.vim.option.OptionsManager
import icons.VimIcons
import kotlinx.coroutines.delay
import org.apache.commons.io.IOUtils
import org.jetbrains.annotations.NonNls
import java.awt.Point
import java.awt.event.MouseEvent
import java.io.BufferedReader
import javax.swing.Icon
import javax.swing.SwingConstants
import com.intellij.openapi.wm.ToolWindowAnchor
import com.maddyhome.idea.vim.ex.vimscript.VimScriptParser


@NonNls
const val STATUS_BAR_ICON_ID = "Mayo-Icon"
const val STATUS_BAR_DISPLAY_NAME = "Mayo"

class StatusBarIconFactory : StatusBarWidgetFactory/*, LightEditCompatible*/ {

  override fun getId(): String = STATUS_BAR_ICON_ID

  override fun getDisplayName(): String = STATUS_BAR_DISPLAY_NAME

  override fun disposeWidget(widget: StatusBarWidget) {
    // Nothing
  }

  override fun isAvailable(project: Project): Boolean {
    return OptionsManager.ideastatusicon.value != IdeaStatusIcon.disabled
  }

  override fun createWidget(project: Project): StatusBarWidget {
    OptionsManager.ideastatusicon.addOptionChangeListener { _, _ -> updateAll() }
    return VimStatusBar()
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  /* Use can configure this icon using ideastatusicon option, but we should still keep the option to remove
  * the icon via IJ because this option is hard to discover */
  override fun isConfigurable(): Boolean = true

  private fun updateAll() {
    val projectManager = ProjectManager.getInstanceIfCreated() ?: return
    for (project in projectManager.openProjects) {
      val statusBarWidgetsManager = project.getService(StatusBarWidgetsManager::class.java) ?: continue
      statusBarWidgetsManager.updateWidget(this)
    }

    updateIcon()
  }

  companion object {
    fun updateIcon() {
      val projectManager = ProjectManager.getInstanceIfCreated() ?: return
      for (project in projectManager.openProjects) {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: continue
        statusBar.updateWidget(STATUS_BAR_ICON_ID)
      }
    }
  }
}

class VimStatusBar : StatusBarWidget, StatusBarWidget.IconPresentation {

  override fun ID(): String = STATUS_BAR_ICON_ID

  override fun install(statusBar: StatusBar) {
    // Nothing
  }

  override fun dispose() {
    // Nothing
  }

  override fun getTooltipText() = STATUS_BAR_DISPLAY_NAME

  override fun getIcon(): Icon {
    if (OptionsManager.ideastatusicon.value == IdeaStatusIcon.gray) return VimIcons.IDEAVIM_DISABLED
    return if (VimPlugin.isEnabled()) VimIcons.IDEAVIM else VimIcons.IDEAVIM_DISABLED
  }

  override fun getClickConsumer() = Consumer<MouseEvent> { event ->
    val component = event.component
    val popup = VimActionsPopup.getPopup(DataManager.getInstance().getDataContext(component))
    val dimension = popup.content.preferredSize

    val at = Point(0, -dimension.height)
    popup.show(RelativePoint(component, at))
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
}

class VimActions : DumbAwareAction() {

  companion object {
    const val actionPlace = "VimActionsPopup"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    VimActionsPopup.getPopup(e.dataContext).showCenteredInCurrentWindow(project)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !project.isDisposed
  }
}

private object VimActionsPopup {
  fun getPopup(dataContext: DataContext): ListPopup {
    val actions = getActions()
    val popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(
        STATUS_BAR_DISPLAY_NAME, actions,
        dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
        VimActions.actionPlace
      )
    popup.setAdText(MessageHelper.message("popup.advertisement.version", VimPlugin.getVersion()), SwingConstants.CENTER)

    return popup
  }

  private fun getActions(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()
    actionGroup.isPopup = true

    // Old Action Bars
    //actionGroup.add(ActionManager.getInstance().getAction("VimPluginToggle"))
    //actionGroup.addSeparator()
   // actionGroup.add(NotificationService.OpenIdeaVimRcAction(null))
    //actionGroup.add(ShortcutConflictsSettings)
    //actionGroup.addSeparator()
    var namespace_list = arrayOf<String>()
    var single_namespace_review_list: MutableList<String> = mutableListOf()
    var cluster_review_list: MutableList<String> = mutableListOf()
    var review_namespace: MutableList<String> = mutableListOf()

    var active_namespace = ""

    // get namespaces
    if (!portForwardActive()) {
      //println("####### PF INACTIVE!!!")
      val p = Runtime.getRuntime().exec("kubectl get namespaces --no-headers -o custom-columns=:metadata.name")
      p.waitFor()
      val stdOut = IOUtils.toString(p.inputStream, Charsets.UTF_8)
      //val stdErr = IOUtils.toString(p.errorStream, Charsets.UTF_8)
      namespace_list = stdOut.split("\n").toTypedArray()
      //namespace_list.forEach { System.out.println(it) }
      for (item in namespace_list) {
        if ("review" in item) {
          var helm_command = arrayOf("/bin/sh", "-c", "helm list --namespace " + item + "| grep -v NAME | grep -v gitlab | sed 's/|/ /' | awk '{print $1,$2}'")
          val p = Runtime.getRuntime().exec(helm_command)
          p.waitFor()
          val stdOut = IOUtils.toString(p.inputStream, Charsets.UTF_8)
          if (!stdOut.isEmpty())
            single_namespace_review_list = stdOut.split("\n").toMutableList()
            cluster_review_list.addAll(single_namespace_review_list)
            single_namespace_review_list.clear()
        }
        //println(review_list)
        //println("printing review of $item")
        //single_namespace_review_list.forEach { System.out.println(it) }
       }
      //cluster_review_list.forEach { println("entry of cluster review list $it") }

    }
    else {
      // println("####### PF ACTIVE!!!")
      // Get active namespace
      val my_command = arrayOf("/bin/sh", "-c", "ps -aux | grep tele | grep port-forward | grep namespace | grep -oP '(?<=namespace )[^ ]*'")
      val p = Runtime.getRuntime().exec(my_command)
      p.waitFor()
      // return from inputStream is full of unwanted chars, remove them
      active_namespace = IOUtils.toString(p.inputStream, Charsets.UTF_8).substringBefore('\n')
      //println("Active namespace : $active_namespace")
    }

    var portForwardMessage: String
    var portForwarReviewMessage: String
    if (portForwardActive())
      portForwardMessage = "Port-Forward (Active in " + active_namespace + ")"
    else
      portForwardMessage = "Port-Forward (Namespace)"
      portForwarReviewMessage = "Port-Forward (Review)"

    // Main group
    val eapGroup = DefaultActionGroup(
      portForwardMessage,
      true
    )
    val reviewGroup = DefaultActionGroup(
      portForwarReviewMessage,
      true
    )

    if (portForwardActive()) {
      eapGroup.add(
        StopPortForward(
          "Stop port-forward",
          active_namespace,
          null
        )
      )
      eapGroup.add(
        OpenConfigurationFile(
          "Open your configuration file",
          active_namespace,
          null
        )
      )
    }
    if (!portForwardActive())
      for (item in namespace_list) {
          if (!item.contains("review"))
            eapGroup.add(
              StartPortForward(
                item,
                item,
                null
              )
            )
      }
      // for items in review!!!
      for (item in cluster_review_list)
        if (item.length > 0) {
          review_namespace = item.split(" ").toMutableList()
          reviewGroup.add(
            StartReviewPortForward(
              review_namespace.elementAt(1) + "/" + review_namespace.elementAt(0),
              review_namespace.elementAt(1),
              review_namespace.elementAt(0),
              null
            )
          )
          review_namespace.clear()
        }

    actionGroup.add(eapGroup)
    actionGroup.add(reviewGroup)

    val helpGroup = DefaultActionGroup(MessageHelper.message("action.contacts.help.text"), true)

    helpGroup.add(
      HelpLink(
        MessageHelper.message("action.create.issue.text"),
        "https://gitlab.wiremind.io/groups/wiremind/devops/-/issues",
        VimIcons.YOUTRACK
      )
    )

    actionGroup.add(helpGroup)

    return actionGroup
  }
}

private class HelpLink(
  // [VERSION UPDATE] 203+ uncomment
  /*@ActionText*/
  name: String,
  val link: String,
  icon: Icon?
) : DumbAwareAction(name, null, icon)/*, LightEditCompatible*/ {
  override fun actionPerformed(e: AnActionEvent) {
    BrowserUtil.browse(link)
  }
}

private class StartPortForward(
  // [VERSION UPDATE] 203+ uncomment
  /*@ActionText*/
  name: String,
  val namespace: String,
  icon: Icon?
) : DumbAwareAction(name, null, icon)/*, LightEditCompatible*/ {
  override fun actionPerformed(e: AnActionEvent) {
    val mayo_command: String = "/usr/local/bin/mayo port-forward-start -d -g -n " + namespace
    val p = Runtime.getRuntime().exec(mayo_command)
     VimPlugin.getNotifications(e.project).notifyPortForwardStarted(namespace)
  }
}

private class StartReviewPortForward(
  // [VERSION UPDATE] 203+ uncomment
  /*@ActionText*/
  name: String,
  val namespace: String,
  val release: String,
  icon: Icon?
) : DumbAwareAction(name, null, icon)/*, LightEditCompatible*/ {
  override fun actionPerformed(e: AnActionEvent) {
    val mayo_command: String = "/usr/local/bin/mayo port-forward-start -g -n " + namespace + " --release-name " + release
    val p = Runtime.getRuntime().exec(mayo_command)
    VimPlugin.getNotifications(e.project).notifyReviewPortForwardStarted(namespace, release)
  }
}


private class StopPortForward(
  // [VERSION UPDATE] 203+ uncomment
  /*@ActionText*/
  name: String,
  val namespace: String,
  icon: Icon?
) : DumbAwareAction(name, null, icon)/*, LightEditCompatible*/ {
  override fun actionPerformed(e: AnActionEvent) {
    val mayo_command: String = "/usr/local/bin/mayo clean-up -y"
    val p = Runtime.getRuntime().exec(mayo_command)
    //p.waitFor()
    VimPlugin.getNotifications(e.project).notifyPortForwardStopped(namespace)
  }
}


private class OpenConfigurationFile(
  // [VERSION UPDATE] 203+ uncomment
  /*@ActionText*/
  name: String,
  val namespace: String,
  icon: Icon?
) : DumbAwareAction("Open your configuration file")/*, LightEditCompatible*/ {
  override fun actionPerformed(e: AnActionEvent) {
    //println("# fetch config from $namespace")
    val eventProject = e.project
    if (eventProject != null) {
      val my_command = arrayOf("/bin/sh", "-c", "find /tmp -name *" + namespace + "* 2>/dev/null")
      val p = Runtime.getRuntime().exec(my_command)
      p.waitFor()
      var config_filename = IOUtils.toString(p.inputStream, Charsets.UTF_8).substringBefore('\n')
      OpenFileAction.openFile(config_filename, eventProject)
     // println("config_filename = $config_filename")
      return
    }
  }
}

fun portForwardActive() : Boolean  {
  val mayo_command = arrayOf("/bin/sh", "-c", "ps -ef| grep -v grep | grep vpn-tcp | wc -l")
  val p = Runtime.getRuntime().exec(mayo_command) // |  grep 'vpn-tcp' | wc -l")
  p.waitFor()
  val stdOut = IOUtils.toString(p.inputStream, Charsets.UTF_8).substringBefore('\n')
  //val telepresence_running = stdOut.toInt()
//  println(" in portForwardActive $stdOut")
  var telepresenceRunning = 0
  telepresenceRunning = stdOut.toInt()
  return telepresenceRunning >= 1
}


  private object ShortcutConflictsSettings :
    DumbAwareAction(MessageHelper.message("action.settings.text"))/*, LightEditCompatible*/ {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().editConfigurable(e.project, VimEmulationConfigurable())
    }
  }