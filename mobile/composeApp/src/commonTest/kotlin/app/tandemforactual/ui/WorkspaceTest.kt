package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceTest {
    private val tabs = listOf(AppRoute.HOME, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.REPORTS)

    @Test
    fun fallsBackToWhatFitsTheWindow() {
        assertEquals(WorkspaceLayout.FOUR, Workspace(WorkspaceLayout.FOUR).effective(1_400f, 900f))
        assertEquals(WorkspaceLayout.THREE, Workspace(WorkspaceLayout.FOUR).effective(1_100f, 900f))
        assertEquals(WorkspaceLayout.TWO, Workspace(WorkspaceLayout.THREE).effective(800f, 900f))
        assertEquals(WorkspaceLayout.TWO, Workspace(WorkspaceLayout.GRID).effective(800f, 600f))
        assertEquals(WorkspaceLayout.SINGLE, Workspace(WorkspaceLayout.TWO).effective(600f, 900f))
    }

    @Test
    fun fillsPanesWithoutRepeatsAndSwapsOnPlace() {
        val workspace = Workspace(WorkspaceLayout.THREE, listOf(AppRoute.BUDGET, AppRoute.BUDGET, AppRoute.SETTINGS))
        val panes = workspace.panesFor(3, AppRoute.ACTIVITY, tabs)
        assertEquals(listOf(AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.HOME), panes)
        assertEquals(listOf(AppRoute.HOME, AppRoute.BUDGET, AppRoute.ACTIVITY), workspace.place(AppRoute.HOME, 0, panes).panes)
        assertEquals(listOf(AppRoute.REPORTS, AppRoute.BUDGET, AppRoute.HOME), workspace.place(AppRoute.REPORTS, 0, panes).panes)
    }

    @Test
    fun roundTripsThroughSettings() {
        val workspace = Workspace(WorkspaceLayout.GRID, listOf(AppRoute.HOME, AppRoute.BUDGET))
        assertEquals(workspace, Workspace.decode(workspace.encode()))
        assertEquals(Workspace(), Workspace.decode(null))
        assertEquals(Workspace(), Workspace.decode("NOPE|HOME"))
        assertEquals(Workspace(WorkspaceLayout.TWO, listOf(AppRoute.HOME)), Workspace.decode("TWO|HOME,GONE"))
    }
}
