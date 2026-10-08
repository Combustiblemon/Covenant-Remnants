package org.combustiblelemon.covenantremnants.utils.render.rupture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Halo 3 slipspace exit tube mesh, lathed at load from the tag's radius profile
 * ([ArkTubeProfile], generated): an unindexed triangle list, 5 floats per vertex (x, y, z, u, v),
 * `SEGMENTS_AROUND` segments around × the profile's stations along +x. The mouth (largest radius)
 * sits at x = 0; u runs around the circumference (the seam column is duplicated at u = 1), v along
 * the length (v = 1 at the mouth, where the streaks are brightest).
 *
 * Built here rather than baked (the original 77-triangle tag mesh was baked): a dense lathe as a
 * Kotlin float literal would exceed the JVM's 64 KB method limit.
 */
internal object ArkTubeMesh {
    const val FLOATS_PER_VERTEX = 5

    /** Largest radius (about the x axis) at the mouth, mesh units. */
    const val MOUTH_RADIUS = ArkTubeProfile.MOUTH_RADIUS

    /** Mesh length along x, mesh units. */
    const val LENGTH = ArkTubeProfile.LENGTH

    private const val AROUND = ArkTubeProfile.SEGMENTS_AROUND
    private val STATIONS = ArkTubeProfile.PROFILE_X.size

    @JvmField
    val VERTEX_COUNT = AROUND * (STATIONS - 1) * 6

    @JvmField
    val DATA: FloatArray = lathe()

    private fun lathe(): FloatArray {
        val xs = ArkTubeProfile.PROFILE_X
        val rs = ArkTubeProfile.PROFILE_R
        val vs = ArkTubeProfile.PROFILE_V
        val out = FloatArray(VERTEX_COUNT * FLOATS_PER_VERTEX)
        var o = 0
        fun put(station: Int, segment: Int) {
            val theta = 2.0 * PI * segment / AROUND
            out[o++] = xs[station]
            out[o++] = (rs[station] * cos(theta)).toFloat()
            out[o++] = (rs[station] * sin(theta)).toFloat()
            out[o++] = segment.toFloat() / AROUND
            out[o++] = vs[station]
        }
        for (i in 0 until STATIONS - 1) {
            for (k in 0 until AROUND) {
                // Quad (i,k) (i,k+1) (i+1,k) (i+1,k+1) as two triangles; k + 1 == AROUND is the seam copy.
                put(i, k); put(i + 1, k); put(i, k + 1)
                put(i, k + 1); put(i + 1, k); put(i + 1, k + 1)
            }
        }
        return out
    }
}
