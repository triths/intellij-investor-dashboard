package com.vermouthx.stocker.views

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.vermouthx.stocker.StockerApp
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerQuoteDeleteListener
import com.vermouthx.stocker.listeners.StockerQuoteDeleteNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateListener
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting

class StockerToolWindow : ToolWindowFactory {

    companion object {
        private val messageBus = ApplicationManager.getApplication().messageBus
    }

    private lateinit var allView: StockerSimpleToolWindow
    private lateinit var tabViewMap: Map<StockerMarketType, StockerSimpleToolWindow>

    private fun injectPopupMenu(content: StockerSimpleToolWindow, insideAll: Boolean) {
        val tbBody = content.tableView.tableBody
        val tbModel = content.tableView.tableModel
        val tbPopupMenu = JBPopupMenu()
        val tbPopupDeleteMenuItem = JBMenuItem("Delete", AllIcons.General.Remove)
        tbPopupDeleteMenuItem.addActionListener {
            val setting = StockerSetting.instance
            for (selectedRow in tbBody.selectedRows.reversed()) {
                val code = tbModel.getValueAt(selectedRow, 0).toString()
                val market = setting.marketOf(code)
                if (market != null) {
                    synchronized(tbBody) {
                        setting.removeCode(market, code)
                        tbModel.removeRow(selectedRow)
                        tbModel.fireTableRowsDeleted(selectedRow, selectedRow)
                    }
                    if (insideAll) {
                        when (market) {
                            StockerMarketType.AShare -> {
                                val publisher = messageBus.syncPublisher(STOCK_CN_QUOTE_DELETE_TOPIC)
                                publisher.after(code)
                            }
                            StockerMarketType.HKStocks -> {
                                val publisher = messageBus.syncPublisher(STOCK_HK_QUOTE_DELETE_TOPIC)
                                publisher.after(code)
                            }
                            StockerMarketType.USStocks -> {
                                val publisher = messageBus.syncPublisher(STOCK_US_QUOTE_DELETE_TOPIC)
                                publisher.after(code)
                            }
                        }
                    } else {
                        val publisher = messageBus.syncPublisher(STOCK_ALL_QUOTE_DELETE_TOPIC)
                        publisher.after(code)
                    }
                }
            }
        }
        tbPopupMenu.add(tbPopupDeleteMenuItem)
        tbBody.componentPopupMenu = tbPopupMenu
    }

    override fun init(toolWindow: ToolWindow) {
        super.init(toolWindow)
        allView = StockerSimpleToolWindow().also { injectPopupMenu(it, true) }
        tabViewMap = mapOf(
            StockerMarketType.AShare to StockerSimpleToolWindow().also { injectPopupMenu(it, false) },
            StockerMarketType.HKStocks to StockerSimpleToolWindow().also { injectPopupMenu(it, false) },
            StockerMarketType.USStocks to StockerSimpleToolWindow().also { injectPopupMenu(it, false) }
        )
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val allContent = contentFactory.createContent(allView.component, "ALL", false)
        contentManager.addContent(allContent)
        val aShareContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.AShare]?.component,
            StockerMarketType.AShare.title,
            false
        )
        contentManager.addContent(aShareContent)
        val hkStocksContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.HKStocks]?.component,
            StockerMarketType.HKStocks.title,
            false
        )
        contentManager.addContent(hkStocksContent)
        val usStocksContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.USStocks]?.component,
            StockerMarketType.USStocks.title,
            false
        )
        contentManager.addContent(usStocksContent)
        this.subscribeMessage()
        StockerApp.schedule()
    }

    private fun subscribeMessage() {
        messageBus.connect()
            .subscribe(
                STOCK_ALL_QUOTE_UPDATE_TOPIC,
                StockerQuoteUpdateListener(allView.tableView)
            )
        messageBus.connect()
            .subscribe(
                STOCK_ALL_QUOTE_DELETE_TOPIC,
                StockerQuoteDeleteListener(allView.tableView)
            )
        tabViewMap.forEach { (market, myTableView) ->
            when (market) {
                StockerMarketType.AShare -> {
                    messageBus.connect()
                        .subscribe(
                            STOCK_CN_QUOTE_UPDATE_TOPIC,
                            StockerQuoteUpdateListener(
                                myTableView.tableView
                            )
                        )
                    messageBus.connect()
                        .subscribe(
                            STOCK_CN_QUOTE_DELETE_TOPIC,
                            StockerQuoteDeleteListener(
                                myTableView.tableView
                            )
                        )
                }
                StockerMarketType.HKStocks -> {
                    messageBus.connect()
                        .subscribe(
                            STOCK_HK_QUOTE_UPDATE_TOPIC,
                            StockerQuoteUpdateListener(
                                myTableView.tableView
                            )
                        )
                    messageBus.connect()
                        .subscribe(
                            STOCK_HK_QUOTE_DELETE_TOPIC,
                            StockerQuoteDeleteListener(
                                myTableView.tableView
                            )
                        )
                }
                StockerMarketType.USStocks -> {
                    messageBus.connect()
                        .subscribe(
                            STOCK_US_QUOTE_UPDATE_TOPIC,
                            StockerQuoteUpdateListener(
                                myTableView.tableView
                            )
                        )
                    messageBus.connect()
                        .subscribe(
                            STOCK_US_QUOTE_DELETE_TOPIC,
                            StockerQuoteDeleteListener(
                                myTableView.tableView
                            )
                        )
                }
            }
        }
    }
}