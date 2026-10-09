package com.fnosari.bluetoothhid.connectivity.hid

/**
 * Sends HID reports one by one from a single worker thread, with a fixed
 * delay between reports so the Bluetooth link never drops any.
 *
 * Two lanes: pointer (mouse/consumer, sent first, mouse moves coalesced) and
 * keyboard jobs (FIFO, progress, cancel). Whatever ends a job early, the
 * queue sends an all-keys-up report so no key stays held on the host.
 *
 * @param sender sends one report, returns false when the stack refused it.
 * @param delayMs pause after each report.
 */
class HIDReportQueue(private val sender: ReportSender, @Volatile var delayMs: Long = 10L) {

    companion object {
        private const val THREAD_NAME = "HIDReportQueue"
        const val MAX_RETRIES = 3
    }

    fun interface ReportSender {
        fun send(report: HIDReport): Boolean
    }

    interface JobListener {
        fun onProgress(sent: Int, total: Int) {}
        fun onFinished(result: JobResult)
    }

    sealed class JobResult {
        data object Completed : JobResult()
        data object Cancelled : JobResult()
        data class Failed(val sentReports: Int) : JobResult()
    }

    private class Job(val reports: List<HIDReport.Keyboard>, val listener: JobListener?) {
        var index = 0
    }

    private val lock = Object()
    private val pointer = ArrayDeque<HIDReport>()
    private val jobs = ArrayDeque<Job>()
    private var cancelRequested = false
    private var running = false
    private var thread: Thread? = null

    val isBusy: Boolean get() = synchronized(lock) { jobs.isNotEmpty() }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        thread = Thread(::loop, THREAD_NAME).apply { isDaemon = true; start() }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        thread?.join(1000)
        thread = null
    }

    fun submit(report: HIDReport) {
        synchronized(lock) {
            val last = pointer.lastOrNull()
            if (report is HIDReport.Mouse && last is HIDReport.Mouse && last.buttons == report.buttons) {
                val merged = HIDReport.Mouse(
                    last.buttons,
                    last.dx + report.dx,
                    last.dy + report.dy,
                    last.wheel + report.wheel,
                    last.pan + report.pan
                )
                if (listOf(merged.dx, merged.dy, merged.wheel, merged.pan).all { it in -127..127 }) {
                    pointer.removeLast()
                    pointer.addLast(merged)
                } else {
                    pointer.addLast(report)
                }
            } else {
                pointer.addLast(report)
            }
            lock.notifyAll()
        }
    }

    fun submitJob(reports: List<HIDReport.Keyboard>, listener: JobListener? = null) {
        if (reports.isEmpty()) {
            listener?.onFinished(JobResult.Completed)
            return
        }
        synchronized(lock) {
            jobs.addLast(Job(reports, listener))
            lock.notifyAll()
        }
    }

    fun cancelJobs() {
        synchronized(lock) {
            if (jobs.isNotEmpty()) cancelRequested = true
            lock.notifyAll()
        }
    }

    private fun loop() {
        while (true) {
            var report: HIDReport? = null
            var job: Job? = null
            var cancelled: List<Job> = emptyList()

            synchronized(lock) {
                while (running && pointer.isEmpty() && jobs.isEmpty()) lock.wait()
                if (!running) return
                if (cancelRequested) {
                    cancelled = jobs.toList()
                    jobs.clear()
                    cancelRequested = false
                } else if (pointer.isNotEmpty()) {
                    report = pointer.removeFirst()
                } else {
                    job = jobs.first()
                    report = job.reports[job.index]
                }
            }

            if (cancelled.isNotEmpty()) {
                sendWithRetry(HIDReport.Keyboard.RELEASE)
                cancelled.forEach { it.listener?.onFinished(JobResult.Cancelled) }
                continue
            }

            val ok = sendWithRetry(report!!)
            val current = job
            if (current == null) {
                pause()
                continue
            }

            if (!ok) {
                val others = synchronized(lock) {
                    val all = jobs.toList()
                    jobs.clear()
                    cancelRequested = false
                    all.filter { it !== current }
                }
                current.listener?.onFinished(JobResult.Failed(current.index))
                others.forEach { it.listener?.onFinished(JobResult.Cancelled) }
                sendWithRetry(HIDReport.Keyboard.RELEASE)
                continue
            }

            current.index++
            current.listener?.onProgress(current.index, current.reports.size)
            if (current.index == current.reports.size) {
                synchronized(lock) { jobs.removeFirst() }
                current.listener?.onFinished(JobResult.Completed)
            }
            pause()
        }
    }

    private fun sendWithRetry(report: HIDReport): Boolean {
        for (attempt in 0..MAX_RETRIES) {
            if (sender.send(report)) return true
            if (attempt < MAX_RETRIES && delayMs > 0) Thread.sleep(delayMs * 2)
        }
        return false
    }

    private fun pause() {
        if (delayMs > 0) Thread.sleep(delayMs)
    }
}
