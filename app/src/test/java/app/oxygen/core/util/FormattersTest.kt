package app.oxygen.core.util
import org.junit.Assert.assertEquals
import org.junit.Test
class FormattersTest {
    @Test
    fun `cost formatting adapts precision`() {
        assertEquals("-", Fmt.cost(null))
        assertEquals("$0", Fmt.cost(0.0))
        assertEquals("<$0.0001", Fmt.cost(0.00001))
        assertEquals("$0.0023", Fmt.cost(0.00234))
        assertEquals("$1.50", Fmt.cost(1.5))
    }
    @Test
    fun `token formatting is compact`() {
        assertEquals("-", Fmt.tokens(null))
        assertEquals("950", Fmt.tokens(950))
        assertEquals("1.2k", Fmt.tokens(1234))
        assertEquals("12k", Fmt.tokens(12_345))
        assertEquals("1.2M", Fmt.tokens(1_234_567))
    }
    @Test
    fun `price per million formats nicely`() {
        assertEquals("Free", Fmt.pricePerM(0.0))
        assertEquals("$3", Fmt.pricePerM(0.000003))
        assertEquals("$0.25", Fmt.pricePerM(0.00000025))
    }
    @Test
    fun `context formatting`() {
        assertEquals("200K", Fmt.context(200_000))
        assertEquals("1M", Fmt.context(1_000_000))
    }
    @Test
    fun `relative time buckets`() {
        val now = 1_000_000_000_000L
        assertEquals("now", Fmt.relativeTime(now - 10_000, now))
        assertEquals("5m", Fmt.relativeTime(now - 5 * 60_000, now))
        assertEquals("3h", Fmt.relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d", Fmt.relativeTime(now - 2 * 86_400_000, now))
    }
    @Test
    fun `tokens per second`() {
        assertEquals("50", Fmt.tokensPerSecond(500, 10_000))
        assertEquals(null, Fmt.tokensPerSecond(null, 10_000))
    }
}
