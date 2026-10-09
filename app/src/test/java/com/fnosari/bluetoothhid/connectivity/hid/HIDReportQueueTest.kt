package com.fnosari.bluetoothhid.connectivity.hid

import com.fnosari.bluetoothhid.connectivity.hid.HIDReportQueue.JobResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HIDReportQueueTest {

    private val sent: MutableList<HIDReport> = Collections.synchronizedList(mutableListOf())
    private var queue: HIDReportQueue? = null

    @After
    fun tearDown() { queue?.stop() }

    private class Recorder : HIDReportQueue.JobListener {
        val done = CountDownLatch(1)
        @Volatile var result: JobResult? = null
        @Volatile var lastProgress = 0 to 0
        override fun onProgress(sent: Int, total: Int) { lastProgress = sent to total }
        override fun onFinished(result: JobResult) { this.result = result; done.countDown() }
        fun await(): JobResult { assertTrue(done.await(2, TimeUnit.SECONDS)); return result!! }
    }

    private fun keys(n: Int) = List(n) { HIDReport.Keyboard(0, listOf(4 + it % 20)) }

    @Test
    fun jobCompletesInOrder() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        q.start()
        val rec = Recorder()
        val reports = keys(5)
        q.submitJob(reports, rec)
        assertEquals(JobResult.Completed, rec.await())
        assertEquals(reports, sent.toList())
        assertEquals(5 to 5, rec.lastProgress)
    }

    @Test
    fun emptyJobCompletesImmediately() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        val rec = Recorder()
        q.submitJob(emptyList(), rec)
        assertEquals(JobResult.Completed, rec.await())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun pointerLaneGoesFirst() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        val rec = Recorder()
        q.submitJob(keys(3), rec)
        q.submit(HIDReport.Consumer(HIDConsumer.MUTE))
        q.start()
        rec.await()
        assertEquals(HIDReport.Consumer(HIDConsumer.MUTE), sent.first())
    }

    @Test
    fun mouseMovesCoalesce() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0).also { queue = it }
        q.submit(HIDReport.Mouse(dx = 10, dy = 1))
        q.submit(HIDReport.Mouse(dx = 20, dy = 2))
        q.submit(HIDReport.Mouse(dx = 120, dy = 0))          // would overflow → separate
        q.submit(HIDReport.Mouse(buttons = 1, dx = 1))       // other buttons → separate
        val rec = Recorder()
        q.submitJob(keys(1), rec)                            // sentinel to know when done
        q.start()
        rec.await()
        assertEquals(
            listOf(
                HIDReport.Mouse(dx = 30, dy = 3),
                HIDReport.Mouse(dx = 120, dy = 0),
                HIDReport.Mouse(buttons = 1, dx = 1)
            ),
            sent.filterIsInstance<HIDReport.Mouse>()
        )
    }

    @Test
    fun failureAbortsReleasesAndReportsIndex() {
        val attempts: MutableList<HIDReport> = Collections.synchronizedList(mutableListOf())
        var ok = 0
        val q = HIDReportQueue({ attempts += it; if (ok < 5) { ok++; true } else false }, delayMs = 0)
            .also { queue = it }
        q.start()
        val rec = Recorder()
        val second = Recorder()
        q.submitJob(keys(20), rec)
        q.submitJob(keys(3), second)
        assertEquals(JobResult.Failed(5), rec.await())
        assertEquals(JobResult.Cancelled, second.await())
        // 5 ok + (1 + MAX_RETRIES) attempts of report #6 + release attempts
        assertEquals(keys(20)[5], attempts[5])
        assertEquals(1 + HIDReportQueue.MAX_RETRIES, attempts.count { it == keys(20)[5] })
        // listeners fire before the best-effort release: wait for attempts to settle
        var size: Int
        do { size = attempts.size; Thread.sleep(20) } while (attempts.size != size)
        assertEquals(HIDReport.Keyboard.RELEASE, attempts.last())
    }

    @Test
    fun cancelStopsAndReleases() {
        lateinit var q: HIDReportQueue
        q = HIDReportQueue({
            sent += it
            if (sent.size == 10) q.cancelJobs()
            true
        }, delayMs = 0).also { queue = it }
        q.start()
        val rec = Recorder()
        q.submitJob(keys(2000), rec)
        assertEquals(JobResult.Cancelled, rec.await())
        assertTrue(sent.size < 2000)
        assertEquals(HIDReport.Keyboard.RELEASE, sent.last())
        assertTrue(!q.isBusy)
    }

    @Test
    fun failJobsReportsFailedWithSentCount() {
        lateinit var q: HIDReportQueue
        q = HIDReportQueue({
            sent += it
            if (sent.size == 7) q.failJobs()   // host disconnected after 7 reports
            true
        }, delayMs = 0).also { queue = it }
        q.start()
        val rec = Recorder()
        val second = Recorder()
        q.submitJob(keys(2000), rec)
        q.submitJob(keys(3), second)
        assertEquals(JobResult.Failed(7), rec.await())
        assertEquals(JobResult.Cancelled, second.await())
    }

    @Test
    fun jobsAfterStopAreCancelledImmediately() {
        val q = HIDReportQueue({ sent += it; true }, delayMs = 0)
        q.start()
        q.stop()
        val rec = Recorder()
        q.submitJob(keys(3), rec)
        assertEquals(JobResult.Cancelled, rec.await())
        assertTrue(sent.isEmpty())
    }
}
