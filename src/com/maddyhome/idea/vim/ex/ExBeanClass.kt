package com.maddyhome.idea.vim.ex

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.util.xmlb.annotations.Attribute

class ExBeanClass : AbstractExtensionPointBean() {
  @Attribute("implementation")
  var implementation: String? = null

  val handler: CommandHandler by lazy {
    this.instantiateClass<CommandHandler>(
      implementation ?: "", ApplicationManager.getApplication().picoContainer)
  }
}