package dev.vector.vectest

class VecTestSuite {
    private val results = mutableListOf<Pair<String, Throwable?>>()
    var passed = 0; private set
    var failed = 0; private set

    fun check(name: String, block: () -> Unit) {
        try {
            block()
            results += name to null
            passed++
        } catch (t: Throwable) {
            results += name to t
            failed++
        }
    }

    fun assert(condition: Boolean, msg: String = "assertion failed") {
        if (!condition) throw AssertionError(msg)
    }
    fun assertNotNull(v: Any?, msg: String = "expected non-null") {
        if (v == null) throw AssertionError(msg)
    }
    fun assertNull(v: Any?, msg: String = "expected null") {
        if (v != null) throw AssertionError("$msg (got $v)")
    }
    fun assertEquals(expected: Any?, actual: Any?) {
        if (expected != actual) throw AssertionError("expected <$expected> but was <$actual>")
    }
    fun assertNotEmpty(c: Collection<*>, msg: String = "expected non-empty collection") {
        if (c.isEmpty()) throw AssertionError(msg)
    }

    fun failures() = results.filter { it.second != null }
}
