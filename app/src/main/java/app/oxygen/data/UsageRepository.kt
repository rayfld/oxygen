package app.oxygen.data
import app.oxygen.core.db.DayAggregate
import app.oxygen.core.db.ModelAggregate
import app.oxygen.core.db.OxygenDatabase
data class UsageSummary(
    val total: Double,
    val last30Days: Double,
    val generationCount: Int,
)
class UsageRepository(private val db: OxygenDatabase) {
    private val dao = db.generations()
    suspend fun summary(): UsageSummary {
        val total = dao.sumAll() ?: 0.0
        val last30 = dao.sumSince(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) ?: 0.0
        val count = dao.countAll()
        return UsageSummary(total = total, last30Days = last30, generationCount = count)
    }
    suspend fun byModel(): List<ModelAggregate> = dao.byModel()
    suspend fun daily(days: Int = 30): List<DayAggregate> =
        dao.daily(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)
    suspend fun clear() = dao.clear()
}
