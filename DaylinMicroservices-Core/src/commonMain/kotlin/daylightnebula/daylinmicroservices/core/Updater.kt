package daylightnebula.daylinmicroservices.core

// a simple function that creates a thread that runs the loop every given amount of ms
fun loopingThread(intervalMS: Int, callback: () -> Unit): LoopingThread {
    // start an infinitely looping thread
    val thread = LoopingThread(intervalMS, callback)
    thread.start()
    return thread
}

// a simple class that creates a loop and function to stop the loop
class LoopingThread(private val intervalMS: Int, val callback: () -> Unit): Thread() {

    private var running = true

    fun dispose() {
        running = false
    }

    override fun run() {
        while(running) {
            // get start time
            val start = System.currentTimeMillis()

            // run callback
            callback()

            // wait until the interval completes
            val wait = intervalMS - (System.currentTimeMillis() - start)
            if (wait > 0)
                sleep(wait)
        }
    }
}