package com.aican.biometricattendance.presentation.screens.mark_attendance

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.converters.Converters
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.entity.AttendanceEventType
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class FaceTemplate(
    val employeeId: String,
    val name: String?,
    val embedding: FloatArray // L2-normalized
)



class NewAttendanceCameraViewModel(
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "NewAttendanceVM"

        // --- Matching & stability tuning ---
        private const val MATCH_THRESHOLD = 0.62f            // tune 0.60–0.68
        private const val TOP2_MARGIN = 0.04f                // top1 - top2 ≥ margin
        private const val REQUIRED_CONSECUTIVE_HITS = 6      // 5–7 gives ~200ms lock at 30fps
        private const val COMPARE_EVERY_MS = 70L             // throttle comparisons
        private const val QUALITY_THRESHOLD = 0.60f          // face quality gate
    }

    // FaceNet processor (set from composable/screen or init lazily elsewhere)
    var faceEmbeddingProcessor: UnifiedFaceEmbeddingProcessor? = null

    // In-memory normalized templates of all registered faces
    private val _templates = MutableStateFlow<List<FaceTemplate>>(emptyList())
    val templates: StateFlow<List<FaceTemplate>> = _templates.asStateFlow()

    // UI state: face boxes, liveness, quality
    private val _faceBoxes = MutableStateFlow<List<FaceBox>>(emptyList())
    val faceBoxes: StateFlow<List<FaceBox>> = _faceBoxes.asStateFlow()

    private val _livenessStatus = MutableStateFlow(LivenessStatus.NO_FACE)
    val livenessStatus: StateFlow<LivenessStatus> = _livenessStatus.asStateFlow()

    private val _faceQuality = MutableStateFlow(0f)
    val faceQuality: StateFlow<Float> = _faceQuality.asStateFlow()

    // Matching state
    private val _similarityScore = MutableStateFlow(0f)
    val similarityScore: StateFlow<Float> = _similarityScore.asStateFlow()

    private val _attendanceResult = MutableStateFlow<AttendanceResult?>(null)
    val attendanceResult: StateFlow<AttendanceResult?> = _attendanceResult.asStateFlow()

    // Last event (for the matched employee)
    val lastEvent = MutableStateFlow<AttendanceEntity?>(null)

    // Internal runtime guards
    @Volatile private var lastCompareTime = 0L
    private var currentCandidateId: String? = null
    private var consecutiveHits = 0

    // -------------------------
    // Public API
    // -------------------------

    /**
     * Call once when camera screen opens (and whenever registry changes).
     * Loads ALL embeddings, L2-normalizes in memory for fast cosine comparisons.
     */
    fun preloadAllEmbeddings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = faceEmbeddingRepository.getAll()  // Ensure DAO: SELECT * FROM face_embeddings
                val conv = Converters()
                val templates = buildList {
                    for (e in entities) {
                        val bytes = e.embedding ?: continue
                        val vec = conv.toFloatArray(bytes)
                        if (vec.size != 512) continue
                        l2NormalizeInPlace(vec)
                        add(FaceTemplate(employeeId = e.employeeId, name = e.name, embedding = vec))
                    }
                }
                _templates.emit(templates)
                Log.d(TAG, "Preloaded templates: ${templates.size}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to preload embeddings", t)
                _templates.emit(emptyList())
            }
        }
    }

    /**
     * Wire this from your analyzer per frame.
     * Pass in:
     *  - cropped 160×160 RGB face bitmap for FaceNet (or null if no valid crop)
     *  - detected face boxes (for overlay)
     *  - liveness status & quality score
     */
    fun onFrame(
        faceBitmap: Bitmap?,
        boxes: List<FaceBox>,
        liveness: LivenessStatus,
        quality: Float
    ) {
        _faceBoxes.value = boxes
        _livenessStatus.value = liveness
        _faceQuality.value = quality

        val now = System.currentTimeMillis()
        if (now - lastCompareTime < COMPARE_EVERY_MS) return
        lastCompareTime = now

        // Gates
        if (boxes.isEmpty()
            || liveness != LivenessStatus.LIVE_FACE
            || quality < QUALITY_THRESHOLD
            || faceBitmap == null
        ) {
            currentCandidateId = null
            consecutiveHits = 0
            return
        }

        // Do the heavy work off the main thread
        viewModelScope.launch(Dispatchers.Default) {
            val proc = faceEmbeddingProcessor ?: return@launch
            val pool = _templates.value
            if (pool.isEmpty()) return@launch

            // 1) Get live embedding
            val liveEmb = proc.generateEmbedding(faceBitmap).embedding
            if (liveEmb == null || liveEmb.size != 512) {
                currentCandidateId = null
                consecutiveHits = 0
                return@launch
            }
            l2NormalizeInPlace(liveEmb)

            // 2) Best match search
            val (bestId, best, second) = bestMatch(liveEmb, pool) ?: run {
                currentCandidateId = null
                consecutiveHits = 0
                return@launch
            }

            _similarityScore.emit(best)

            val passesThreshold = best >= MATCH_THRESHOLD
            val passesMargin = (best - second) >= TOP2_MARGIN

            if (passesThreshold && passesMargin) {
                if (currentCandidateId == bestId) {
                    consecutiveHits++
                } else {
                    currentCandidateId = bestId
                    consecutiveHits = 1
                }

                if (consecutiveHits >= REQUIRED_CONSECUTIVE_HITS) {
                    // lock → mark attendance and notify UI
                    handleAutoSuccessForEmployee(bestId, best)
                    currentCandidateId = null
                    consecutiveHits = 0
                }
            } else {
                currentCandidateId = null
                consecutiveHits = 0
            }
        }
    }

    fun resetState() {
        _similarityScore.value = 0f
        _attendanceResult.value = null
        _faceBoxes.value = emptyList()
        _livenessStatus.value = LivenessStatus.NO_FACE
        _faceQuality.value = 0f
        currentCandidateId = null
        consecutiveHits = 0
        lastCompareTime = 0L
    }

    // -------------------------
    // Attendance
    // -------------------------

    private fun handleAutoSuccessForEmployee(employeeId: String, sim: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val last = attendanceRepository.getLastEvent(employeeId)
                val nextType = if (last?.eventType == AttendanceEventType.CHECK_IN)
                    AttendanceEventType.CHECK_OUT else AttendanceEventType.CHECK_IN

                val entry = AttendanceEntity(
                    employeeId = employeeId,
                    timestamp = System.currentTimeMillis(),
                    eventType = nextType,
                    matchPercent = sim * 100f
                )
                attendanceRepository.insert(entry)
                lastEvent.emit(entry)

                _attendanceResult.emit(
                    AttendanceResult(
                        success = true,
                        similarity = sim,
                        employeeId = employeeId,
                        message = if (nextType == AttendanceEventType.CHECK_IN)
                            "Checked in • ${(sim * 100).toInt()}%"
                        else
                            "Checked out • ${(sim * 100).toInt()}%"
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to mark attendance", t)
                _attendanceResult.emit(
                    AttendanceResult(
                        success = false,
                        similarity = 0f,
                        employeeId = employeeId,
                        message = "Failed to mark attendance"
                    )
                )
            }
        }
    }

    // -------------------------
    // Math helpers
    // -------------------------

    private fun bestMatch(
        live: FloatArray,
        pool: List<FaceTemplate>
    ): Triple<String, Float, Float>? {
        if (pool.isEmpty()) return null
        var bestId = pool[0].employeeId
        var best = -1f
        var second = -1f
        for (t in pool) {
            val s = dot512(live, t.embedding)
            if (s > best) {
                second = best
                best = s
                bestId = t.employeeId
            } else if (s > second) {
                second = s
            }
        }
        return Triple(bestId, best, second)
    }

    private fun dot512(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        // hot loop — keep it simple & fast
        for (i in 0 until 512) sum += a[i] * b[i]
        return sum
    }

    private fun l2NormalizeInPlace(v: FloatArray) {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        if (n > 0f) {
            for (i in v.indices) v[i] /= n
        }
    }
}
