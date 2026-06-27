/*
 * File: app/src/test/java/com/lockin/app/core/util/MainDispatcherRule.kt
 * Purpose: JUnit 4 test rule to swap Dispatchers.Main with a TestDispatcher.
 * Why: Required because local JVM tests do not have a Looper-backed main thread,
 * and calling viewModelScope.launch throws an exception without this rule.
 */

package com.lockin.app.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
